package com.amerthamer.tramagent.ui.nearby

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amerthamer.tramagent.domain.Dk3
import com.amerthamer.tramagent.gtfs.GtfsUpdater
import com.amerthamer.tramagent.ui.components.GlassCard
import com.amerthamer.tramagent.ui.components.LedText
import com.amerthamer.tramagent.ui.components.LineBadge
import com.amerthamer.tramagent.ui.components.StateMessage
import com.amerthamer.tramagent.ui.components.TramLoader
import com.amerthamer.tramagent.ui.components.TramProgress
import com.amerthamer.tramagent.ui.components.TramScreen
import com.amerthamer.tramagent.ui.components.ledPanel
import com.amerthamer.tramagent.ui.components.tramArrival
import com.amerthamer.tramagent.ui.theme.NightSurface
import com.amerthamer.tramagent.ui.theme.TextSecondary
import com.amerthamer.tramagent.ui.theme.TramYellow
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import kotlin.coroutines.resume

private val Budapest = ZoneId.of("Europe/Budapest")

class NearbyViewModel(app: Application) : AndroidViewModel(app) {

    sealed interface State {
        data object NeedPermission : State
        data object Locating : State
        data class Searching(val source: String) : State
        /** Egyszeri villamos-index készítés (ha a GTFS-hez még nincs). */
        data class Indexing(val progress: Float) : State
        data object MissingGtfs : State
        data class Done(val result: Dk3.Result.Success, val source: String) : State
    }

    var state by mutableStateOf<State>(State.NeedPermission)
        private set

    /** Pozíció (utolsó ismert → friss → alapértelmezett), majd keresés a GTFS-ben. */
    @SuppressLint("MissingPermission")
    fun search() {
        if (state is State.Locating || state is State.Searching || state is State.Indexing) return
        state = State.Locating
        viewModelScope.launch {
            val (loc, source) = currentLocation(getApplication())
            val lat = loc?.latitude ?: DEFAULT_LAT
            val lon = loc?.longitude ?: DEFAULT_LON
            state = State.Searching(source)
            val dir = GtfsUpdater.gtfsDir(getApplication())
            val r = Dk3.findNearby(dir, lat, lon, LocalTime.now(Budapest)) { p -> state = State.Indexing(p) }
            state = when (r) {
                Dk3.Result.MissingGtfs -> State.MissingGtfs
                is Dk3.Result.Success -> State.Done(r, source)
            }
        }
    }

    fun permissionDenied() { state = State.NeedPermission }

    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(context: Context): Pair<Location?, String> {
        val fused = LocationServices.getFusedLocationProviderClient(context)
        val last = suspendCancellableCoroutine { c ->
            fused.lastLocation.addOnSuccessListener { c.resume(it) }.addOnFailureListener { c.resume(null) }
        }
        if (last != null) return last to "Saját pozíció"
        val fresh = suspendCancellableCoroutine { c ->
            fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener { c.resume(it) }.addOnFailureListener { c.resume(null) }
        }
        return if (fresh != null) fresh to "Saját pozíció" else null to "Alapértelmezett pozíció (belváros)"
    }

    private companion object {
        // Ha a pozíció nem kérhető le, a belvárosból keresünk (az eredeti alkalmazás alapértéke)
        const val DEFAULT_LAT = 47.49794
        const val DEFAULT_LON = 19.05403
    }
}

/** DK3 – Indulások a közelben (GTFS menetrend alapján). */
@Composable
fun NearbyScreen(onBack: () -> Unit, onOpenGtfs: () -> Unit, vm: NearbyViewModel = viewModel()) {
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) vm.search() else vm.permissionDenied()
    }
    fun hasPermission() = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    fun start() {
        if (hasPermission()) vm.search()
        else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    // Ha már van engedély, rögtön keresünk
    LaunchedEffect(Unit) { if (vm.state is NearbyViewModel.State.NeedPermission && hasPermission()) vm.search() }

    // A „hány perc múlva” felirat magától fogy
    var now by remember { mutableStateOf(LocalTime.now(Budapest)) }
    LaunchedEffect(Unit) { while (true) { delay(15_000); now = LocalTime.now(Budapest) } }

    TramScreen(
        kicker = "DK3 · GTFS menetrend",
        title = "Közeli indulások",
        onBack = onBack,
        actions = {
            if (vm.state is NearbyViewModel.State.Done) {
                IconButton(onClick = ::start) { Icon(Icons.Rounded.Refresh, "Frissítés", tint = TramYellow) }
            }
        },
    ) {
        when (val s = vm.state) {
            NearbyViewModel.State.NeedPermission -> StateMessage(
                Icons.Rounded.MyLocation, "Hol vagy most?",
                "A 300 méteren belüli megállók következő villamosaihoz szükség van a helyzetedre.",
                actionText = "Helymeghatározás engedélyezése", onAction = ::start,
            )
            NearbyViewModel.State.Locating -> TramLoader(Modifier.fillMaxWidth(), text = "Pozíció meghatározása…")
            is NearbyViewModel.State.Searching -> TramLoader(Modifier.fillMaxWidth(), text = "Indulások keresése…")
            is NearbyViewModel.State.Indexing -> Column(Modifier.fillMaxWidth()) {
                TramProgress(s.progress, Modifier.padding(top = 24.dp))
                Text(
                    "Egyszeri előkészítés: villamos-index készítése… ${(s.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium, color = TextSecondary,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            NearbyViewModel.State.MissingGtfs -> StateMessage(
                Icons.Rounded.CloudDownload, "Nincsenek menetrendi adatok",
                "A közeli indulásokhoz előbb le kell tölteni a BKK GTFS menetrendet.",
                actionText = "Ugrás a GTFS frissítéshez", onAction = onOpenGtfs,
            )
            is NearbyViewModel.State.Done -> Results(s, now)
        }
    }
}

@Composable
private fun Results(s: NearbyViewModel.State.Done, now: LocalTime) {
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.NearMe, null, tint = TramYellow, modifier = Modifier.size(16.dp))
                Text(
                    "${s.source} · 300 m · 60 perc",
                    style = MaterialTheme.typography.bodySmall, color = TextSecondary,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
        if (s.result.stops.isEmpty()) {
            item {
                StateMessage(
                    if (s.result.anyStopInRadius) Icons.Rounded.SearchOff else Icons.Rounded.LocationOff,
                    if (s.result.anyStopInRadius) "Most nem indul villamos" else "Nincs megálló a közelben",
                    if (s.result.anyStopInRadius) "A közeli megállókból 60 percen belül nincs villamosindulás."
                    else "300 méteren belül nincs megálló.",
                )
            }
        }
        itemsIndexed(s.result.stops, key = { _, st -> st.stopId }) { i, stop ->
            StopBoard(stop, now, Modifier.tramArrival(i))
        }
    }
}

/** Egy megálló kártyája peronkijelző-szerű indulási táblával. */
@Composable
private fun StopBoard(stop: Dk3.NearbyStop, now: LocalTime, modifier: Modifier) {
    GlassCard(modifier.fillMaxWidth(), contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stop.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Row(
                Modifier.clip(RoundedCornerShape(99.dp)).background(NightSurface).padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Rounded.DirectionsWalk, null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                Text("${stop.distanceM.toInt()} m", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
        Spacer(Modifier.height(10.dp))
        Column(
            Modifier.fillMaxWidth().ledPanel(10.dp).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            stop.departures.forEach { d ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LineBadge(d.line, size = 28.dp)
                    LedText(d.headsign, fontSize = 19.sp, maxLines = 1, modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
                    Box(Modifier.width(52.dp), contentAlignment = Alignment.CenterEnd) {
                        LedText(minutesLabel(d.time, now), fontSize = 24.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** „most”, „5'”, vagy pontos idő, ha már elment (a következő frissítésig). */
private fun minutesLabel(dep: LocalTime, now: LocalTime): String {
    var mins = Duration.between(now, dep).toMinutes()
    if (mins < -720) mins += 1440 // éjfélen átnyúló indulás
    return when {
        mins < 0 -> "%02d:%02d".format(dep.hour, dep.minute)
        mins == 0L -> "most"
        else -> "$mins'"
    }
}
