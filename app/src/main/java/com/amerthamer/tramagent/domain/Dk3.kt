package com.amerthamer.tramagent.domain

import com.amerthamer.tramagent.data.BkkApi
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
 * DK3 – Közeli indulások a letöltött GTFS alapján (korábban a Dk3aActivity-ben volt).
 *
 * - a megadott sugáron belüli megállók (stops.txt, Haversine)
 * - megállónként a legkorábbi [perStop] villamos-indulás a következő [maxMinutes] percben
 * - csak villamos (route_type 0 vagy 900), csak aznapi időpontok (24:00 utániak nélkül)
 * - csak a ma közlekedő menetek (calendar.txt / calendar_dates.txt szerint)
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

    suspend fun findNearby(
        gtfsDir: File,
        lat: Double,
        lon: Double,
        now: LocalTime,
        today: LocalDate = LocalDate.now(ZoneId.of("Europe/Budapest")),
        radius: Double = 300.0,
        maxMinutes: Int = 60,
        perStop: Int = 3,
    ): Result = withContext(Dispatchers.IO) {
        val stopsFile = File(gtfsDir, "stops.txt")
        val stopTimesFile = File(gtfsDir, "stop_times.txt")
        if (!stopsFile.exists() || !stopTimesFile.exists()) return@withContext Result.MissingGtfs

        val nearby = BkkApi.loadStops(stopsFile)
            .map { it to BkkApi.haversine(lat, lon, it.lat, it.lon) }
            .filter { it.second <= radius }
        if (nearby.isEmpty()) return@withContext Result.Success(emptyList(), anyStopInRadius = false)

        val nearbyIds = nearby.map { it.first.stopId }.toSet()
        val tramRoutes = BkkApi.loadRoutes(File(gtfsDir, "routes.txt")).filterValues { it.type == 0 || it.type == 900 }
        val trips = loadTodaysTramTrips(gtfsDir, tramRoutes.keys, activeServices(gtfsDir, today))

        val horizon = now.plusMinutes(maxMinutes.toLong())
        val wrapsMidnight = horizon < now
        val found = mutableMapOf<String, MutableList<Departure>>()

        // A stop_times.txt nagy: soronként olvassuk, és csak a közeli megállók sorait bontjuk fel
        stopTimesFile.bufferedReader().use { br ->
            val idx = BkkApi.headerIndexMap(br.readLine() ?: return@use)
            br.lineSequence().forEach { raw ->
                if (raw.isBlank() || nearbyIds.none { raw.contains(it) }) return@forEach
                val cols = BkkApi.parseCsvLine(raw)
                val stopId = BkkApi.getCol(cols, idx, "stop_id")
                if (stopId !in nearbyIds) return@forEach

                val depStr = BkkApi.getCol(cols, idx, "departure_time")
                val hour = depStr.substringBefore(':').toIntOrNull() ?: return@forEach
                if (hour >= 24) return@forEach
                val dep = runCatching { LocalTime.parse(depStr.padStart(8, '0')) }.getOrNull() ?: return@forEach
                val inWindow = if (wrapsMidnight) dep >= now || dep <= horizon else dep >= now && dep <= horizon
                if (!inWindow) return@forEach

                val trip = trips[BkkApi.getCol(cols, idx, "trip_id")] ?: return@forEach
                val route = tramRoutes[trip.routeId] ?: return@forEach

                found.getOrPut(stopId) { mutableListOf() } += Departure(
                    time = dep,
                    line = route.shortName.ifBlank { TramLines.nameFor(trip.routeId) ?: trip.routeId },
                    headsign = trip.headsign ?: "?",
                )
            }
        }

        // Éjfélen átnyúló ablaknál az éjfél utáni indulások a későbbiek
        fun sortKey(d: Departure) = if (wrapsMidnight && d.time < now) d.time.toSecondOfDay() + 86_400 else d.time.toSecondOfDay()

        val stops = nearby.mapNotNull { (stop, dist) ->
            val deps = found[stop.stopId] ?: return@mapNotNull null
            NearbyStop(stop.stopId, stop.stopName, dist, deps.sortedBy(::sortKey).distinct().take(perStop))
        }.sortedBy { it.distanceM }

        Result.Success(stops, anyStopInRadius = true)
    }

    private data class TripInfo(val routeId: String, val headsign: String?)

    /** Csak a villamos- és a mai napon közlekedő menetek a trips.txt-ből (null [services] = nincs naptárszűrés). */
    private fun loadTodaysTramTrips(dir: File, tramRouteIds: Set<String>, services: Set<String>?): Map<String, TripInfo> {
        val out = HashMap<String, TripInfo>()
        File(dir, "trips.txt").bufferedReader().use { br ->
            val idx = BkkApi.headerIndexMap(br.readLine() ?: return out)
            br.lineSequence().forEach { raw ->
                if (raw.isBlank()) return@forEach
                val cols = BkkApi.parseCsvLine(raw)
                val routeId = BkkApi.getCol(cols, idx, "route_id")
                if (routeId !in tramRouteIds) return@forEach
                if (services != null && BkkApi.getCol(cols, idx, "service_id") !in services) return@forEach
                out[BkkApi.getCol(cols, idx, "trip_id")] =
                    TripInfo(routeId, BkkApi.getCol(cols, idx, "trip_headsign").ifBlank { null })
            }
        }
        return out
    }

    /**
     * A [date] napon érvényes service_id-k a calendar.txt (heti minta + érvényesség)
     * és a calendar_dates.txt (1 = hozzáadva, 2 = törölve) alapján.
     * Ha egyik fájl sincs meg, null (nem szűrünk).
     */
    private fun activeServices(dir: File, date: LocalDate): Set<String>? {
        val calendar = File(dir, "calendar.txt")
        val dates = File(dir, "calendar_dates.txt")
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
        return active
    }
}
