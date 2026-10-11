package com.amerthamer.tramagent.domain

import com.amerthamer.tramagent.data.BkkApi
import com.amerthamer.tramagent.gtfs.TramIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * DK3 – Közeli indulások a letöltött GTFS alapján.
 *
 * - a megadott sugáron belüli megállók (stops.txt, Haversine)
 * - megállónként a legkorábbi [perStop] villamos-indulás a következő [maxMinutes] percben
 * - csak a ma közlekedő menetek (calendar.txt / calendar_dates.txt szerint), beleértve
 *   az előző napi menetrend éjfél utáni (24:xx) indulásait is
 * - a stop_times helyett az előre elkészített villamos-indexből dolgozik (TramIndex)
 */
object Dk3 {

    data class Departure(val time: LocalTime, val line: String, val headsign: String)

    data class NearbyStop(
        val stopId: String,
        val name: String,
        val distanceM: Double,
        val departures: List<Departure>,
    )

    sealed interface Result {
        data class Success(val stops: List<NearbyStop>, val anyStopInRadius: Boolean) : Result
        data object MissingGtfs : Result
    }

    private const val DAY = 86_400

    /**
     * @param onIndexing ha még nincs index (pl. régebbi GTFS-letöltés), az elkészítés haladása 0..1
     */
    suspend fun findNearby(
        gtfsDir: File,
        lat: Double,
        lon: Double,
        now: LocalTime,
        today: LocalDate = LocalDate.now(ZoneId.of("Europe/Budapest")),
        radius: Double = 300.0,
        maxMinutes: Int = 60,
        perStop: Int = 3,
        onIndexing: (Float) -> Unit = {},
    ): Result = withContext(Dispatchers.IO) {
        val stopsFile = File(gtfsDir, "stops.txt")
        if (!stopsFile.exists() || !File(gtfsDir, "stop_times.txt").exists()) return@withContext Result.MissingGtfs

        val nearby = BkkApi.loadStops(stopsFile)
            .map { it to BkkApi.haversine(lat, lon, it.lat, it.lon) }
            .filter { it.second <= radius }
        if (nearby.isEmpty()) return@withContext Result.Success(emptyList(), anyStopInRadius = false)

        val index = TramIndex.get(gtfsDir, onIndexing)
        val todayActive = activeMask(index, activeServices(gtfsDir, today))
        val yesterdayActive = activeMask(index, activeServices(gtfsDir, today.minusDays(1)))

        val from = now.toSecondOfDay()
        val to = from + maxMinutes * 60

        val stops = nearby.mapNotNull { (stop, dist) ->
            val pairs = index.stops[stop.stopId] ?: return@mapNotNull null
            // (tényleges idő mai 0:00-tól, menet)
            val found = ArrayList<Pair<Int, Int>>()
            var k = 0
            while (k < pairs.size) {
                val dep = pairs[k]; val trip = pairs[k + 1]; k += 2
                val svc = index.tripService[trip]
                if (todayActive[svc] && dep in from..to) found += dep to trip
                // Tegnapi menetrend éjfél utáni indulása (pl. 24:35 → ma 00:35)
                if (yesterdayActive[svc] && dep >= DAY && dep - DAY in from..to) found += (dep - DAY) to trip
            }
            if (found.isEmpty()) return@mapNotNull null
            val deps = found.sortedBy { it.first }.map { (sec, trip) ->
                Departure(
                    time = LocalTime.ofSecondOfDay((sec % DAY).toLong()),
                    line = index.lines[index.tripLine[trip]],
                    headsign = index.headsigns[index.tripHeadsign[trip]],
                )
            }.distinct().take(perStop)
            NearbyStop(stop.stopId, stop.stopName, dist, deps)
        }.sortedBy { it.distanceM }

        Result.Success(stops, anyStopInRadius = true)
    }

    /** Service-sorszám → aktív-e; ha nincs naptárinformáció (null), minden aktív. */
    private fun activeMask(index: TramIndex.Index, active: Set<String>?): BooleanArray =
        BooleanArray(index.services.size) { active == null || index.services[it] in active }

    private val serviceCache = HashMap<Pair<String, LocalDate>, Set<String>?>()

    /**
     * A [date] napon érvényes service_id-k a calendar.txt (heti minta + érvényesség)
     * és a calendar_dates.txt (1 = hozzáadva, 2 = törölve) alapján.
     * Ha egyik fájl sincs meg, null (nem szűrünk).
     */
    private fun activeServices(dir: File, date: LocalDate): Set<String>? = synchronized(serviceCache) {
        val calendar = File(dir, "calendar.txt")
        val dates = File(dir, "calendar_dates.txt")
        val key = (dir.path + ":" + dates.lastModified() + ":" + calendar.lastModified()) to date
        serviceCache[key]?.let { return it }
        if (!calendar.exists() && !dates.exists()) return null

        val ymd = date.format(DateTimeFormatter.BASIC_ISO_DATE)
        val active = HashSet<String>()
        if (calendar.exists()) calendar.bufferedReader().use { br ->
            val idx = BkkApi.headerIndexMap(br.readLine() ?: return@use)
            val dayCol = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH).lowercase(Locale.ROOT)
            br.lineSequence().forEach { raw ->
                if (raw.isBlank()) return@forEach
                val cols = BkkApi.parseCsvLine(raw)
                val inRange = BkkApi.getCol(cols, idx, "start_date") <= ymd && ymd <= BkkApi.getCol(cols, idx, "end_date")
                if (inRange && BkkApi.getCol(cols, idx, dayCol) == "1") active += BkkApi.getCol(cols, idx, "service_id")
            }
        }
        if (dates.exists()) dates.bufferedReader().use { br ->
            val idx = BkkApi.headerIndexMap(br.readLine() ?: return@use)
            br.lineSequence().forEach { raw ->
                if (raw.isBlank()) return@forEach
                val cols = BkkApi.parseCsvLine(raw)
                if (BkkApi.getCol(cols, idx, "date") != ymd) return@forEach
                val id = BkkApi.getCol(cols, idx, "service_id")
                when (BkkApi.getCol(cols, idx, "exception_type")) {
                    "1" -> active += id
                    "2" -> active -= id
                }
            }
        }
        serviceCache[key] = active
        active
    }
}
