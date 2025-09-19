package com.example.na3.ui

import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.na3.databinding.ActivityDk1Binding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import android.app.AlertDialog

class Dk1Activity : AppCompatActivity() {
    private lateinit var binding: ActivityDk1Binding
    private val client = OkHttpClient()

    private val tramOptions = arrayOf(
        "1 Villamos" to "BKK_3010",
        "1A Villamos" to "BKK_3011",
        "2 Villamos" to "BKK_3020",
        "2B Villamos" to "BKK_3022",
        "3 Villamos" to "BKK_3030",
        "4 Villamos" to "BKK_3040",
        "6 Villamos" to "BKK_3060",
        "12 Villamos" to "BKK_3120",
        "14 Villamos" to "BKK_3140",
        "17 Villamos" to "BKK_3170",
        "19 Villamos" to "BKK_3190",
        "23 Villamos" to "BKK_3230",
        "24 Villamos" to "BKK_3240",
        "28 Villamos" to "BKK_3280",
        "28A Villamos" to "BKK_3281",
        "37 Villamos" to "BKK_3370",
        "37A Villamos" to "BKK_3371",
        "37E Villamos" to "BKK_3375",
        "41 Villamos" to "BKK_3410",
        "42 Villamos" to "BKK_3420",
        "47 Villamos" to "BKK_3470",
        "48 Villamos" to "BKK_3480",
        "49 Villamos" to "BKK_3490",
        "50 Villamos" to "BKK_3500",
        "51 Villamos" to "BKK_3510",
        "51A Villamos" to "BKK_3511",
        "52 Villamos" to "BKK_3520",
        "56 Villamos" to "BKK_3560",
        "56A Villamos" to "BKK_3561",
        "59 Villamos" to "BKK_3590",
        "59A Villamos" to "BKK_3591",
        "59B Villamos" to "BKK_3592",
        "60 Villamos" to "BKK_3600",
        "61 Villamos" to "BKK_3610",
        "61B Villamos" to "BKK_3612",
        "62 Villamos" to "BKK_3620",
        "62A Villamos" to "BKK_3621",
        "69 Villamos" to "BKK_3690"
    )

    private val selectedRoutes = mutableListOf<String>()
    private lateinit var prefs: SharedPreferences

    companion object {
        private const val API_KEY = "2cde79f6-2ac0-4c42-88a3-9e15208ee44e"
        private const val BASE_URL = "https://go.bkk.hu/api/query/v1/ws/otp/api/where"
        private const val PREFS_NAME = "dk1_prefs"
        private const val KEY_SELECTED = "selected_routes"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDk1Binding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        // Mentett választások visszatöltése
        val saved = prefs.getStringSet(KEY_SELECTED, emptySet()) ?: emptySet()
        selectedRoutes.clear()
        selectedRoutes.addAll(saved)

        binding.multiSelectButton.setOnClickListener {
            showMultiSelectDialog()
        }

        binding.queryButton.setOnClickListener {
            val manualInput = binding.queryInput.text.toString().trim()
            val allRoutes = selectedRoutes.toMutableList()
            if (manualInput.isNotEmpty()) allRoutes.add(manualInput)

            if (allRoutes.isEmpty()) {
                binding.result.text = "Válassz ki legalább egy viszonylatot!"
                return@setOnClickListener
            }

            binding.result.text = "Lekérdezés folyamatban..."
            lifecycleScope.launch(Dispatchers.IO) {
                val results = allRoutes.map { runDK1(it) }
                val output = results.joinToString("\n\n---\n\n")
                runOnUiThread { binding.result.text = output }
            }
        }
    }

    private fun showMultiSelectDialog() {
        val names = tramOptions.map { it.first }.toTypedArray()
        val checked = BooleanArray(names.size) { i ->
            tramOptions[i].second in selectedRoutes
        }

        AlertDialog.Builder(this)
            .setTitle("Válassz villamosvonalakat")
            .setMultiChoiceItems(names, checked) { _, which, isChecked ->
                val id = tramOptions[which].second
                if (isChecked) {
                    if (!selectedRoutes.contains(id)) selectedRoutes.add(id)
                } else {
                    selectedRoutes.remove(id)
                }
            }
            .setPositiveButton("OK") { _, _ ->
                // Mentés SharedPreferences-be
                prefs.edit().putStringSet(KEY_SELECTED, selectedRoutes.toSet()).apply()
            }
            .show()
    }

    private fun runDK1(routeId: String): String {
        val url = "$BASE_URL/vehicles-for-route?routeId=$routeId&key=$API_KEY&version=2&dialect=otp&includeReferences=true"
        val req = Request.Builder().url(url).get().build()

        return try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return "❌ Hiba történt: HTTP ${resp.code}"

                val jsonStr = resp.body?.string() ?: return "❌ Üres válasz az API-tól"
                val dataObj = JSONObject(jsonStr).optJSONObject("data") ?: return "❌ Nincs 'data' az API válaszban"

                val vehicles = dataObj.optJSONArray("list")
                val references = dataObj.optJSONObject("references") ?: JSONObject()
                val trips = references.optJSONObject("trips") ?: JSONObject()

                if (vehicles == null || vehicles.length() == 0) {
                    return "Nincs jármű a megadott viszonylathoz: $routeId"
                }

                val routeName = tramOptions.find { it.second == routeId }
                    ?.first
                    ?.substringBefore(" ")
                    ?: routeId

                val lines = mutableListOf<String>()
                for (i in 0 until vehicles.length()) {
                    val vehicle = vehicles.getJSONObject(i)
                    val tripId = vehicle.optString("tripId")
                    val trip = trips.optJSONObject(tripId) ?: JSONObject()
                    val blockId = trip.optString("blockId")
                    val parts = blockId.split("_")
                    val forgalmiSzam = if (parts.size >= 3) parts[parts.size - 2] else "Ismeretlen"
                    val rendszam = vehicle.optString("licensePlate", "Nincs rendszám")
                    lines.add("🚋 $routeName | 🚌 Forgalmi szám: $forgalmiSzam | Pályaszám: $rendszam")
                }

                lines.joinToString("\n")
            }
        } catch (e: Exception) {
            "❌ Hiba történt: ${e.message}"
        }
    }

    override fun onPause() {
        super.onPause()
        // Kimeneti szöveg és manuális input törlése
        binding.result.text = ""
        binding.queryInput.text.clear()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Checkbox választások törlése, ha kilépünk az Activity-ből
        prefs.edit().remove(KEY_SELECTED).apply()
        selectedRoutes.clear()
    }
}
