package com.amerthamer.tramagent.ui.home

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
import androidx.lifecycle.compose.LifecycleResumeEffect
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
import java.io.File
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

@Composable
fun HomeScreen(onOpen: (Feature) -> Unit) {
    val context = LocalContext.current
    // A GTFS-állapotot minden visszatéréskor újraolvassuk (pl. frissítés után)
    var gtfsAge by remember { mutableStateOf<Long?>(null) }
    LifecycleResumeEffect(Unit) {
        gtfsAge = gtfsAgeDays(File(context.filesDir, "gtfs/stops.txt"))
        onPauseOrDispose { }
    }

    NightBox {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()),
        ) {
            // Fejléc: üdvözlés + kijelzős óra
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("TRAMAGENT", style = MaterialTheme.typography.labelSmall, color = TramYellow)
                    Text(greeting(), style = MaterialTheme.typography.headlineLarge)
                }
                LedClock()
            }

            TramTrack(Modifier.padding(top = 8.dp))

            LedMarquee(
                text = gtfsStatusText(gtfsAge),
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

private fun greeting(): String = when (LocalTime.now(ZoneId.of("Europe/Budapest")).hour) {
    in 5..8 -> "Jó reggelt!"
    in 9..17 -> "Jó napot!"
    in 18..21 -> "Jó estét!"
    else -> "Jó éjszakát!"
}

private fun gtfsAgeDays(file: File): Long? =
    if (file.exists()) TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - file.lastModified()) else null

private fun gtfsSubtitle(age: Long?): String = when (age) {
    null -> "Még nincs letöltve"
    0L -> "Frissítve ma"
    else -> "Frissítve $age napja"
}

private fun gtfsStatusText(age: Long?): String = when (age) {
    null -> "★ Nincs letöltött GTFS – a közeli indulásokhoz és a térképhez frissítsd a menetrendi adatokat ★"
    0L -> "★ Menetrendi adatok: frissek (ma letöltve) ★ Jó utat! ★"
    else -> "★ Menetrendi adatok: $age napja frissítve ★ Jó utat! ★"
}
