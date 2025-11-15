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

/**
 * DK3 képernyő (közeli megállók és indulások)
 *
 * A felhasználó aktuális pozíciója alapján:
 * - megkeresi a 300 méteren belüli megállókat a GTFS állományokból
 * - ezekhez megkeresi a legközelebbi villamos-indulásokat (csak aznapi, jövőbeli indulások)
 * - kiírja megállónként a következő indulást és az irányt (headsign)
 */
class Dk3aActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDk3Binding

    // Google Fused Location Provider – utolsó ismert / aktuális pozícióhoz
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }

    // Alapértelmezett koordináta (pl. belváros), ha a pozíció lekérése nem sikerül
    private val DEFAULT_LAT = 47.49794
    private val DEFAULT_LON = 19.05403

    /**
     * Runtime location permission kérés eredménykezelője.
     * Ha bármelyik (fine vagy coarse) engedélyezve lett, indulhat a lekérdezés.
     */
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

        // "Lekérdezés" gomb – elindítja a jogosultság ellenőrzést, majd a pozíció alapú keresést
        binding.btnQuery.setOnClickListener {
            ensurePermissionAndRun()
        }
    }

    /**
     * Ellenőrzi, hogy rendelkezünk-e már helymeghatározási engedéllyel.
     * Ha igen → pozíció lekérdezése.
     * Ha nem → runtime permission dialógus megjelenítése.
     */
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

    /**
     * Pozíció lekérdezése (utolsó ismert vagy friss) és a közeli megállók/indulások lekérdezése.
     *
     * - Először megpróbáljuk az utolsó ismert helyet (gyors, cache-ből)
     * - Ha nincs, akkor getCurrentLocation API (frissebb, de lassabb)
     * - Ha semmi nem működik → alapértelmezett (Budapest, Széll Kélmán tér) koordináták
     */
    private fun fetchAndQuery() {
        binding.textResult.text = "Pozíció lekérése…"

        fused.lastLocation
            .addOnSuccessListener { last: Location? ->
                if (last != null) {
                    // Utolsó ismert pozíció sikeresen lekérve
                    queryNearby(last.latitude, last.longitude, from = "LastLocation")
                } else {
                    // Nincs cache-elt pozíció – kérünk egy frissebbet
                    fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                        .addOnSuccessListener { fresh ->
                            if (fresh != null) {
                                queryNearby(fresh.latitude, fresh.longitude, from = "CurrentLocation")
                            } else {
                                // Ha ez sem sikerül, default koordinátára esünk vissza
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

    /**
     * Indulások lekérdezése egy konkrét pozíció környezetében.
     *
     * A tényleges GTFS feldolgozás és szűrés a findNearbyDepartures suspend függvényben történik.
     */
    private fun queryNearby(lat: Double, lon: Double, from: String) {
        binding.textResult.text = "Megállók és indulások lekérése…\nForrás: $from\n($lat, $lon)"
        lifecycleScope.launch {
            val lines = findNearbyDepartures(lat, lon)
            binding.textResult.text = "Forrás: $from\n($lat, $lon)\n\n" + lines.joinToString("\n")
        }
    }

    /**
     * GTFS fájlokból közeli megállók és indulások keresése.
     *
     * - Csak villamos típusú járatok (route_type == 0 vagy 900)
     * - Megállónként maximum 1 indulás (a legközelebbi a következő 60 percen belül)
     * - Csak aznapi, jövőbeli indulások (HH:mm:ss alapján, 24+ órás értékek kizárva)
     *
     * @param lat aktuális szélesség
     * @param lon aktuális hosszúság
     * @param radius sugár méterben, amin belül keressük a megállókat (default: 300 m)
     * @param maxMinutes legtávolabbi indulási idő (percben) – default: 60 perc
     */
    private suspend fun findNearbyDepartures(
        lat: Double,
        lon: Double,
        radius: Double = 300.0,
        maxMinutes: Int = 60
    ): List<String> = withContext(Dispatchers.IO) {
        val gtfsDir = File(filesDir, "gtfs")

        // GTFS statikus állományok: megállók, trip-ek, viszonylatok
        val stops = BkkApi.loadStops(File(gtfsDir, "stops.txt"))
        val trips = BkkApi.loadTrips(File(gtfsDir, "trips.txt")).associateBy { it.tripId }
        val routes = BkkApi.loadRoutes(File(gtfsDir, "routes.txt"))

        val zone = ZoneId.of("Europe/Budapest")
        val now = LocalTime.now(zone)
        val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")

        // Közeli megállók kiszűrése Haversine-távolság alapján
        val nearbyStops = stops
            .map { it to BkkApi.haversine(lat, lon, it.lat, it.lon) }
            .filter { it.second <= radius }
            .sortedBy { it.second }

        if (nearbyStops.isEmpty()) {
            return@withContext listOf("ℹ️ Nincs megálló ${radius.toInt()} méteren belül.")
        }

        val nearbyStopIds = nearbyStops.map { it.first.stopId }.toSet()
        val results = mutableListOf<String>()
        // Egy megállóból csak egy indulást jelenítünk meg
        val addedStops = mutableSetOf<String>()

        val stopTimesFile = File(gtfsDir, "stop_times.txt")
        if (!stopTimesFile.exists()) {
            return@withContext listOf("❌ Hiányzik a stop_times.txt")
        }

        // stop_times.txt streaming-szerű bejárása soronként,
        // hogy ne kelljen az egész fájlt memóriába tölteni.
        stopTimesFile.bufferedReader().use { br ->
            val header = br.readLine() ?: return@use
            val idx = BkkApi.headerIndexMap(header)

            br.lineSequence().forEach { raw ->
                if (raw.isBlank()) return@forEach
                val cols = BkkApi.parseCsvLine(raw)
                val tripId = BkkApi.getCol(cols, idx, "trip_id")
                val stopId = BkkApi.getCol(cols, idx, "stop_id")
                val depTimeStr = BkkApi.getCol(cols, idx, "departure_time")

                // Csak a közeli megállókat vizsgáljuk, és egy megállóból csak 1 indulást veszünk fel
                if (stopId !in nearbyStopIds || stopId in addedStops) return@forEach

                val trip = trips[tripId] ?: return@forEach
                val route = routes[trip.routeId] ?: return@forEach

                // Csak villamos jellegű járatok (0 = light rail / villamos, 900 = egyéb villamos kategória)
                if (route.type != 0 && route.type != 900) return@forEach

                // 24+ órás (következő napi) időket kihagyjuk
                val hour = depTimeStr.takeWhile { it != ':' }.toIntOrNull() ?: return@forEach
                if (hour >= 24) return@forEach

                val depTime = try {
                    LocalTime.parse(depTimeStr, formatter)
                } catch (_: Exception) {
                    null
                }

                // Csak aznapi, jövőbeli indulások (mosttól maxMinutes percig)
                if (depTime != null && (depTime.isAfter(now) || depTime == now)) {
                    val diff = Duration.between(now, depTime).toMinutes()
                    if (diff in 0..maxMinutes) {
                        val stop = nearbyStops.first { it.first.stopId == stopId }.first
                        val line = if (route.shortName.isNotBlank()) route.shortName else trip.routeId

                        results.add(
                            "🚏 ${stop.stopName} (${stop.stopId}) - " +
                                    "${"%.1f".format(BkkApi.haversine(lat, lon, stop.lat, stop.lon))} m"
                        )
                        results.add(
                            "   ⏱ ${depTime.withSecond(0)} | $line → ${trip.tripHeadsign ?: "?"}"
                        )

                        // Megjegyezzük, hogy ebből a megállóból már választottunk 1 indulást
                        addedStops += stopId
                    }
                }
            }
        }

        if (results.isEmpty()) {
            listOf("ℹ️ Nincs villamos indulás $maxMinutes percen belül.")
        } else {
            results
        }
    }
}
