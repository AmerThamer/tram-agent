package com.amerthamer.tramagent.ui.gtfs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amerthamer.tramagent.gtfs.GtfsRepository
import com.amerthamer.tramagent.gtfs.GtfsUpdater
import com.amerthamer.tramagent.ui.components.GlassCard
import com.amerthamer.tramagent.ui.components.LedText
import com.amerthamer.tramagent.ui.components.TramButton
import com.amerthamer.tramagent.ui.components.TramProgress
import com.amerthamer.tramagent.ui.components.TramScreen
import com.amerthamer.tramagent.ui.components.ledPanel
import com.amerthamer.tramagent.ui.components.tramArrival
import com.amerthamer.tramagent.ui.theme.LedAmber
import com.amerthamer.tramagent.ui.theme.OnTimeGreen
import com.amerthamer.tramagent.ui.theme.TextSecondary
import com.amerthamer.tramagent.ui.theme.TramYellow
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val LedRed = androidx.compose.ui.graphics.Color(0xFFFF5A4E)
private val DateFmt = DateTimeFormatter.ofPattern("yyyy.MM.dd. HH:mm")

/** GTFS frissítés – a menetrendi adatok letöltése a DK3-hoz. */
@Composable
fun GtfsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val state by GtfsRepository.state.collectAsStateWithLifecycle()

    // A meglévő adatok (a letöltés előtt / nélkül is megmutatjuk)
    var existing by remember { mutableStateOf<List<Pair<String, Long>>>(emptyList()) }
    var updatedAt by remember { mutableStateOf<Long?>(null) }
    fun reloadExisting() {
        val dir = GtfsUpdater.gtfsDir(context)
        existing = dir.listFiles()?.filter(File::isFile)?.map { it.name to it.length() }?.sortedByDescending { it.second }.orEmpty()
        updatedAt = File(dir, "stops.txt").takeIf { it.exists() }?.lastModified()
    }
    LifecycleResumeEffect(state) {
        reloadExisting()
        onPauseOrDispose { }
    }

    val running = state as? GtfsRepository.State.Running
    val indexing = state as? GtfsRepository.State.Indexing
    val busy = running != null || indexing != null
    val files = (state as? GtfsRepository.State.Done)?.result?.files ?: existing

    TramScreen(kicker = "GTFS · go.bkk.hu", title = "Menetrendi adatok", onBack = onBack) {
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(Modifier.fillMaxWidth().ledPanel(12.dp).padding(horizontal = 14.dp, vertical = 10.dp)) {
                    when (val s = state) {
                        is GtfsRepository.State.Running -> {
                            LedText(
                                s.progress?.let { "Letöltés ${(it * 100).toInt()}%" }
                                    ?: if (s.bytes > 0) "Letöltés ${humanSize(s.bytes)}" else "Letöltés…",
                                fontSize = 32.sp,
                            )
                            LedText("Ne zárd be az alkalmazást", fontSize = 18.sp, color = LedAmber.copy(alpha = 0.6f))
                        }
                        is GtfsRepository.State.Indexing -> {
                            LedText("Indexelés ${(s.progress * 100).toInt()}%", fontSize = 32.sp)
                            LedText("Villamos-indulások rendezése", fontSize = 18.sp, color = LedAmber.copy(alpha = 0.6f))
                        }
                        is GtfsRepository.State.Done -> {
                            LedText("Kész ✓", fontSize = 32.sp, color = OnTimeGreen)
                            LedText("${s.result.files.size} fájl frissítve", fontSize = 18.sp, color = OnTimeGreen.copy(alpha = 0.7f))
                        }
                        is GtfsRepository.State.Failed -> {
                            LedText("Hiba", fontSize = 32.sp, color = LedRed)
                            LedText(s.message, fontSize = 18.sp, color = LedRed.copy(alpha = 0.8f))
                        }
                        GtfsRepository.State.Idle -> {
                            val at = updatedAt
                            LedText(if (at == null) "Nincs letöltve" else "Letöltve", fontSize = 32.sp)
                            LedText(
                                if (at == null) "Töltsd le a közeli indulásokhoz"
                                else DateFmt.format(Instant.ofEpochMilli(at).atZone(ZoneId.of("Europe/Budapest"))),
                                fontSize = 18.sp, color = LedAmber.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
            }
            if (busy) item { TramProgress(indexing?.progress ?: running?.progress, Modifier.padding(vertical = 4.dp)) }
            item {
                TramButton(
                    when (state) {
                        is GtfsRepository.State.Running -> "Letöltés folyamatban…"
                        is GtfsRepository.State.Indexing -> "Indexelés folyamatban…"
                        is GtfsRepository.State.Failed -> "Újrapróbálom"
                        else -> if (updatedAt == null) "Letöltés indítása" else "Frissítés most"
                    },
                    onClick = { GtfsRepository.start(context) },
                    icon = Icons.Rounded.CloudDownload,
                    enabled = !busy,
                )
            }
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("Mire kell?", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "A BKK teljes statikus menetrendje (GTFS). " +
                            "A közeli indulások (DK3) ebből dolgoznak, ezért érdemes hetente frissíteni. " +
                            "Wi-Fi ajánlott.",
                        style = MaterialTheme.typography.bodyMedium, color = TextSecondary,
                    )
                }
            }
            if (files.isNotEmpty()) {
                item {
                    Text("Tárolt állományok", style = MaterialTheme.typography.labelSmall, color = TramYellow,
                        modifier = Modifier.padding(top = 4.dp))
                }
                itemsIndexed(files, key = { _, f -> f.first }) { i, (name, size) ->
                    Row(Modifier.fillMaxWidth().tramArrival(i.coerceAtMost(10)), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Description, null, tint = TextSecondary)
                        Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(start = 10.dp))
                        Text(humanSize(size), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

private fun humanSize(bytes: Long): String = when {
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "%.0f kB".format(bytes / 1_000.0)
    else -> "$bytes B"
}
