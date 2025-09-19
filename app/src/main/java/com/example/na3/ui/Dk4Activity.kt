package com.example.na3.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.na3.R
import com.example.na3.data.BkkApi
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

class Dk4Activity : AppCompatActivity(), OnMapReadyCallback {
    private var googleMap: GoogleMap? = null
    private val apiKey = BkkApi.API_KEY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dk4)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        val inputField = findViewById<EditText>(R.id.vehicle_input)
        val searchButton = findViewById<Button>(R.id.search_button)

        searchButton.setOnClickListener {
            val vehicleLabel = inputField.text.toString().trim()
            if (vehicleLabel.isNotEmpty()) {
                fetchVehicleLocation(vehicleLabel)
            } else {
                Toast.makeText(this, "Add meg a pályaszámot!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        val budapest = LatLng(47.4979, 19.0402)
        googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(budapest, 12f))
    }

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
                    val vehicles = json.getJSONObject("data").getJSONArray("list")
                    if (vehicles.length() > 0) {
                        val vehicle = vehicles.getJSONObject(0)
                        val loc = vehicle.optJSONObject("location")
                        if (loc != null) {
                            val lat = loc.optDouble("lat")
                            val lon = loc.optDouble("lon")
                            val tripId = vehicle.optString("tripId")

                            runOnUiThread {
                                updateMap(
                                    LatLng(lat, lon),
                                    vehicle.optString("label", vehicleLabel)
                                )
                            }

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

    private fun updateMap(position: LatLng, vehicleLabel: String) {
        googleMap?.clear()
        googleMap?.addMarker(
            MarkerOptions()
                .position(position)
                .title("Pályaszám: $vehicleLabel")
                .icon(resizeIcon(R.drawable.ic_tram, 96, 96)) // sárga villamos marker
        )
        googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(position, 15f))
    }

    private fun showTripStops(tripId: String) {
        lifecycleScope.launch {
            val gtfsDir = File(filesDir, "gtfs")
            val stopsFile = File(gtfsDir, "stops.txt")
            if (!stopsFile.exists()) {
                runOnUiThread {
                    Toast.makeText(
                        this@Dk4Activity,
                        "GTFS fájlok hiányoznak, frissítsd először!",
                        Toast.LENGTH_LONG
                    ).show()
                }
                return@launch
            }

            val stopTimes = withContext(Dispatchers.IO) { BkkApi.getTripDetails(tripId) }
            val stops = withContext(Dispatchers.IO) { BkkApi.loadStops(stopsFile) }

            stopTimes.forEach { st ->
                val stopId = st.optString("stopId").removePrefix("BKK_")
                val stopInfo = stops.firstOrNull { it.stopId == stopId }
                stopInfo?.let { stop ->
                    // LONG-ként olvasunk minden időt
                    val scheduledRaw = st.optLong("arrivalTime", 0L).takeIf { it > 0L }
                    val predictedRaw = st.optLong("predictedArrivalTime", 0L).takeIf { it > 0L }
                    // (opcionális) ha a szerver ad 'serviceDate'-et (ms), később bővíthető vele:
                    val serviceDateMs = st.optLong("serviceDate", 0L).takeIf { it > 0L }

                    val arrivalText = when {
                        predictedRaw != null -> "Várható érkezés: ${epochToHms(predictedRaw, serviceDateMs)}"
                        scheduledRaw != null -> "Menetrendi érkezés: ${epochToHms(scheduledRaw, serviceDateMs)}"
                        else -> "Nincs adat"
                    }

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
     * Rugalmas konvertáló:
     * - ha 'raw' 10 számjegy körüli -> másodperc; 13 számjegy körüli -> millis
     * - mindig Europe/Budapest időzónában jelenít
     * - ha a backend valaha serviceDate+offset formátumot adna, a serviceDateMs felhasználható
     */
    private fun epochToHms(raw: Long, serviceDateMs: Long? = null): String {
        if (raw <= 0L) return "N/A"

        // 1) másodperc vs. millis felismerése
        val millis = when {
            raw >= 10_000_000_000L -> raw                          // >= ~Sat Nov 20 2286 (ms) -> biztos millis
            raw >= 1_000_000_000L -> raw * 1000L                   // ~2001..~2286 (sec) -> szekundum
            // ritka eset: ha 0..86400 közti érték jön (másodperc a nap elejétől):
            serviceDateMs != null && raw in 1..172_800L -> serviceDateMs + (raw * 1000L)
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

    private fun resizeIcon(resId: Int, width: Int, height: Int): BitmapDescriptor {
        val bitmap = BitmapFactory.decodeResource(resources, resId)
        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, false)
        return BitmapDescriptorFactory.fromBitmap(scaled)
    }
}
