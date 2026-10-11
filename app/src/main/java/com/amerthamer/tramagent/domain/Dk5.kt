package com.amerthamer.tramagent.domain

import com.amerthamer.tramagent.data.BkkApi
import com.amerthamer.tramagent.gtfs.TramIndex
import java.io.File

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
        /** A menet valós pályája (FUTÁR polyline, vagy a GTFS shapes.txt); üres, ha egyik sem ismert. */
        val path: List<Futar.LatLon> = emptyList(),
    ) {
        /** Az aktuális késés: a következő megálló eltérése, ha nincs, az előzőé. */
        val currentDelaySec: Int?
            get() = trip.stops.getOrNull(nextIndex)?.delaySec ?: trip.stops.getOrNull(nextIndex - 1)?.delaySec
    }

    /**
     * @param gtfsDir a letöltött GTFS mappája – ha a FUTÁR nem ad pályát, innen vesszük a shapes.txt alapján.
     * @return null, ha nincs ilyen pályaszámú aktív jármű.
     */
    suspend fun track(vehicleLabel: String, gtfsDir: File? = null): Tracking? {
        val vehicle = Futar.findVehicle(vehicleLabel) ?: return null
        if (vehicle.tripId.isBlank()) throw BkkApi.ApiException("A jármű jelenleg nem teljesít menetet.")
        var trip = Futar.tripDetails(vehicle.tripId)
        if (trip.stops.isEmpty()) throw BkkApi.ApiException("Ehhez a menethez nincs megállólista.")
        val now = System.currentTimeMillis() / 1000
        val path = trip.path.ifEmpty {
            gtfsDir?.let { TramIndex.shapeForTrip(it, trip.tripId) }
                ?.let { p -> List(p.size / 2) { Futar.LatLon(p[it * 2] / 1e6, p[it * 2 + 1] / 1e6) } }
                .orEmpty()
        }
        if (trip.blockNo == null && gtfsDir != null) {
            TramIndex.blockNoForTrip(gtfsDir, trip.tripId)?.let { trip = trip.copy(blockNo = it) }
        }
        return Tracking(vehicle, trip, Futar.nextStopIndex(trip, vehicle, now), now, path)
    }
}
