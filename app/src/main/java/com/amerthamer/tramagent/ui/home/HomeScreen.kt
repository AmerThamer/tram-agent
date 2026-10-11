package com.amerthamer.tramagent.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amerthamer.tramagent.data.AlertsRepository
import com.amerthamer.tramagent.data.ApiKeyStore
import com.amerthamer.tramagent.domain.Dk2
import com.amerthamer.tramagent.ui.components.AlertBanner
import com.amerthamer.tramagent.ui.components.TramArriveEasing
import com.amerthamer.tramagent.ui.components.GlassCard
import com.amerthamer.tramagent.ui.components.IconChip
import com.amerthamer.tramagent.ui.components.LedClock
import com.amerthamer.tramagent.ui.components.LedMarquee
import com.amerthamer.tramagent.ui.components.NightBox
import com.amerthamer.tramagent.ui.components.TramTrack
import com.amerthamer.tramagent.ui.components.tramArrival
import com.amerthamer.tramagent.ui.nav.Feature
import com.amerthamer.tramagent.ui.theme.TextSecondary
import com.amerthamer.tramagent.ui.theme.TramYellow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit

@Composable
fun HomeScreen(onOpen: (Feature) -> Unit, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    // A GTFS-állapotot minden visszatéréskor újraolvassuk (pl. frissítés után)
    var gtfsAge by remember { mutableStateOf<Long?>(null) }
    val alertsState by AlertsRepository.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LifecycleResumeEffect(Unit) {
        gtfsAge = gtfsAgeDays(File(context.filesDir, "gtfs/stops.txt"))
        scope.launch { AlertsRepository.refresh() }
        onPauseOrDispose { }
    }
    val now = System.currentTimeMillis() / 1000
    val activeAlerts = alertsState.alerts.orEmpty().filter { it.isActive(now) }

    NightBox {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()),
        ) {
            // Fejléc: az app neve + kijelzős óra
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("TramAgent", style = MaterialTheme.typography.headlineLarge, color = TramYellow)
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Beállítások", tint = TextSecondary)
                }
                LedClock()
            }

            TramTrack(Modifier.padding(top = 8.dp))

            // Kulcs nélkül a valós idejű funkciók nem működnek – ezt jelezzük
            val userKey by ApiKeyStore.userKey.collectAsStateWithLifecycle()
            if (userKey == null && ApiKeyStore.buildKey == null) {
                GlassCard(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp), onClick = onOpenSettings) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconChip(Icons.Rounded.Key)
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text("Add meg a BKK API-kulcsot", style = MaterialTheme.typography.titleMedium)
                            Text("A valós idejű funkciókhoz szükséges", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = TramYellow)
                    }
                }
            }

            AnimatedVisibility(
                visible = activeAlerts.isNotEmpty(),
                enter = expandVertically(tween(500, easing = TramArriveEasing)) + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                val lines = activeAlerts.flatMap { it.lines }.distinct().sortedWith(Dk2.LineOrder)
                AlertBanner(
                    text = "${activeAlerts.size} aktív zavar · " + lines.joinToString(", "),
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                    onClick = { onOpen(Feature.Alerts) },
                )
            }

            LedMarquee(
                text = marqueeText(activeAlerts, gtfsAge),
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
            )

            Spacer(Modifier.height(6.dp))

            // Csempék: 2×2 négyzetes + 2 széles
            val square = Feature.entries.take(4)
            val wide = Feature.entries.drop(4)
            Column(
                Modifier.padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                square.chunked(2).forEachIndexed { row, pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEachIndexed { col, f ->
                            SquareTile(f, Modifier.weight(1f).tramArrival(row * 2 + col)) { onOpen(f) }
                        }
                    }
                }
                wide.forEachIndexed { i, f ->
                    val subtitle = if (f == Feature.Gtfs) gtfsSubtitle(gtfsAge) else f.subtitle
                    WideTile(f, subtitle, Modifier.tramArrival(4 + i)) { onOpen(f) }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SquareTile(f: Feature, modifier: Modifier, onClick: () -> Unit) {
    GlassCard(modifier.height(136.dp), onClick = onClick) {
        IconChip(f.icon)
        Spacer(Modifier.weight(1f))
        Text(f.title, style = MaterialTheme.typography.titleMedium)
        Text(f.subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
}

@Composable
private fun WideTile(f: Feature, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    GlassCard(modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconChip(f.icon)
            Column(Modifier.padding(start = 14.dp)) {
                Text(f.title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
    }
}

private fun gtfsAgeDays(file: File): Long? =
    if (file.exists()) TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - file.lastModified()) else null

private fun gtfsSubtitle(age: Long?): String = when (age) {
    null -> "Még nincs letöltve"
    0L -> "Frissítve ma"
    else -> "Frissítve $age napja"
}

/** Futószöveg: ha van aktív zavar, azokat görgeti, különben a GTFS-állapotot. */
private fun marqueeText(alerts: List<Dk2.TramAlert>, gtfsAge: Long?): String =
    if (alerts.isEmpty()) gtfsStatusText(gtfsAge)
    else alerts.joinToString(" ★ ", prefix = "★ ", postfix = " ★") { a ->
        a.lines.joinToString(", ") + ": " + a.header.ifBlank { a.effect ?: "zavar" }
    }

private fun gtfsStatusText(age: Long?): String = when (age) {
    null -> "★ Nincs letöltött GTFS – a közeli indulásokhoz és a térképhez frissítsd a menetrendi adatokat ★"
    0L -> "★ Menetrendi adatok: frissek (ma letöltve) ★ Jó utat! ★"
    else -> "★ Menetrendi adatok: $age napja frissítve ★ Jó utat! ★"
}
