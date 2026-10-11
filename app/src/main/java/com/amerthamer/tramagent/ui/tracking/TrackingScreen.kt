package com.amerthamer.tramagent.ui.tracking

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.Tram
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amerthamer.tramagent.domain.Dk5
import com.amerthamer.tramagent.domain.Futar
import com.amerthamer.tramagent.ui.components.DelayChip
import com.amerthamer.tramagent.ui.components.GlassCard
import com.amerthamer.tramagent.ui.components.LedText
import com.amerthamer.tramagent.ui.components.LineBadge
import com.amerthamer.tramagent.ui.components.StateMessage
import com.amerthamer.tramagent.ui.components.TramButton
import com.amerthamer.tramagent.ui.components.TramLoader
import com.amerthamer.tramagent.ui.components.TramScreen
import com.amerthamer.tramagent.ui.components.TramTextField
import com.amerthamer.tramagent.ui.components.delayLabel
import com.amerthamer.tramagent.ui.components.tramArrival
import com.amerthamer.tramagent.ui.theme.Night
import com.amerthamer.tramagent.ui.theme.TextSecondary
import com.amerthamer.tramagent.ui.theme.TramYellow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val Budapest = ZoneId.of("Europe/Budapest")
private val Hm = DateTimeFormatter.ofPattern("HH:mm")
private val Hms = DateTimeFormatter.ofPattern("HH:mm:ss")
private fun clock(epochSec: Long, f: DateTimeFormatter = Hm) = f.format(Instant.ofEpochSecond(epochSec).atZone(Budapest))

class TrackingViewModel : ViewModel() {
    sealed interface State {
        data object Idle : State
        data object Loading : State
        data object NotFound : State
        data class Error(val message: String) : State
        data class Tracking(val data: Dk5.Tracking) : State
    }

    var query by mutableStateOf("")
    var state by mutableStateOf<State>(State.Idle)
        private set
    private var job: Job? = null

    /** Követés indítása; utána 20 mp-enként csendben frissít, amíg a képernyő látszik. */
    fun track(label: String = query, silent: Boolean = false) {
        val q = label.trim()
        if (q.isEmpty()) return
        query = q
        job?.cancel()
        if (!(silent && state is State.Tracking)) state = State.Loading
        job = viewModelScope.launch {
            while (isActive) {
                state = try {
                    Dk5.track(q)?.let { State.Tracking(it) } ?: State.NotFound
                } catch (e: Exception) {
                    // Frissítéskor egy átmeneti hiba ne tüntesse el a meglévő adatot
                    if (state is State.Tracking) state else State.Error(e.message ?: "Hálózati hiba")
                }
                if (state !is State.Tracking) break
                delay(20_000)
            }
        }
    }

    fun pause() { job?.cancel() }
    /** Visszatéréskor csendben (a meglévő adat megtartásával) folytatja a frissítést. */
    fun resume() { if (state is State.Tracking) track(query, silent = true) }
}

/** DK5 – Nyomkövetés. */
@Composable
fun TrackingScreen(
    initialVehicle: String?,
    onBack: () -> Unit,
    onShowOnMap: (String) -> Unit,
    vm: TrackingViewModel = viewModel(),
) {
    val focus = LocalFocusManager.current
    LaunchedEffect(initialVehicle) {
        if (initialVehicle != null && vm.state is TrackingViewModel.State.Idle) vm.track(initialVehicle)
    }
    // Csak akkor frissít, amikor a képernyő látszik
    LifecycleResumeEffect(Unit) {
        vm.resume()
        onPauseOrDispose { vm.pause() }
    }

    TramScreen(kicker = "DK5 · Nyomkövetés", title = "Menet követése", onBack = onBack) {
        val listState = rememberLazyListState()
        val state = vm.state
        val tracking = (state as? TrackingViewModel.State.Tracking)?.data

        // Első betöltéskor a lista az aktuális megállóhoz görget (előtte 3 fejléc-elem van,
        // és két megtett megállót még meghagyunk felül)
        LaunchedEffect(tracking?.trip?.tripId) {
            // Rövid megtett szakasznál maradjon látható a fejléc kártya
            tracking?.takeIf { it.nextIndex > 4 }?.let { listState.animateScrollToItem(it.nextIndex - 3 + HeaderItems) }
        }

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item {
                TramTextField(
                    value = vm.query,
                    onValueChange = { vm.query = it },
                    label = "Pályaszám",
                    placeholder = "pl. 1342",
                    leadingIcon = Icons.Rounded.Tag,
                    onSubmit = { focus.clearFocus(); vm.track() },
                )
                Spacer(Modifier.height(10.dp))
                TramButton(
                    "Követés indítása",
                    onClick = { focus.clearFocus(); vm.track() },
                    icon = Icons.Rounded.Timeline,
                    enabled = vm.query.isNotBlank() && state !is TrackingViewModel.State.Loading,
                )
                Spacer(Modifier.height(12.dp))
            }

            when (state) {
                TrackingViewModel.State.Idle -> item {
                    StateMessage(
                        Icons.Rounded.Tram, "Melyik villamost követjük?",
                        "Add meg a jármű pályaszámát, és megmutatom a menetét megállónként, késéssel együtt.",
                    )
                }
                TrackingViewModel.State.Loading -> item { TramLoader(Modifier.fillMaxWidth(), text = "Jármű keresése…") }
                TrackingViewModel.State.NotFound -> item {
                    StateMessage(
                        Icons.Rounded.SearchOff, "Nincs ilyen aktív jármű",
                        "A(z) „${vm.query}” pályaszámú jármű most nem közlekedik, vagy nem küld adatot.",
                    )
                }
                is TrackingViewModel.State.Error -> item {
                    StateMessage(
                        Icons.Rounded.CloudOff, "Nem sikerült a lekérdezés", state.message,
                        actionText = "Újrapróbálom", onAction = { vm.track() },
                    )
                }
                is TrackingViewModel.State.Tracking -> {
                    val t = state.data
                    item { TripHeader(t) { onShowOnMap(vm.query) } }
                    item { Spacer(Modifier.height(12.dp)) }
                    itemsIndexed(t.trip.stops, key = { i, s -> "${s.stopId}_$i" }) { i, stop ->
                        StopRow(
                            stop = stop,
                            index = i,
                            last = i == t.trip.stops.lastIndex,
                            nextIndex = t.nextIndex,
                            atStop = t.vehicle.stoppedAtStop,
                            modifier = Modifier.tramArrival(i.coerceAtMost(12)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TripHeader(t: Dk5.Tracking, onMap: () -> Unit) {
    val delay = t.currentDelaySec
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LineBadge(t.trip.line, size = 46.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = TramYellow, modifier = Modifier.size(16.dp))
                    Text(t.trip.headsign, style = MaterialTheme.typography.titleMedium, maxLines = 2,
                        modifier = Modifier.padding(start = 4.dp))
                }
                Text("Pályaszám: ${t.vehicle.licensePlate ?: "?"}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            DelayChip(delay, large = true)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(delayLabel(delay), style = MaterialTheme.typography.titleMedium)
                Text(
                    "Frissítve ${clock(t.fetchedAtSec, Hms)}",
                    style = MaterialTheme.typography.bodySmall, color = TextSecondary,
                )
            }
            TextButton(onClick = onMap) {
                Icon(Icons.Rounded.Map, null, tint = TramYellow, modifier = Modifier.size(18.dp))
                Text("Térkép", color = TramYellow, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}

private const val HeaderItems = 3
private val RailWidth = 40.dp

/**
 * Egy megálló a függőleges vonaldiagramon.
 * A megtett szakasz sárga, a hátralévő halvány; a villamos ikon a következő megálló előtt
 * (úton) vagy rajta (megállóban) látszik.
 */
@Composable
private fun StopRow(
    stop: Futar.TripStop,
    index: Int,
    last: Boolean,
    nextIndex: Int,
    atStop: Boolean,
    modifier: Modifier = Modifier,
) {
    val passed = index < nextIndex
    val isNext = index == nextIndex
    val dim = Color.White.copy(alpha = 0.18f)

    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(Modifier.width(RailWidth).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxWidth().fillMaxHeight()) {
                val cx = size.width / 2
                val cy = size.height / 2
                val w = 4.dp.toPx()
                if (index > 0) drawLine(if (index <= nextIndex) TramYellow else dim, Offset(cx, 0f), Offset(cx, cy), w)
                if (!last) drawLine(if (passed) TramYellow else dim, Offset(cx, cy), Offset(cx, size.height), w)
                when {
                    passed -> drawCircle(TramYellow, 5.dp.toPx(), Offset(cx, cy))
                    isNext -> {
                        drawCircle(Night, 8.dp.toPx(), Offset(cx, cy))
                        drawCircle(TramYellow, 8.dp.toPx(), Offset(cx, cy), style = Stroke(3.dp.toPx()))
                    }
                    else -> {
                        drawCircle(Night, 6.dp.toPx(), Offset(cx, cy))
                        drawCircle(dim, 6.dp.toPx(), Offset(cx, cy), style = Stroke(2.dp.toPx()))
                    }
                }
            }
            // A villamos: megállóban a ponton, úton a pont fölött (a két megálló között)
            if (isNext) {
                TramPin(Modifier.offset(y = if (atStop || index == 0) 0.dp else (-30).dp))
            }
        }

        Row(
            Modifier.weight(1f).padding(start = 8.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stop.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = if (isNext) FontWeight.ExtraBold else FontWeight.SemiBold,
                    ),
                    color = if (passed) TextSecondary else Color.White,
                )
                val sched = stop.scheduled?.let { clock(it) }
                val pred = stop.predicted?.let { clock(it) }
                Text(
                    when {
                        sched != null && pred != null && sched != pred -> "menetrend $sched · várható $pred"
                        sched != null -> "menetrend $sched"
                        pred != null -> "várható $pred"
                        else -> "nincs időadat"
                    },
                    style = MaterialTheme.typography.bodySmall, color = TextSecondary,
                )
            }
            if (isNext) LedText("köv.", fontSize = 16.sp, modifier = Modifier.padding(end = 8.dp))
            DelayChip(stop.delaySec)
        }
    }
}

/** Pulzáló sárga villamos-jelölő. */
@Composable
private fun TramPin(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "pin")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1500), RepeatMode.Restart), label = "p")
    Box(
        modifier
            .size(30.dp)
            .drawBehind {
                drawCircle(TramYellow.copy(alpha = 0.45f * (1f - p)), radius = size.minDimension / 2 + 12.dp.toPx() * p)
            }
            .clip(CircleShape)
            .background(TramYellow),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Tram, contentDescription = "A villamos itt jár", tint = Night, modifier = Modifier.size(18.dp))
    }
}
