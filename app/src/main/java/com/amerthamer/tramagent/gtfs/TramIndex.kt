package com.amerthamer.tramagent.gtfs

import com.amerthamer.tramagent.data.BkkApi
import com.amerthamer.tramagent.domain.Futar
import com.amerthamer.tramagent.domain.TramLines
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * Villamos-indulások indexe a GTFS-ből (DK3 gyorsításához).
 *
 * A stop_times.txt több száz MB, ezért keresésenként végigolvasni lassú. Az index egyszer, a GTFS
 * letöltése után készül el, és csak a villamos-indulásokat tartalmazza megállónként, időrendben:
 * (indulás másodpercben a szolgáltatási nap kezdetétől, menet sorszáma). A menetekhez a vonal,
 * az irány és a service_id tartozik. A menetek pályája (shapes.txt) is bekerül, hogy a térkép
 * a valós vágányokat kövesse. Bináris fájl: gtfs/tram_index.bin.
 */
object TramIndex {

    private const val FILE_NAME = "tram_index.bin"
    private const val MAGIC = 0x54495834 // "TIX4"

    class Index(
        /** A forrás stop_times.txt azonosítója (módosítás ideje + méret); ha eltér, újra kell építeni. */
        val stamp: Long,
        val lines: Array<String>,
        val headsigns: Array<String>,
        val services: Array<String>,
        val tripLine: IntArray,
        val tripHeadsign: IntArray,
        val tripService: IntArray,
        /** stopId → [dep0, trip0, dep1, trip1, …] indulás szerint rendezve. */
        val stops: Map<String, IntArray>,
        /** GTFS trip_id-k (a FUTÁR-ban „BKK_” előtaggal). */
        val tripIds: Array<String>,
        /** Menet → pálya sorszáma a [shapes]-ben, vagy -1. */
        val tripShape: IntArray,
        /** Pályák: [lat0, lon0, lat1, lon1, …] mikrofokban (1e-6). */
        val shapes: Array<IntArray>,
        /** Forgalmi számok (a block_id-ból) és menetenként a sorszámuk, vagy -1. */
        val blocks: Array<String>,
        val tripBlock: IntArray,
    ) {
        private val tripLookup: Map<String, Int> by lazy {
            HashMap<String, Int>(tripIds.size * 2).also { m -> tripIds.forEachIndexed { i, id -> m[id] = i } }
        }

        /** A menet pályája (lat, lon párok mikrofokban), ha ismert. */
        private fun trip(tripId: String) = tripLookup[tripId] ?: tripLookup[tripId.removePrefix("BKK_")]

        fun shapeForTrip(tripId: String): IntArray? =
            trip(tripId)?.let { t -> tripShape[t].takeIf { it >= 0 }?.let { shapes[it] } }

        fun blockNoForTrip(tripId: String): String? =
            trip(tripId)?.let { t -> tripBlock[t].takeIf { it >= 0 }?.let { blocks[it] } }
    }

    private val mutex = Mutex()
    @Volatile private var cached: Index? = null

    private fun stampOf(dir: File): Long {
        val st = File(dir, "stop_times.txt")
        return st.lastModified() xor (st.length() shl 20)
    }

    /** Megvan-e friss index a GTFS-hez. */
    fun isFresh(dir: File): Boolean {
        val f = File(dir, FILE_NAME)
        if (!f.exists()) return false
        return runCatching {
            DataInputStream(f.inputStream().buffered()).use { it.readInt() == MAGIC && it.readLong() == stampOf(dir) }
        }.getOrDefault(false)
    }

    /** Az index (memóriából, fájlból, vagy szükség esetén most felépítve). */
    suspend fun get(dir: File, onBuildProgress: (Float) -> Unit = {}): Index = mutex.withLock {
        val stamp = stampOf(dir)
        cached?.takeIf { it.stamp == stamp }?.let { return@withLock it }
        withContext(Dispatchers.IO) {
            val index = if (isFresh(dir)) read(File(dir, FILE_NAME)) else build(dir, onBuildProgress)
            cached = index
            index
        }
    }

    /**
     * A menet pályája a GTFS shapes.txt-ből – csak ha már van friss index (a térkép miatt nem
     * indítunk hosszú építést); egyébként null.
     */
    suspend fun shapeForTrip(dir: File, tripId: String): IntArray? = ifReady(dir)?.shapeForTrip(tripId)

    /** A menet forgalmi száma a GTFS block_id-ból (ha már van friss index). */
    suspend fun blockNoForTrip(dir: File, tripId: String): String? = ifReady(dir)?.blockNoForTrip(tripId)

    private suspend fun ifReady(dir: File): Index? {
        if (cached?.stamp != stampOf(dir) && !isFresh(dir)) return null
        return runCatching { get(dir) }.getOrNull()
    }

    /** Index felépítése (pl. közvetlenül a GTFS letöltése után). */
    suspend fun rebuild(dir: File, onProgress: (Float) -> Unit) = mutex.withLock {
        withContext(Dispatchers.IO) { cached = build(dir, onProgress) }
    }

    private suspend fun build(dir: File, onProgress: (Float) -> Unit): Index {
        onProgress(0f)
        // 1) Villamos-viszonylatok
        val tramRoutes = BkkApi.loadRoutes(File(dir, "routes.txt"))
            .filterValues { it.type == 0 || it.type == 900 }
            .mapValues { (id, r) -> r.shortName.ifBlank { TramLines.nameFor(id) ?: id } }

        // 2) Villamos-menetek
        val lines = Interner(); val headsigns = Interner(); val services = Interner()
        val tripIndex = HashMap<String, Int>()
        val tLine = IntList(); val tHead = IntList(); val tSvc = IntList()
        val tIds = ArrayList<String>(); val shapeIds = Interner(); val tShape = IntList()
        val blocks = Interner(); val tBlock = IntList()
        File(dir, "trips.txt").bufferedReader().use { br ->
            val idx = BkkApi.headerIndexMap(br.readLine() ?: return@use)
            br.lineSequence().forEach { raw ->
                if (raw.isBlank()) return@forEach
                val cols = BkkApi.parseCsvLine(raw)
                val line = tramRoutes[BkkApi.getCol(cols, idx, "route_id")] ?: return@forEach
                tripIndex[BkkApi.getCol(cols, idx, "trip_id")] = tLine.size
                tLine += lines.id(line)
                tHead += headsigns.id(BkkApi.getCol(cols, idx, "trip_headsign").ifBlank { "?" })
                tSvc += services.id(BkkApi.getCol(cols, idx, "service_id"))
                tIds += BkkApi.getCol(cols, idx, "trip_id")
                tShape += BkkApi.getCol(cols, idx, "shape_id").let { if (it.isBlank()) -1 else shapeIds.id(it) }
                tBlock += Futar.blockNoOf(BkkApi.getCol(cols, idx, "block_id"))?.let(blocks::id) ?: -1
            }
        }

        // 3) stop_times.txt – egyetlen menet, a villamos-menetek soraira szűrve
        val stopTimes = File(dir, "stop_times.txt")
        val total = stopTimes.length().coerceAtLeast(1)
        val perStop = HashMap<String, IntList>()
        var readBytes = 0L
        var lastPct = -1
        stopTimes.bufferedReader().use { br ->
            val header = br.readLine() ?: return@use
            val idx = BkkApi.headerIndexMap(header)
            val iTrip = idx["trip_id"] ?: return@use
            val iStop = idx["stop_id"] ?: return@use
            val iDep = idx["departure_time"] ?: idx["arrival_time"] ?: return@use
            val maxCol = maxOf(iTrip, iStop, iDep)
            val starts = IntArray(maxCol + 2)
            var lineNo = 0
            while (true) {
                val raw = br.readLine() ?: break
                readBytes += raw.length + 1
                if (++lineNo and 0xFFFF == 0) {
                    coroutineContext.ensureActive()
                    val pct = (readBytes * 100 / total).toInt()
                    if (pct != lastPct) { lastPct = pct; onProgress(pct / 100f * 0.95f) }
                }
                // Gyors mezőbontás: idézőjel nélküli sorokon csak a vesszőket keressük
                val trip: String; val stop: String; val dep: String
                if (raw.indexOf('"') < 0) {
                    if (!fieldStarts(raw, starts, maxCol)) continue
                    trip = field(raw, starts, iTrip)
                    val t = tripIndex[trip] ?: continue
                    stop = field(raw, starts, iStop)
                    dep = field(raw, starts, iDep)
                    val sec = parseGtfsTime(dep)
                    if (sec < 0) continue
                    perStop.getOrPut(stop) { IntList() }.add2(sec, t)
                } else {
                    val cols = BkkApi.parseCsvLine(raw)
                    val t = tripIndex[cols.getOrNull(iTrip) ?: continue] ?: continue
                    val sec = parseGtfsTime(cols.getOrNull(iDep) ?: continue)
                    if (sec < 0) continue
                    perStop.getOrPut(cols.getOrNull(iStop) ?: continue) { IntList() }.add2(sec, t)
                }
            }
        }

        // 4) A villamos-menetek pályái (shapes.txt, ha van)
        val shapes = readShapes(File(dir, "shapes.txt"), shapeIds)

        val index = Index(
            stamp = stampOf(dir),
            lines = lines.toArray(), headsigns = headsigns.toArray(), services = services.toArray(),
            tripLine = tLine.toArray(), tripHeadsign = tHead.toArray(), tripService = tSvc.toArray(),
            stops = perStop.mapValues { (_, l) -> l.toSortedPairs() },
            tripIds = tIds.toTypedArray(), tripShape = tShape.toArray(), shapes = shapes,
            blocks = blocks.toArray(), tripBlock = tBlock.toArray(),
        )
        write(index, File(dir, FILE_NAME))
        onProgress(1f)
        return index
    }

    /** A megadott shape_id-k pontjai sorrendben, mikrofokban; a hiányzók üres tömbök. */
    private suspend fun readShapes(f: File, wanted: Interner): Array<IntArray> {
        val ids = wanted.toArray()
        if (ids.isEmpty() || !f.exists()) return Array(ids.size) { IntArray(0) }
        val lookup = ids.withIndex().associate { (i, id) -> id to i }
        // pontonként: (sorszám, lat, lon)
        val raw = Array(ids.size) { IntList() }
        f.bufferedReader().use { br ->
            val idx = BkkApi.headerIndexMap(br.readLine() ?: return@use)
            val iId = idx["shape_id"] ?: return@use
            val iSeq = idx["shape_pt_sequence"] ?: return@use
            val iLat = idx["shape_pt_lat"] ?: return@use
            val iLon = idx["shape_pt_lon"] ?: return@use
            val maxCol = maxOf(iId, iSeq, iLat, iLon)
            val starts = IntArray(maxCol + 2)
            var lineNo = 0
            while (true) {
                val line = br.readLine() ?: break
                if (++lineNo and 0xFFFF == 0) coroutineContext.ensureActive()
                val cols: (Int) -> String
                if (line.indexOf('"') < 0) {
                    if (!fieldStarts(line, starts, maxCol)) continue
                    cols = { field(line, starts, it) }
                } else {
                    val parsed = BkkApi.parseCsvLine(line)
                    cols = { parsed.getOrNull(it).orEmpty() }
                }
                val s = lookup[cols(iId)] ?: continue
                val seq = cols(iSeq).trim().toIntOrNull() ?: continue
                val lat = cols(iLat).trim().toDoubleOrNull() ?: continue
                val lon = cols(iLon).trim().toDoubleOrNull() ?: continue
                raw[s].add2(seq, (lat * 1e6).toInt()); raw[s] += (lon * 1e6).toInt()
            }
        }
        return Array(ids.size) { k ->
            val l = raw[k].toArray()
            val n = l.size / 3
            val order = (0 until n).sortedBy { l[it * 3] }
            IntArray(n * 2).also { out -> order.forEachIndexed { j, p -> out[j * 2] = l[p * 3 + 1]; out[j * 2 + 1] = l[p * 3 + 2] } }
        }
    }

    // --- fájlformátum ---

    private fun write(ix: Index, f: File) {
        val tmp = File(f.parentFile, "$FILE_NAME.tmp")
        DataOutputStream(BufferedOutputStream(tmp.outputStream(), 1 shl 16)).use { o ->
            o.writeInt(MAGIC); o.writeLong(ix.stamp)
            for (arr in listOf(ix.lines, ix.headsigns, ix.services)) { o.writeInt(arr.size); arr.forEach(o::writeUTF) }
            o.writeInt(ix.tripLine.size)
            for (i in ix.tripLine.indices) { o.writeInt(ix.tripLine[i]); o.writeInt(ix.tripHeadsign[i]); o.writeInt(ix.tripService[i]) }
            o.writeInt(ix.stops.size)
            for ((stop, pairs) in ix.stops) { o.writeUTF(stop); o.writeInt(pairs.size); pairs.forEach(o::writeInt) }
            for (i in ix.tripIds.indices) { o.writeUTF(ix.tripIds[i]); o.writeInt(ix.tripShape[i]) }
            o.writeInt(ix.shapes.size)
            for (shape in ix.shapes) { o.writeInt(shape.size); shape.forEach(o::writeInt) }
            o.writeInt(ix.blocks.size); ix.blocks.forEach(o::writeUTF)
            ix.tripBlock.forEach(o::writeInt)
        }
        f.delete()
        tmp.renameTo(f)
    }

    private fun read(f: File): Index = DataInputStream(BufferedInputStream(f.inputStream(), 1 shl 16)).use { i ->
        check(i.readInt() == MAGIC)
        val stamp = i.readLong()
        fun strings() = Array(i.readInt()) { i.readUTF() }
        val lines = strings(); val heads = strings(); val svcs = strings()
        val n = i.readInt()
        val tl = IntArray(n); val th = IntArray(n); val ts = IntArray(n)
        for (k in 0 until n) { tl[k] = i.readInt(); th[k] = i.readInt(); ts[k] = i.readInt() }
        val stopCount = i.readInt()
        val stops = HashMap<String, IntArray>(stopCount * 2)
        repeat(stopCount) {
            val id = i.readUTF()
            stops[id] = IntArray(i.readInt()) { i.readInt() }
        }
        val tripIds = Array(n) { "" }; val tripShape = IntArray(n)
        for (k in 0 until n) { tripIds[k] = i.readUTF(); tripShape[k] = i.readInt() }
        val shapes = Array(i.readInt()) { IntArray(i.readInt()) { i.readInt() } }
        val blocks = strings()
        val tripBlock = IntArray(n) { i.readInt() }
        Index(stamp, lines, heads, svcs, tl, th, ts, stops, tripIds, tripShape, shapes, blocks, tripBlock)
    }

    // --- segédek ---

    /** „HH:MM:SS” → másodperc (a 24:00 utáni, következő napra átnyúló időket is kezeli); hibásnál -1. */
    fun parseGtfsTime(s: String): Int {
        val a = s.indexOf(':'); val b = s.indexOf(':', a + 1)
        if (a <= 0 || b <= a) return -1
        val h = s.substring(0, a).trim().toIntOrNull() ?: return -1
        val m = s.substring(a + 1, b).toIntOrNull() ?: return -1
        val sec = s.substring(b + 1).trim().toIntOrNull() ?: return -1
        return h * 3600 + m * 60 + sec
    }

    /** A vesszővel elválasztott mezők kezdőpozíciói 0..maxCol+1-ig; false, ha kevés a mező. */
    private fun fieldStarts(line: String, starts: IntArray, maxCol: Int): Boolean {
        starts[0] = 0
        var col = 1; var p = 0
        while (col <= maxCol + 1) {
            val c = line.indexOf(',', p)
            if (c < 0) { starts[col] = line.length + 1; return col > maxCol }
            starts[col] = c + 1; p = c + 1; col++
        }
        return true
    }

    private fun field(line: String, starts: IntArray, col: Int) = line.substring(starts[col], starts[col + 1] - 1)

    private class Interner {
        private val map = HashMap<String, Int>(); private val list = ArrayList<String>()
        fun id(s: String) = map.getOrPut(s) { list += s; list.size - 1 }
        fun toArray() = list.toTypedArray()
    }

    private class IntList {
        private var a = IntArray(16); var size = 0; private set
        operator fun plusAssign(v: Int) { if (size == a.size) a = a.copyOf(size * 2); a[size++] = v }
        fun add2(x: Int, y: Int) { plusAssign(x); plusAssign(y) }
        fun toArray() = a.copyOf(size)
        /** Párok (indulás, menet) rendezése indulás szerint. */
        fun toSortedPairs(): IntArray {
            val n = size / 2
            val order = (0 until n).sortedBy { a[it * 2] }
            return IntArray(size).also { out -> order.forEachIndexed { k, j -> out[k * 2] = a[j * 2]; out[k * 2 + 1] = a[j * 2 + 1] } }
        }
    }
}
