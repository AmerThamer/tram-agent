package com.example.na3.domain

import com.google.transit.realtime.GtfsRealtime
import com.example.na3.data.BkkApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.*

object Dk2 {

    private val client = OkHttpClient()

    // leképezés: GTFS routeId -> emberbarát név
    private val tramMap = mapOf(
        "3010" to "1",
        "3011" to "1A",
        "3020" to "2",
        "3022" to "2B",
        "3030" to "3",
        "3040" to "4",
        "3060" to "6",
        "3120" to "12",
        "3140" to "14",
        "3170" to "17",
        "3190" to "19",
        "3230" to "23",
        "3240" to "24",
        "3280" to "28",
        "3281" to "28A",
        "3370" to "37",
        "3371" to "37A",
        "3375" to "37E",
        "3410" to "41",
        "3420" to "42",
        "3470" to "47",
        "3480" to "48",
        "3490" to "49",
        "3500" to "50",
        "3510" to "51",
        "3511" to "51A",
        "3520" to "52",
        "3560" to "56",
        "3561" to "56A",
        "3590" to "59",
        "3591" to "59A",
        "3592" to "59B",
        "3600" to "60",
        "3610" to "61",
        "3612" to "61B",
        "3620" to "62",
        "3621" to "62A",
        "3690" to "69"
    )

    private fun fmt(ts: Long?): String {
        if (ts == null || ts <= 0L) return "Ismeretlen"
        val sdf = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale("hu", "HU"))
        sdf.timeZone = TimeZone.getTimeZone("Europe/Budapest")
        return sdf.format(Date(ts * 1000))
    }

    private fun trans(ts: GtfsRealtime.TranslatedString?): String {
        if (ts == null) return ""
        val hu = ts.translationList.firstOrNull {
            it.language.lowercase(Locale.ROOT).startsWith("hu")
        }?.text
        if (!hu.isNullOrBlank()) return hu
        return ts.translationList.firstOrNull()?.text ?: ""
    }

    suspend fun run(): String = withContext(Dispatchers.IO) {
        val url =
            "https://go.bkk.hu/api/query/v1/ws/gtfs-rt/full/Alerts.pb?key=${BkkApi.API_KEY}"
        val req = Request.Builder().url(url).get().build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext "❌ Hiba: HTTP ${resp.code}"

            val feed = try {
                GtfsRealtime.FeedMessage.parseFrom(resp.body!!.bytes())
            } catch (e: Exception) {
                return@withContext "❌ Hiba a feed feldolgozásakor: ${e.message}"
            }

            if (feed.entityCount == 0) return@withContext "✅ Nincs egyetlen zavar sem."

            val blocks = mutableListOf<String>()

            for (entity in feed.entityList) {
                val alert = entity.alert ?: continue

                val ap = alert.activePeriodList.firstOrNull()
                val startTs = ap?.start ?: 0
                val endTs = ap?.end ?: Long.MAX_VALUE

                val header = trans(alert.headerText)
                val desc = trans(alert.descriptionText)

                // nyers értékek a feedből
                val routeIdsRaw = alert.informedEntityList
                    .mapNotNull { it.routeId }
                    .distinct()
                    .sorted()

                // csak villamosok: 3xxx tartomány
                val tramRoutes = routeIdsRaw.filter { it.startsWith("3") }

                if (tramRoutes.isEmpty()) continue

                // emberbarát formátum
                val tramNames = tramRoutes.map { tramMap[it] ?: it }

                val block = buildString {
                    append("<b>=== ALERT ===</b><br>")
                    append("<b>Érintett viszonylatok:</b><br>")
                    append(tramNames.joinToString(", ")) // emberbarát
                    append("<br>")
                    append("<i>(nyers értékek: $tramRoutes)</i><br>") // nyers lista
                    append("<b>Kezdés:</b><br>").append(fmt(startTs)).append("<br>")
                    append("<b>Vége:</b><br>").append(
                        if (endTs != Long.MAX_VALUE) fmt(endTs) else "Ismeretlen"
                    ).append("<br>")
                    append("<b>Összefoglaló:</b><br>")
                    if (header.startsWith("https://")) {
                        append("<a href=\"$header\">$header</a><br>")
                    } else {
                        append(header).append("<br>")
                    }
                    if (desc.isNotBlank()) {
                        append("<b>Leírás:</b><br>").append(desc).append("<br>")
                    }
                }
                blocks += block
            }

            if (blocks.isEmpty()) "✅ Jelenleg nincs villamoszavar."
            else blocks.asReversed().joinToString("<br><br><br>") // fordított sorrend
        }
    }
}
