// app/src/main/java/com/example/na3/domain/Dk3a.kt
package com.amerthamer.tramagent.domain

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object Dk3a {

    private const val BASE =
        "https://futar.bkk.hu/api/query/v1/ws/otp/api/where"

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
        }
        conn.inputStream.bufferedReader().use { br ->
            val txt = br.readText()
            conn.disconnect()
            return txt
        }
    }

    private fun epochSecToClock(sec: Long): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(Date(sec * 1000))
    }

    /**
     * Közeli megállók és indulások (következő 60 perc) viszonylattal.
     * Visszatérés: formázott sorok listája, amit közvetlenül ki lehet írni.
     */
    suspend fun findNearbyDepartures(
        context: Context,
        lat: Double,
        lon: Double,
        radius: Int = 250,
        maxStops: Int = 6,
        minutesAfter: Int = 60
    ): List<String> {
        val out = mutableListOf<String>()


        val stopsUrl =
            "$BASE/stops-for-location.json?lat=$lat&lon=$lon&radius=$radius&query=&maxCount=$maxStops&version=4"
        val stopsJson = JSONObject(httpGet(stopsUrl))
        val stopsData = stopsJson.getJSONObject("data")
        val stopsRefs = stopsData.getJSONObject("references")
        val stopsArray = stopsData.getJSONArray("list")

        if (stopsArray.length() == 0) {
            return listOf("Nincs megálló a közelben.")
        }

        for (i in 0 until stopsArray.length()) {
            val stop = stopsArray.getJSONObject(i)
            val stopId = stop.getString("id")
            val stopName = stop.optString("name", stop.optString("shortName", "Ismeretlen megálló"))
            out += "📍 $stopName"


            val depUrl =
                "$BASE/arrivals-and-departures-for-stop.json?stopId=$stopId&minutesBefore=0&minutesAfter=$minutesAfter&includeReferences=true&version=4"
            val depJson = JSONObject(httpGet(depUrl)).getJSONObject("data")

            val refs = depJson.getJSONObject("references")
            val routes = refs.optJSONObject("routes") ?: JSONObject()
            val trips = refs.optJSONObject("trips") ?: JSONObject()

            val entries = depJson.getJSONObject("entry")
                .getJSONArray("arrivalsAndDepartures")

            if (entries.length() == 0) {
                out += "ℹ️ Nincs indulás $minutesAfter percen belül."
                continue
            }

            for (j in 0 until entries.length()) {
                val e = entries.getJSONObject(j)
                val rt = e.optLong("predictedDepartureTime", 0L)
                val sked = e.optLong("scheduledDepartureTime", 0L)
                val whenSec = if (rt > 0) rt else sked

                val routeId = e.optString("routeId", "")
                val tripId = e.optString("tripId", "")

                val routeObj = routes.optJSONObject(routeId)
                val routeShort = routeObj?.optString("shortName") ?: routeObj?.optString("longName") ?: "?"
                val headsign = trips.optJSONObject(tripId)?.optString("tripHeadsign")
                    ?: e.optString("headsign", "")

                val clock = if (whenSec > 0) epochSecToClock(whenSec) else "– –:– –"
                out += "⏰ $clock   $routeShort → $headsign"
            }
        }

        return out
    }
}
