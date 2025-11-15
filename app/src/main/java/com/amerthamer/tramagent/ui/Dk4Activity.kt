package com.amerthamer.tramagent.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.amerthamer.tramagent.R
import com.amerthamer.tramagent.data.BkkApi
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

/**
 * TÉRKÉPES KERESŐ képernyő
 *
 * Egy konkrét jármű (pályaszám alapján) valós idejű pozícióját jeleníti meg a térképen,
 * és opcionálisan kirajzolja a hozzá tartozó trip megállóit is a GTFS adatok alapján.
 */
class Dk4Activity : AppCompatActivity(), OnMapReadyCallback {

    // Google Maps objektum, ha a térkép már inicializálódott
    private var googleMap: GoogleMap? = null

    // BKK API kulcs BkkApi-ból – itt nem tárolunk külön stringet
    private val apiKey = BkkApi.API_KEY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dk4)

        // Térképtöredék inicializálása
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        val inputField = findViewById<EditText>(R.id.vehicle_input)
        val searchButton = findViewById<Button>(R.id.search_button)

        // Keresés gomb – pályaszám alapján jármű pozíció lekérdezése
        searchButton.setOnClickListener {
            val vehicleLabel = inputField.text.toString().trim()
            if (vehicleLabel.isNotEmpty()) {
                fetchVehicleLocation(vehicleLabel)
            } else {
                Toast.makeText(this, "Add meg a pályaszámot!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * A térkép betöltésekor hívódik meg – alapnézet beállítása.
     */
    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        val budapest = LatLng(47.4979, 19.0402)
        googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(budapest, 12f))
    }

    /**
     * FUTÁR API hívás: vehicles-for-location
     *
     * A megadott pályaszámra (vehicleLabel) lekéri az első találat jármű pozícióját
     * és a hozzá tartozó tripId-t, majd kirajzolja a térképre.
     */
    private fun fetchVehicleLocation(vehicleLabel: String) {
        val url = "https://futar.bkk.hu/api/query/v1/ws/otp/api/where/vehicles-for-location" +
                "?key=$apiKey&version=2&appVersion=1.0&query=$vehicleLabel&includeReferences=true"

        val request = Request.Builder().url(url).build()
        val client = OkHttpClient()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@Dk4Activity, "Hálózati hiba!", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { body ->
                    val json = JSONObject(body)
                    val data = json.getJSONObject("data")
                    val vehicles = data.getJSONArray("list")
                    val refs = data.optJSONObject("references")

                    if (vehicles.length() > 0) {
                        val vehicle = vehicles.getJSONObject(0)
                        val loc = vehicle.optJSONObject("location")
                        if (loc != null) {
                            // Jármű aktuális pozíciója
                            val lat = loc.optDouble("lat")
                            val lon = loc.optDouble("lon")
                            val tripId = vehicle.optString("tripId")

                            // Trip fejléce (irány / viszonylatvégi megnevezés)
                            var headsign = ""
                            refs?.optJSONObject("trips")?.optJSONObject(tripId)?.let { tripObj ->
                                headsign = tripObj.optString("tripHeadsign", "")
                            }

                            // Marker frissítése a térképen a jármű pozíciójával
                            runOnUiThread {
                                updateMap(LatLng(lat, lon), headsign)
                            }

                            // Ha van tripId, akkor a megállókat is kirajzoljuk
                            if (tripId.isNotBlank()) {
                                showTripStops(tripId)
                            }
                        } else {
                            runOnUiThread {
                                Toast.makeText(this@Dk4Activity, "Nincs helyadat a járműhöz.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        runOnUiThread {
                            Toast.makeText(this@Dk4Activity, "Nincs találat.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        })
    }

    /**
     * Térképen a jármű markerének frissítése.
     *
     * - törli a korábbi markereket
     * - elhelyez egy sárga villamos ikont a jármű pozícióján
     * - ránagyít az adott pontra
     */
    private fun updateMap(position: LatLng, headsign: String) {
        googleMap?.clear()
        val title = if (headsign.isNotBlank()) {
            "➡️ $headsign"
        } else {
            "➡️ Ismeretlen"
        }
        googleMap?.addMarker(
            MarkerOptions()
                .position(position)
                .title(title)
                .icon(resizeIcon(R.drawable.ic_tram, 96, 96)) // sárga villamos marker
        )
        googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(position, 15f))
    }

    /**
     * A triphez tartozó megállók kirajzolása a térképre.
     *
     * - GTFS-ből betölti a stops.txt-t (ha elérhető)
     * - FUTÁR trip-details segítségével lekéri a stopTimes listát
     * - minden megállóhoz elhelyez egy marker-t, amin a várható / menetrendi érkezés is látszik
     */
    private fun showTripStops(tripId: String) {
        lifecycleScope.launch {
            val gtfsDir = File(filesDir, "gtfs")
            val stopsFile = File(gtfsDir, "stops.txt")
            if (!stopsFile.exists()) {
                // Ha nincs GTFS letöltve, a felhasználónak jelezzük
                runOnUiThread {
                    Toast.makeText(
                        this@Dk4Activity,
                        "GTFS fájlok hiányoznak, frissítsd először!",
                        Toast.LENGTH_LONG
                    ).show()
                }
                return@launch
            }

            // Trip megállóinak lekérése FUTÁR API-ból
            val stopTimes = withContext(Dispatchers.IO) { BkkApi.getTripDetails(tripId) }
            // Megállók koordinátáinak betöltése GTFS-ből
            val stops = withContext(Dispatchers.IO) { BkkApi.loadStops(stopsFile) }

            stopTimes.forEach { st ->
                val stopId = st.optString("stopId").removePrefix("BKK_")
                val stopInfo = stops.firstOrNull { it.stopId == stopId }
                stopInfo?.let { stop ->
                    val scheduledRaw = st.optLong("arrivalTime", 0L).takeIf { it > 0L }
                    val predictedRaw = st.optLong("predictedArrivalTime", 0L).takeIf { it > 0L }
                    val serviceDateMs = st.optLong("serviceDate", 0L).takeIf { it > 0L }

                    // Menetrendi vs. várható érkezés eldöntése
                    val arrivalText = when {
                        predictedRaw != null -> "Várható érkezés: ${epochToHms(predictedRaw, serviceDateMs)}"
                        scheduledRaw != null -> "Menetrendi érkezés: ${epochToHms(scheduledRaw, serviceDateMs)}"
                        else -> "Nincs adat"
                    }

                    // Megálló kirajzolása kék ikonnal és érkezési idővel
                    runOnUiThread {
                        googleMap?.addMarker(
                            MarkerOptions()
                                .position(LatLng(stop.lat, stop.lon))
                                .title("${stop.stopName} - $arrivalText")
                                .icon(resizeIcon(R.drawable.ic_stop, 64, 64)) // kék megálló marker
                        )
                    }
                }
            }
        }
    }

    /**
     * Epoch idő konvertálása HH:mm:ss formátumra, budapesti időzónával.
     *
     * A FUTÁR API különböző formátumokat használhat:
     * - teljes millis (>= 10_000_000_000)
     * - epoch másodperc (~ 1_000_000_000)
     * - serviceDate + offset (másodperc a nap elejétől)
     */
    private fun epochToHms(raw: Long, serviceDateMs: Long? = null): String {
        if (raw <= 0L) return "N/A"
        val millis = when {
            // Már millis (pl. FUTÁR egyes mezői)
            raw >= 10_000_000_000L -> raw
            // Klasszikus epoch másodperc
            raw >= 1_000_000_000L -> raw * 1000L
            // serviceDate + offset (másodpercben, max 2 nap)
            serviceDateMs != null && raw in 1..172_800L -> serviceDateMs + (raw * 1000L)
            // Biztonsági default: másodpercből millis
            else -> raw * 1000L
        }

        val sdf = SimpleDateFormat("HH:mm:ss", Locale("hu", "HU"))
        sdf.timeZone = TimeZone.getTimeZone("Europe/Budapest")
        return try {
            sdf.format(Date(millis))
        } catch (_: Exception) {
            raw.toString()
        }
    }

    /**
     * Egyszerű helper, ami egy drawabléből átméretezett BitmapDescriptor-t készít
     * (pl. villamos / megálló ikon markerhez).
     */
    private fun resizeIcon(resId: Int, width: Int, height: Int): BitmapDescriptor {
        val bitmap = BitmapFactory.decodeResource(resources, resId)
        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, false)
        return BitmapDescriptorFactory.fromBitmap(scaled)
    }
}
