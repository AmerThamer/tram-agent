package com.amerthamer.tramagent.domain

import com.amerthamer.tramagent.data.BkkApi

/**
 * DK5 – Nyomkövetés: egy jármű (pályaszám alapján) aktuális menete megállónként,
 * menetrendi és becsült időkkel, késéssel. A DK4 térkép is ezt használja.
 *
 * A becsült idők az aktuális pontig a valós menetet, utána a számolt késés/sietés
 * kivetítését mutatják (a FUTÁR predikciója alapján).
 */
object Dk5 {

    data class Tracking(
        val vehicle: Futar.Vehicle,
        val trip: Futar.TripDetails,
        /** A következő (vagy éppen kiszolgált) megálló indexe a trip.stops-ban. */
        val nextIndex: Int,
        val fetchedAtSec: Long,
    ) {
        /** Az aktuális késés: a következő megálló eltérése, ha nincs, az előzőé. */
        val currentDelaySec: Int?
            get() = trip.stops.getOrNull(nextIndex)?.delaySec ?: trip.stops.getOrNull(nextIndex - 1)?.delaySec
    }

    /** @return null, ha nincs ilyen pályaszámú aktív jármű. */
    suspend fun track(vehicleLabel: String): Tracking? {
        val vehicle = Futar.findVehicle(vehicleLabel) ?: return null
        if (vehicle.tripId.isBlank()) throw BkkApi.ApiException("A jármű jelenleg nem teljesít menetet.")
        val trip = Futar.tripDetails(vehicle.tripId)
        if (trip.stops.isEmpty()) throw BkkApi.ApiException("Ehhez a menethez nincs megállólista.")
        val now = System.currentTimeMillis() / 1000
        return Tracking(vehicle, trip, Futar.nextStopIndex(trip, vehicle, now), now)
    }
}
