package com.amerthamer.tramagent.domain

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * DK1 – Pályaszám kereső: a kiválasztott viszonylatokon közlekedő járművek
 * forgalmi számmal (blockId-ból) és pályaszámmal.
 */
object Dk1 {

    data class LineVehicle(
        val vehicle: Futar.Vehicle,
        /** Forgalmi szám, a blockId utolsó előtti tagja (pl. BKK_..._12_1 → „12”). */
        val blockNo: String?,
        val headsign: String?,
    )

    data class LineResult(
        val line: String,
        val routeId: String,
        val vehicles: List<LineVehicle>,
        val error: String? = null,
    )

    /** Viszonylatonként párhuzamosan kérdez le; egy vonal hibája nem rontja el a többit. */
    suspend fun query(routeIds: List<String>): List<LineResult> = coroutineScope {
        routeIds.distinct().map { routeId -> async { queryOne(routeId) } }.awaitAll()
    }

    private suspend fun queryOne(routeId: String): LineResult {
        val line = TramLines.nameFor(routeId) ?: routeId.removePrefix("BKK_")
        return try {
            val (vehicles, refs) = Futar.vehiclesForRoute(routeId)
            val trips = refs.optJSONObject("trips")
            val rows = vehicles.map { v ->
                val trip = trips?.optJSONObject(v.tripId)
                val parts = trip?.optString("blockId").orEmpty().split("_")
                LineVehicle(
                    vehicle = v,
                    blockNo = if (parts.size >= 3) parts[parts.size - 2] else null,
                    headsign = trip?.optString("tripHeadsign")?.takeIf { it.isNotBlank() },
                )
            }.sortedWith(compareBy({ it.blockNo?.toIntOrNull() ?: Int.MAX_VALUE }, { it.blockNo }))
            LineResult(line, routeId, rows)
        } catch (e: Exception) {
            LineResult(line, routeId, emptyList(), e.message ?: "Hálózati hiba")
        }
    }
}
