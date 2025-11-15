package com.amerthamer.tramagent.ui

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.amerthamer.tramagent.databinding.ActivityDk3Binding
import com.amerthamer.tramagent.data.BkkApi
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class Dk3aActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDk3Binding
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }

    private val DEFAULT_LAT = 47.49794
    private val DEFAULT_LON = 19.05403

    private val requestLocationPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            fetchAndQuery()
        } else {
            binding.textResult.text =
                "A helymeghatározási engedély szükséges a közeli megállók megjelenítéséhez."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDk3Binding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnQuery.setOnClickListener {
            ensurePermissionAndRun()
        }
    }

    private fun ensurePermissionAndRun() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            fetchAndQuery()
        } else {
            requestLocationPerms.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun fetchAndQuery() {
        binding.textResult.text = "Pozíció lekérése…"

        fused.lastLocation
            .addOnSuccessListener { last: Location? ->
                if (last != null) {
                    queryNearby(last.latitude, last.longitude, from = "LastLocation")
                } else {
                    fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                        .addOnSuccessListener { fresh ->
                            if (fresh != null) {
                                queryNearby(fresh.latitude, fresh.longitude, from = "CurrentLocation")
                            } else {
                                queryNearby(DEFAULT_LAT, DEFAULT_LON, from = "Default (emulált)")
                            }
                        }
                        .addOnFailureListener {
                            queryNearby(DEFAULT_LAT, DEFAULT_LON, from = "Default (hiba miatt)")
                        }
                }
            }
            .addOnFailureListener {
                queryNearby(DEFAULT_LAT, DEFAULT_LON, from = "Default (hiba miatt)")
            }
    }

    private fun queryNearby(lat: Double, lon: Double, from: String) {
        binding.textResult.text = "Megállók és indulások lekérése…\nForrás: $from\n($lat, $lon)"
        lifecycleScope.launch {
            val lines = findNearbyDepartures(lat, lon)
            binding.textResult.text = "Forrás: $from\n($lat, $lon)\n\n" + lines.joinToString("\n")
        }
    }

    /**
     * GTFS fájlokból közeli megállók és indulások keresése.
     * - Csak villamosok (route_type == 0 vagy 900)
     * - Megállónként csak a legközelebbi indulás
     * - Csak aznapi, jövőbeli indulások
     */
    private suspend fun findNearbyDepartures(
        lat: Double,
        lon: Double,
        radius: Double = 300.0,
        maxMinutes: Int = 60
    ): List<String> = withContext(Dispatchers.IO) {
        val gtfsDir = File(filesDir, "gtfs")
        val stops = BkkApi.loadStops(File(gtfsDir, "stops.txt"))
        val trips = BkkApi.loadTrips(File(gtfsDir, "trips.txt")).associateBy { it.tripId }
        val routes = BkkApi.loadRoutes(File(gtfsDir, "routes.txt")) // <— ÚJ

        val zone = ZoneId.of("Europe/Budapest")
        val now = LocalTime.now(zone)
        val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")

        val nearbyStops = stops.map { it to BkkApi.haversine(lat, lon, it.lat, it.lon) }
            .filter { it.second <= radius }
            .sortedBy { it.second }

        if (nearbyStops.isEmpty()) {
            return@withContext listOf("ℹ️ Nincs megálló ${radius.toInt()} méteren belül.")
        }

        val nearbyStopIds = nearbyStops.map { it.first.stopId }.toSet()
        val results = mutableListOf<String>()
        val addedStops = mutableSetOf<String>() // egy megállóból csak 1 indulás

        val stopTimesFile = File(gtfsDir, "stop_times.txt")
        if (!stopTimesFile.exists()) {
            return@withContext listOf("❌ Hiányzik a stop_times.txt")
        }

        stopTimesFile.bufferedReader().use { br ->
            val header = br.readLine() ?: return@use
            val idx = BkkApi.headerIndexMap(header)

            br.lineSequence().forEach { raw ->
                if (raw.isBlank()) return@forEach
                val cols = BkkApi.parseCsvLine(raw)
                val tripId = BkkApi.getCol(cols, idx, "trip_id")
                val stopId = BkkApi.getCol(cols, idx, "stop_id")
                val depTimeStr = BkkApi.getCol(cols, idx, "departure_time")

                if (stopId !in nearbyStopIds || stopId in addedStops) return@forEach
                val trip = trips[tripId] ?: return@forEach
                val route = routes[trip.routeId] ?: return@forEach

                if (route.type != 0 && route.type != 900) return@forEach

                val hour = depTimeStr.takeWhile { it != ':' }.toIntOrNull() ?: return@forEach
                if (hour >= 24) return@forEach

                val depTime = try { LocalTime.parse(depTimeStr, formatter) } catch (_: Exception) { null }
                if (depTime != null && (depTime.isAfter(now) || depTime == now)) {
                    val diff = Duration.between(now, depTime).toMinutes()
                    if (diff in 0..maxMinutes) {
                        val stop = nearbyStops.first { it.first.stopId == stopId }.first
                        val line = if (route.shortName.isNotBlank()) route.shortName else trip.routeId
                        results.add("🚏 ${stop.stopName} (${stop.stopId}) - ${"%.1f".format(BkkApi.haversine(lat, lon, stop.lat, stop.lon))} m")
                        results.add("   ⏱ ${depTime.withSecond(0)} | $line → ${trip.tripHeadsign ?: "?"}")
                        addedStops += stopId
                    }
                }
            }
        }

        if (results.isEmpty()) listOf("ℹ️ Nincs villamos indulás $maxMinutes percen belül.")
        else results
    }
}
