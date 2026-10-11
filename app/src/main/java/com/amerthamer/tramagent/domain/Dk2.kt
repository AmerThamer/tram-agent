package com.amerthamer.tramagent.domain

import com.google.transit.realtime.GtfsRealtime
import com.amerthamer.tramagent.data.BkkApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import androidx.core.text.HtmlCompat
import java.util.Locale

object Dk2 {

    private val client = OkHttpClient()


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

    /** Egy villamosokat érintő zavar, megjelenítésre előkészítve. */
    data class TramAlert(
        val id: String,
        /** Emberbarát vonalszámok, pl. ["4", "6"]. */
        val lines: List<String>,
        /** Kezdés / vége epoch másodpercben; null, ha nincs megadva. */
        val start: Long?,
        val end: Long?,
        val header: String,
        val description: String,
        val url: String?,
        val effect: String?,
    ) {
        fun isActive(nowSec: Long): Boolean = (start == null || start <= nowSec) && (end == null || end >= nowSec)
    }

    sealed interface Result {
        data class Success(val alerts: List<TramAlert>) : Result
        data class Failure(val message: String) : Result
    }

    private fun trans(ts: GtfsRealtime.TranslatedString?): String {
        if (ts == null) return ""
        val hu = ts.translationList.firstOrNull {
            it.language.lowercase(Locale.ROOT).startsWith("hu")
        }?.text
        if (!hu.isNullOrBlank()) return hu
        return ts.translationList.firstOrNull()?.text ?: ""
    }

    private fun effectLabel(effect: GtfsRealtime.Alert.Effect): String? = when (effect) {
        GtfsRealtime.Alert.Effect.NO_SERVICE -> "Nem közlekedik"
        GtfsRealtime.Alert.Effect.REDUCED_SERVICE -> "Ritkább járatok"
        GtfsRealtime.Alert.Effect.SIGNIFICANT_DELAYS -> "Jelentős késés"
        GtfsRealtime.Alert.Effect.DETOUR -> "Terelés"
        GtfsRealtime.Alert.Effect.ADDITIONAL_SERVICE -> "Sűrítés"
        GtfsRealtime.Alert.Effect.MODIFIED_SERVICE -> "Módosított forgalom"
        GtfsRealtime.Alert.Effect.STOP_MOVED -> "Megálló áthelyezve"
        else -> null
    }

    /** Vonalszámok sorrendje: előbb a szám, aztán a betű (4 < 4A < 6 < 17). */
    val LineOrder: Comparator<String> = compareBy<String>(
        { it.takeWhile(Char::isDigit).toIntOrNull() ?: Int.MAX_VALUE },
        { it.dropWhile(Char::isDigit) },
    )

    /**
     * GTFS-RT Alerts feed letöltése és a villamosokat érintő zavarok kiszűrése.
     * Sorrend: előbb a folyamatban lévők, aztán a tervezettek, kezdés szerint.
     */
    suspend fun fetchTramAlerts(): Result = withContext(Dispatchers.IO) {
        val url =
            "https://go.bkk.hu/api/query/v1/ws/gtfs-rt/full/Alerts.pb?key=${BkkApi.API_KEY}"
        val req = Request.Builder().url(url).get().build()

        try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val hint = if (resp.code == 401 || resp.code == 403) " – ellenőrizd a BKK API-kulcsot" else ""
                    return@withContext Result.Failure("HTTP ${resp.code}$hint")
                }

                val feed = try {
                    GtfsRealtime.FeedMessage.parseFrom(resp.body!!.bytes())
                } catch (e: Exception) {
                    return@withContext Result.Failure("Hibás adatfolyam: ${e.message}")
                }

                val alerts = feed.entityList.mapNotNull { entity ->
                    if (!entity.hasAlert()) return@mapNotNull null
                    val alert = entity.alert

                    val tramRoutes = alert.informedEntityList
                        .filter { it.hasRouteId() }
                        .map { it.routeId }
                        .filter { it.startsWith("3") }
                        .distinct()
                    if (tramRoutes.isEmpty()) return@mapNotNull null

                    val ap = alert.activePeriodList.firstOrNull()
                    val header = trans(alert.headerText).trim()
                    val link = trans(alert.url).takeIf { it.startsWith("http") }
                        ?: header.takeIf { it.startsWith("http") }

                    TramAlert(
                        id = entity.id,
                        lines = tramRoutes.map { tramMap[it] ?: it }.distinct().sortedWith(LineOrder),
                        start = ap?.takeIf { it.hasStart() && it.start > 0 }?.start,
                        end = ap?.takeIf { it.hasEnd() && it.end > 0 }?.end,
                        header = if (header.startsWith("http")) "" else header,
                        description = HtmlCompat.fromHtml(trans(alert.descriptionText), HtmlCompat.FROM_HTML_MODE_LEGACY)
                            .toString().trim(),
                        url = link,
                        effect = effectLabel(alert.effect),
                    )
                }

                val now = System.currentTimeMillis() / 1000
                Result.Success(
                    alerts.sortedWith(compareBy({ !it.isActive(now) }, { it.start ?: 0L }))
                )
            }
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Hálózati hiba")
        }
    }
}
