package com.amerthamer.tramagent.ui.alerts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amerthamer.tramagent.data.AlertsRepository
import com.amerthamer.tramagent.domain.Dk2
import com.amerthamer.tramagent.ui.components.GlassCard
import com.amerthamer.tramagent.ui.components.LedText
import com.amerthamer.tramagent.ui.components.LineBadge
import com.amerthamer.tramagent.ui.components.StateMessage
import com.amerthamer.tramagent.ui.components.TramLoader
import com.amerthamer.tramagent.ui.components.TramScreen
import com.amerthamer.tramagent.ui.components.ledPanel
import com.amerthamer.tramagent.ui.components.tramArrival
import com.amerthamer.tramagent.ui.theme.LedAmber
import com.amerthamer.tramagent.ui.theme.OnTimeGreen
import com.amerthamer.tramagent.ui.theme.TextSecondary
import com.amerthamer.tramagent.ui.theme.TramYellow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Budapest = ZoneId.of("Europe/Budapest")
private val LedRed = Color(0xFFFF5A4E)

/** DK2 – Aktív zavarok (GTFS-RT Alerts, csak villamos). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(onBack: () -> Unit) {
    val state by AlertsRepository.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var selectedLine by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { AlertsRepository.refresh() }

    TramScreen(
        kicker = "DK2 · GTFS-RT",
        title = "Aktív zavarok",
        onBack = onBack,
        actions = { RefreshButton(state.loading) { scope.launch { AlertsRepository.refresh(force = true) } } },
    ) {
        val alerts = state.alerts
        PullToRefreshBox(
            isRefreshing = state.loading && alerts != null,
            onRefresh = { scope.launch { AlertsRepository.refresh(force = true) } },
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                alerts == null && state.error != null -> ScrollableState {
                    StateMessage(
                        icon = Icons.Rounded.CloudOff,
                        title = "Nem sikerült lekérni a zavarokat",
                        message = state.error.orEmpty(),
                        actionText = "Újrapróbálom",
                        onAction = { scope.launch { AlertsRepository.refresh(force = true) } },
                    )
                }

                alerts == null -> ScrollableState { TramLoader(Modifier.fillMaxWidth(), text = "Zavarok lekérése…") }

                alerts.isEmpty() -> ScrollableState {
                    SummaryPanel(emptyList())
                    StateMessage(
                        icon = Icons.Rounded.CheckCircle,
                        title = "Zavartalan villamosforgalom",
                        message = "Jelenleg egyik villamosvonalon sincs bejelentett zavar.",
                    )
                }

                else -> {
                    val allLines = alerts.flatMap { it.lines }.distinct().sortedWith(Dk2.LineOrder)
                    val shown = selectedLine?.let { l -> alerts.filter { l in it.lines } } ?: alerts

                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item { SummaryPanel(alerts) }
                        if (allLines.size > 1) {
                            item {
                                LineFilter(allLines, selectedLine) { selectedLine = if (selectedLine == it) null else it }
                            }
                        }
                        itemsIndexed(shown, key = { _, a -> a.id }) { i, alert ->
                            AlertCard(alert, Modifier.animateItem().tramArrival(i))
                        }
                        item {
                            state.fetchedAt?.let {
                                Text(
                                    "Frissítve: " + DateTimeFormatter.ofPattern("HH:mm:ss")
                                        .format(Instant.ofEpochMilli(it).atZone(Budapest)) + " · húzd le a frissítéshez",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Görgethető tartó az állapotüzenetekhez, hogy a lehúzós frissítés ott is működjön. */
@Composable
private fun ScrollableState(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { content() }
}

/** Frissítés gomb, ami töltés közben forog. */
@Composable
private fun RefreshButton(loading: Boolean, onClick: () -> Unit) {
    val spin = rememberInfiniteTransition(label = "spin")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "a")
    IconButton(onClick = onClick, enabled = !loading) {
        Icon(
            Icons.Rounded.Refresh, contentDescription = "Frissítés", tint = TramYellow,
            modifier = if (loading) Modifier.rotate(angle) else Modifier,
        )
    }
}

/** Kijelzős összesítő panel: hány zavar van, mely vonalakon. */
@Composable
private fun SummaryPanel(alerts: List<Dk2.TramAlert>) {
    val now = System.currentTimeMillis() / 1000
    val active = alerts.count { it.isActive(now) }
    val planned = alerts.size - active
    val lines = alerts.filter { it.isActive(now) }.flatMap { it.lines }.distinct().sortedWith(Dk2.LineOrder)

    Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp).ledPanel(12.dp).padding(horizontal = 14.dp, vertical = 10.dp)) {
        if (alerts.isEmpty()) {
            LedText("Nincs zavar", fontSize = 32.sp, color = OnTimeGreen)
            LedText("Minden villamos menetrend szerint", fontSize = 18.sp, color = OnTimeGreen.copy(alpha = 0.7f))
        } else {
            LedText(
                if (active > 0) "$active aktív zavar" else "Nincs aktív zavar",
                fontSize = 32.sp,
                color = if (active > 0) LedRed else LedAmber,
            )
            if (lines.isNotEmpty()) LedText("Érintett: " + lines.joinToString(" · "), fontSize = 20.sp)
            if (planned > 0) LedText("+ $planned tervezett", fontSize = 18.sp, color = LedAmber.copy(alpha = 0.6f))
        }
    }
}

/** Vízszintes vonalszűrő; a kiválasztott vonal teli sárga, a többi csak keretes. */
@Composable
private fun LineFilter(lines: List<String>, selected: String?, onToggle: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Szűrés:", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        lines.forEach { line ->
            LineBadge(
                line,
                dimmed = selected != null && selected != line,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onToggle(line) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlertCard(alert: Dk2.TramAlert, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable(alert.id) { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    val now = System.currentTimeMillis() / 1000
    val active = alert.isActive(now)
    val hasMore = alert.description.isNotBlank() || alert.url != null

    GlassCard(
        modifier.fillMaxWidth().animateContentSize(),
        onClick = if (hasMore) ({ expanded = !expanded }) else null,
    ) {
        Row(verticalAlignment = Alignment.Top) {
            FlowRow(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                alert.lines.forEach { LineBadge(it) }
            }
            StatusChip(active)
        }

        Spacer(Modifier.height(10.dp))
        alert.effect?.let {
            Text(it.uppercase(Locale.ROOT), style = MaterialTheme.typography.labelSmall, color = TramYellow)
        }
        Text(
            alert.header.ifBlank { "Zavar a villamosforgalomban" },
            style = MaterialTheme.typography.titleMedium,
        )

        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Schedule, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp))
            Text(
                timeRange(alert.start, alert.end),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(start = 6.dp).weight(1f),
            )
            if (hasMore) {
                Icon(
                    Icons.Rounded.ExpandMore, contentDescription = if (expanded) "Kevesebb" else "Részletek",
                    tint = TextSecondary, modifier = Modifier.rotate(if (expanded) 180f else 0f),
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(Modifier.padding(top = 10.dp)) {
                if (alert.description.isNotBlank()) {
                    Text(alert.description, style = MaterialTheme.typography.bodyMedium)
                }
                alert.url?.let { url ->
                    TextButton(onClick = { uri.openUri(url) }, contentPadding = PaddingValues(0.dp)) {
                        Text("Részletek a BKK oldalán", color = TramYellow)
                        Icon(
                            Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, tint = TramYellow,
                            modifier = Modifier.padding(start = 6.dp).size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Kijelzős állapotcímke: piros „MOST”, ha folyamatban van, borostyán „TERVEZETT”, ha még csak lesz. */
@Composable
private fun StatusChip(active: Boolean) {
    val blink = rememberInfiniteTransition(label = "blink")
    val alpha by blink.animateFloat(1f, 0.35f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "al")
    Box(Modifier.ledPanel(6.dp).padding(horizontal = 8.dp, vertical = 1.dp)) {
        if (active) LedText("● most", fontSize = 16.sp, color = LedRed.copy(alpha = alpha))
        else LedText("tervezett", fontSize = 16.sp)
    }
}

private val DayFormat = DateTimeFormatter.ofPattern("MMM d.", Locale("hu", "HU"))
private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm")

private fun formatPoint(epochSec: Long): String {
    val t = Instant.ofEpochSecond(epochSec).atZone(Budapest)
    val today = LocalDate.now(Budapest)
    val day = when (t.toLocalDate()) {
        today -> "ma"
        today.plusDays(1) -> "holnap"
        today.minusDays(1) -> "tegnap"
        else -> DayFormat.format(t)
    }
    return "$day ${TimeFormat.format(t)}"
}

private fun timeRange(start: Long?, end: Long?): String = when {
    start != null && end != null -> "${formatPoint(start)} → ${formatPoint(end)}"
    start != null -> "${formatPoint(start)} óta, visszavonásig"
    end != null -> "${formatPoint(end)}-ig"
    else -> "Időtartam ismeretlen"
}
