package com.amerthamer.tramagent.gtfs

import com.amerthamer.tramagent.data.BkkApi
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
 * az irány és a service_id tartozik. Bináris fájl: gtfs/tram_index.bin.
 */
object TramIndex {

    private const val FILE_NAME = "tram_index.bin"
    private const val MAGIC = 0x54495832 // "TIX2"

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
    )

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
                    if (pct != lastPct) { lastPct = pct; onProgress(pct / 100f) }
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

        val index = Index(
            stamp = stampOf(dir),
            lines = lines.toArray(), headsigns = headsigns.toArray(), services = services.toArray(),
            tripLine = tLine.toArray(), tripHeadsign = tHead.toArray(), tripService = tSvc.toArray(),
            stops = perStop.mapValues { (_, l) -> l.toSortedPairs() },
        )
        write(index, File(dir, FILE_NAME))
        onProgress(1f)
        return index
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
        Index(stamp, lines, heads, svcs, tl, th, ts, stops)
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
