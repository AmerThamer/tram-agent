package com.amerthamer.tramagent.domain

import com.amerthamer.tramagent.data.BkkApi
import org.json.JSONObject
import java.net.URLEncoder

/**
 * FUTÁR valós idejű lekérdezések strukturált eredménnyel (DK1, DK4, DK5 közösen használja).
 * Hiba esetén BkkApi.ApiException-t dob.
 */
object Futar {

    data class Vehicle(
        val vehicleId: String,
        /** Pályaszám (a FUTÁR-ban licensePlate, villamosnál pl. „1342”). */
        val licensePlate: String?,
        val routeId: String,
        val tripId: String,
        val lat: Double?,
        val lon: Double?,
        /** A következő / aktuális megálló, ill. „STOPPED_AT” / „IN_TRANSIT_TO”. */
        val stopId: String?,
        val status: String?,
    ) {
        val line: String get() = TramLines.nameFor(routeId) ?: routeId.removePrefix("BKK_")
        val stoppedAtStop: Boolean get() = status == "STOPPED_AT"
    }

    data class TripStop(
        val stopId: String,
        val name: String,
        val lat: Double?,
        val lon: Double?,
        /** Menetrendi / becsült érkezés epoch másodpercben. */
        val scheduled: Long?,
        val predicted: Long?,
        /** Eltérés másodpercben (negatív = siet, pozitív = késik). */
        val delaySec: Int?,
    ) {
        val bestTime: Long? get() = predicted ?: scheduled
    }

    data class TripDetails(
        val tripId: String,
        val routeId: String,
        val line: String,
        val headsign: String,
        val stops: List<TripStop>,
    )

    private fun JSONObject.optStringOrNull(key: String): String? =
        optString(key, "").takeIf { it.isNotBlank() && it != "null" }

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (has(key) && !isNull(key)) optLong(key).takeIf { it > 0 } else null

    fun parseVehicle(v: JSONObject): Vehicle {
        val loc = v.optJSONObject("location")
        return Vehicle(
            vehicleId = v.optString("vehicleId"),
            licensePlate = v.optStringOrNull("licensePlate") ?: v.optStringOrNull("label"),
            routeId = v.optString("routeId"),
            tripId = v.optString("tripId"),
            lat = loc?.optDouble("lat")?.takeUnless { it.isNaN() },
            lon = loc?.optDouble("lon")?.takeUnless { it.isNaN() },
            stopId = v.optStringOrNull("stopId"),
            status = v.optStringOrNull("status"),
        )
    }

    /** Jármű keresése pályaszám (vagy annak részlete) alapján; pontos egyezést előnyben részesít. */
    suspend fun findVehicle(query: String): Vehicle? {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val data = BkkApi.getJson(
            "${BkkApi.FUTAR_BASE}/vehicles-for-location?version=2&appVersion=1.0&includeReferences=true&query=$q"
        ).optJSONObject("data") ?: return null
        val list = data.optJSONArray("list") ?: return null
        val vehicles = List(list.length()) { parseVehicle(list.getJSONObject(it)) }
        return vehicles.firstOrNull { it.licensePlate.equals(query.trim(), ignoreCase = true) } ?: vehicles.firstOrNull()
    }

    /** Egy viszonylat összes járműve + a references blokk (trips, blockId-k). */
    suspend fun vehiclesForRoute(routeId: String): Pair<List<Vehicle>, JSONObject> {
        val data = BkkApi.getJson(
            "${BkkApi.GO_BASE}/vehicles-for-route?routeId=$routeId&version=2&dialect=otp&includeReferences=true"
        ).optJSONObject("data") ?: return emptyList<Vehicle>() to JSONObject()
        val list = data.optJSONArray("list")
        val vehicles = if (list == null) emptyList() else List(list.length()) { parseVehicle(list.getJSONObject(it)) }
        return vehicles to (data.optJSONObject("references") ?: JSONObject())
    }

    /** Egy trip megállói menetrendi és becsült időkkel, a references-ből vett nevekkel és koordinátákkal. */
    suspend fun tripDetails(tripId: String): TripDetails {
        val data = BkkApi.getJson(
            "${BkkApi.FUTAR_BASE}/trip-details?version=2&appVersion=1.0&includeReferences=true&tripId=$tripId"
        ).optJSONObject("data") ?: throw BkkApi.ApiException("Üres válasz a trip-details végponttól")

        val entry = data.optJSONObject("entry") ?: JSONObject()
        val refs = data.optJSONObject("references") ?: JSONObject()
        val stopRefs = refs.optJSONObject("stops") ?: JSONObject()
        val tripRef = refs.optJSONObject("trips")?.optJSONObject(tripId)
        val routeId = tripRef?.optString("routeId").orEmpty()
        val routeRef = refs.optJSONObject("routes")?.optJSONObject(routeId)

        val stopTimes = entry.optJSONArray("stopTimes")
        val stops = if (stopTimes == null) emptyList() else List(stopTimes.length()) { i ->
            val st = stopTimes.getJSONObject(i)
            val stopId = st.optString("stopId")
            val ref = stopRefs.optJSONObject(stopId)
            val scheduled = st.optLongOrNull("arrivalTime") ?: st.optLongOrNull("departureTime")
            val predicted = st.optLongOrNull("predictedArrivalTime") ?: st.optLongOrNull("predictedDepartureTime")
            val apiDelay = if (st.has("arrivalDelay") && !st.isNull("arrivalDelay")) st.optInt("arrivalDelay") else null
            TripStop(
                stopId = stopId,
                name = ref?.optStringOrNull("name") ?: stopId.removePrefix("BKK_"),
                lat = ref?.optDouble("lat")?.takeUnless { it.isNaN() },
                lon = ref?.optDouble("lon")?.takeUnless { it.isNaN() },
                scheduled = scheduled,
                predicted = predicted,
                delaySec = apiDelay ?: if (scheduled != null && predicted != null) (predicted - scheduled).toInt() else null,
            )
        }

        return TripDetails(
            tripId = tripId,
            routeId = routeId,
            line = routeRef?.optStringOrNull("shortName") ?: TramLines.nameFor(routeId) ?: routeId.removePrefix("BKK_"),
            headsign = tripRef?.optStringOrNull("tripHeadsign") ?: stops.lastOrNull()?.name.orEmpty(),
            stops = stops,
        )
    }

    /**
     * A jármű helyzete a trip megállói között: a következő (vagy éppen kiszolgált) megálló indexe.
     * Ha a jármű megadja a stopId-t, azt használjuk; különben az első, még el nem múlt időpontot.
     */
    fun nextStopIndex(trip: TripDetails, vehicle: Vehicle?, nowSec: Long): Int {
        vehicle?.stopId?.let { id ->
            val i = trip.stops.indexOfFirst { it.stopId == id }
            if (i >= 0) return i
        }
        val i = trip.stops.indexOfFirst { (it.bestTime ?: 0) >= nowSec }
        return if (i >= 0) i else trip.stops.lastIndex
    }

    /** Késés-kategória a FUTÁR fedélzeti kijelzőjének mintájára. */
    enum class DelayClass { Early, OnTime, Late }

    fun classify(delaySec: Int): DelayClass = when {
        delaySec <= -31 -> DelayClass.Early
        delaySec <= 180 -> DelayClass.OnTime
        else -> DelayClass.Late
    }

    /** „±M:SS” formátum. */
    fun formatDelay(seconds: Int): String {
        val sign = if (seconds >= 0) "+" else "-"
        val s = kotlin.math.abs(seconds)
        return "$sign${s / 60}:${"%02d".format(s % 60)}"
    }
}
