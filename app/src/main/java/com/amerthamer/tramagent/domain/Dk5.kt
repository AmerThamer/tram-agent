package com.amerthamer.tramagent.domain

import com.amerthamer.tramagent.data.BkkApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import android.text.SpannableString
import android.text.Spannable
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan

object Dk5 {

    private fun formatDelay(seconds: Int): String {
        val sign = if (seconds >= 0) "+" else "-"
        val s = kotlin.math.abs(seconds)
        val m = s / 60
        val sec = s % 60
        return "$sign$m:${"%02d".format(sec)}"
    }

    private fun styledDelay(sec: Int): SpannableString {
        val txt = formatDelay(sec)
        val spannable = SpannableString(txt)

        when {
            sec <= -31 -> {
                spannable.setSpan(
                    BackgroundColorSpan(0xFFFF0000.toInt()), // piros háttér
                    0, txt.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannable.setSpan(
                    ForegroundColorSpan(0xFFFFFFFF.toInt()), // fehér szöveg
                    0, txt.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            sec in -30..180 -> {
                spannable.setSpan(
                    BackgroundColorSpan(0xFF00FF00.toInt()), // zöld háttér
                    0, txt.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannable.setSpan(
                    ForegroundColorSpan(0xFF000000.toInt()), // fekete szöveg
                    0, txt.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            sec >= 181 -> {
                spannable.setSpan(
                    BackgroundColorSpan(0xFF0000FF.toInt()), // kék háttér
                    0, txt.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannable.setSpan(
                    ForegroundColorSpan(0xFFFF0000.toInt()), // piros szöveg
                    0, txt.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        return spannable
    }

    private fun epochToHms(epoch: Int?): String {
        return try {
            if (epoch == null) return "N/A"
            val sdf = SimpleDateFormat("HH:mm:ss", Locale("hu", "HU"))
            sdf.format(Date(epoch * 1000L))
        } catch (_: Exception) {
            epoch?.toString() ?: "N/A"
        }
    }

    suspend fun run(vehicleLabel: String, gtfsDir: File): CharSequence = withContext(Dispatchers.Default) {
        val vehicle = BkkApi.getVehicleForQuery(vehicleLabel)
            ?: return@withContext "A jármű nem található vagy nem aktív."

        val routeId = vehicle.optString("routeId")
        val tripId = vehicle.optString("tripId")

        val header = StringBuilder().apply {
            appendLine(" --- NYOMKÖVETÉS ---")
            appendLine("🛤 RouteID: $routeId")
            appendLine("🚋 TripID: $tripId")
        }

        if (tripId.isBlank()) {
            return@withContext header.toString() + "\nNincs elérhető menetrendi adat (tripId hiányzik)."
        }

        val stopTimes = BkkApi.getTripDetails(tripId)
        if (stopTimes.isEmpty()) return@withContext "\nNincs elérhető valós idejű megállólista ehhez a tripId-hez."

        val stopsFile = File(gtfsDir, "stops.txt")
        val stops = BkkApi.loadStops(stopsFile)

        val result = android.text.SpannableStringBuilder()
        result.append(header.toString())
        result.append("\n== 🚏 Várható megállóhelyi indulások ==\n")

        for (st in stopTimes) {
            val stopId = st.optString("stopId").removePrefix("BKK_")
            val stopInfo = stops.firstOrNull { it.stopId == stopId }
            val stopName = stopInfo?.stopName ?: "ismeretlen megálló"

            val scheduled = st.optInt("arrivalTime", Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }
            val predicted = st.optInt("predictedArrivalTime", Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }
            val delayApi = st.optInt("arrivalDelay", Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }

            result.append("🚏 Megálló: $stopName\n")
            result.append("    Menetrendi érkezés: ${scheduled?.let { epochToHms(it) } ?: "N/A"}\n")
            result.append("    Becsült érkezés: ${predicted?.let { epochToHms(it) } ?: "N/A"}\n")

            val delaySpan = when {
                delayApi != null -> styledDelay(delayApi)
                scheduled != null && predicted != null -> styledDelay(predicted - scheduled)
                else -> SpannableString("N/A")
            }
            result.append("   ⏱ Eltérés: ").append(delaySpan).append("\n")
        }

        result
    }
}
