package com.example.na3.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

// -------------------------------------------------------------
// BKK API/GTFS segéd
// - Robusztus CSV parser
// - trip_headsign támogatás
// - ÚJ: routes.txt betöltése (route_type, route_short_name)
// -------------------------------------------------------------
object BkkApi {
    const val API_KEY = "2cde79f6-2ac0-4c42-88a3-9e15208ee44e"
    private const val FUTAR_BASE = "https://futar.bkk.hu/api/query/v1/ws/otp/api/where"
    private const val GO_BASE = "https://go.bkk.hu/api/query/v1/ws/otp/api/where"

    private val client: OkHttpClient by lazy {
        val log = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        OkHttpClient.Builder().addInterceptor(log).build()
    }

    // -----------------------------
    // HTTP hívások
    // -----------------------------
    suspend fun getTripDetails(tripId: String): List<JSONObject> = withContext(Dispatchers.IO) {
        val url = "$FUTAR_BASE/trip-details?key=$API_KEY&version=2&appVersion=1.0&tripId=$tripId"
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext emptyList()
            val body = JSONObject(resp.body!!.string())
            val stopTimes = body.optJSONObject("data")?.optJSONObject("entry")?.optJSONArray("stopTimes")
                ?: return@withContext emptyList()
            return@withContext List(stopTimes.length()) { stopTimes.getJSONObject(it) }
        }
    }

    suspend fun getVehicleForQuery(query: String): JSONObject? = withContext(Dispatchers.IO) {
        val url =
            "$FUTAR_BASE/vehicles-for-location?key=$API_KEY&version=2&appVersion=1.0&includeReferences=true&query=$query"
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext null
            val body = JSONObject(resp.body!!.string())
            val list = body.optJSONObject("data")?.optJSONArray("list") ?: return@withContext null
            if (list.length() == 0) return@withContext null
            list.getJSONObject(0)
        }
    }

    suspend fun getVehiclesForRoute(routeId: String): Pair<List<JSONObject>, JSONObject?> =
        withContext(Dispatchers.IO) {
            val url =
                "$GO_BASE/vehicles-for-route?routeId=$routeId&key=$API_KEY&version=2&dialect=otp&includeReferences=true"
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext Pair(emptyList(), null)
                val body = JSONObject(resp.body!!.string())
                val data = body.optJSONObject("data")
                val list = data?.optJSONArray("list") ?: return@withContext Pair(emptyList(), null)
                val refs = data.optJSONObject("references")
                Pair(List(list.length()) { list.getJSONObject(it) }, refs)
            }
        }

    // -----------------------------
    // Távolság
    // -----------------------------
    fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371e3 // méter
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dphi = Math.toRadians(lat2 - lat1)
        val dl = Math.toRadians(lon2 - lon1)
        val a = sin(dphi / 2).pow(2.0) + cos(phi1) * cos(phi2) * sin(dl / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }

    // -----------------------------
    // CSV parser + segédek
    // -----------------------------
    fun parseCsvLine(line: String): List<String> {
        val out = ArrayList<String>(16)
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when (c) {
                '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"'); i++ // "" -> "
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ',' -> if (inQuotes) sb.append(c) else { out += sb.toString(); sb.setLength(0) }
                else -> sb.append(c)
            }
            i++
        }
        out += sb.toString()
        return out
    }

    fun headerIndexMap(headerLine: String): Map<String, Int> =
        parseCsvLine(headerLine).mapIndexed { idx, name -> name.trim().lowercase() to idx }.toMap()

    fun getCol(cols: List<String>, idx: Map<String, Int>, vararg names: String): String {
        for (n in names) {
            val i = idx[n.lowercase()]
            if (i != null && i in cols.indices) return cols[i]
        }
        return ""
    }

    // -----------------------------
    // Betöltők (stops, trips, stop_times, routes)
    // -----------------------------
    fun loadStops(stopsFile: File): List<Stop> {
        if (!stopsFile.exists()) return emptyList()
        val out = mutableListOf<Stop>()
        stopsFile.bufferedReader(Charsets.UTF_8).use { br ->
            val header = br.readLine() ?: return emptyList()
            val idx = headerIndexMap(header)
            br.lineSequence().forEach { raw ->
                if (raw.isBlank()) return@forEach
                val cols = parseCsvLine(raw)
                val stopId = getCol(cols, idx, "stop_id").trim()
                if (stopId.isEmpty()) return@forEach
                val stopName = getCol(cols, idx, "stop_name").ifBlank { stopId }.trim()
                val lat = getCol(cols, idx, "stop_lat").toDoubleOrNull() ?: return@forEach
                val lon = getCol(cols, idx, "stop_lon").toDoubleOrNull() ?: return@forEach
                out += Stop(stopId = stopId, stopName = stopName, lat = lat, lon = lon)
            }
        }
        return out
    }

    fun loadTrips(tripsFile: File): List<Trip> {
        if (!tripsFile.exists()) return emptyList()
        val out = mutableListOf<Trip>()
        tripsFile.bufferedReader(Charsets.UTF_8).use { br ->
            val header = br.readLine() ?: return emptyList()
            val idx = headerIndexMap(header)
            br.lineSequence().forEach { raw ->
                if (raw.isBlank()) return@forEach
                val cols = parseCsvLine(raw)
                val tripId = getCol(cols, idx, "trip_id").trim()
                if (tripId.isEmpty()) return@forEach
                val routeId = getCol(cols, idx, "route_id").trim()
                val headsign = getCol(cols, idx, "trip_headsign").trim().ifBlank { null }
                out += Trip(tripId = tripId, routeId = routeId, tripHeadSign = headsign)
            }
        }
        return out
    }

    fun loadStopTimes(stopTimesFile: File): List<StopTime> {
        if (!stopTimesFile.exists()) return emptyList()
        val out = mutableListOf<StopTime>()
        stopTimesFile.bufferedReader(Charsets.UTF_8).use { br ->
            val header = br.readLine() ?: return emptyList()
            val idx = headerIndexMap(header)
            br.lineSequence().forEach { raw ->
                if (raw.isBlank()) return@forEach
                val cols = parseCsvLine(raw)
                out += StopTime(
                    tripId = getCol(cols, idx, "trip_id"),
                    stopId = getCol(cols, idx, "stop_id"),
                    arrivalTime = getCol(cols, idx, "arrival_time"),
                    departureTime = getCol(cols, idx, "departure_time")
                )
            }
        }
        return out
    }

    // ÚJ: routes.txt
    fun loadRoutes(routesFile: File): Map<String, Route> {
        if (!routesFile.exists()) return emptyMap()
        val map = mutableMapOf<String, Route>()
        routesFile.bufferedReader(Charsets.UTF_8).use { br ->
            val header = br.readLine() ?: return emptyMap()
            val idx = headerIndexMap(header)
            br.lineSequence().forEach { raw ->
                if (raw.isBlank()) return@forEach
                val cols = parseCsvLine(raw)
                val routeId = getCol(cols, idx, "route_id").trim()
                if (routeId.isEmpty()) return@forEach
                val shortName = getCol(cols, idx, "route_short_name").trim()
                val type = getCol(cols, idx, "route_type").trim().toIntOrNull() ?: -1
                map[routeId] = Route(routeId, shortName, type)
            }
        }
        return map
    }

    fun formatTimestampMillisUtc(millis: Long?): String? {
        millis ?: return null
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            sdf.format(Date(millis))
        } catch (_: Exception) {
            null
        }
    }
}

// --- Adatmodellek ---
data class Stop(
    val stopId: String,
    val stopName: String,
    val lat: Double,
    val lon: Double
)

data class Trip(
    val tripId: String,
    val routeId: String,
    val tripHeadSign: String? = null,
    val tripHeadsign: String? = tripHeadSign
)

data class StopTime(
    val tripId: String,
    val stopId: String,
    val arrivalTime: String,
    val departureTime: String
)

// ÚJ
data class Route(
    val routeId: String,
    val shortName: String,
    val type: Int // GTFS route_type (0 = tram/light rail, 3 = bus, stb.)
)
