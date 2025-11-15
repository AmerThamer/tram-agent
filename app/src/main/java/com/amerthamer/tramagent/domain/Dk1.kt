package com.amerthamer.tramagent.domain

import com.amerthamer.tramagent.data.BkkApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

object Dk1 {
    suspend fun run(routeId: String): String = withContext(Dispatchers.Default) {
        val (vehicles, refs) = BkkApi.getVehiclesForRoute(routeId)
        if (vehicles.isEmpty()) return@withContext "Nincs jármű a megadott viszonylathoz: $routeId"

        val trips = refs?.optJSONObject("trips") ?: JSONObject()
        val lines = vehicles.map { v ->
            val tripId = v.optString("tripId", "")
            val blockId = trips.optJSONObject(tripId)?.optString("blockId", "") ?: ""
            val parts = blockId.split("_")
            val forgalmi = if (parts.size >= 3) parts[parts.size - 2] else "Ismeretlen"
            val rendszam = v.optString("licensePlate", "Nincs rendszám")
            "🚌 Forgalmi szám: $forgalmi | Rendszám: $rendszam"
        }
        lines.joinToString("\n")
    }
}
