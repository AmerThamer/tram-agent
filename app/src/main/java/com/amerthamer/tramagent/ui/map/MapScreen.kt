package com.amerthamer.tramagent.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.Tram
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amerthamer.tramagent.BuildConfig
import com.amerthamer.tramagent.R
import com.amerthamer.tramagent.domain.Dk5
import com.amerthamer.tramagent.ui.components.DelayChip
import com.amerthamer.tramagent.ui.components.LedMarquee
import com.amerthamer.tramagent.ui.components.LineBadge
import com.amerthamer.tramagent.ui.components.NightBox
import com.amerthamer.tramagent.ui.components.TramButton
import com.amerthamer.tramagent.ui.components.TramLoader
import com.amerthamer.tramagent.ui.components.TramTextField
import com.amerthamer.tramagent.ui.components.delayLabel
import com.amerthamer.tramagent.ui.theme.Night
import com.amerthamer.tramagent.ui.theme.NightOutline
import com.amerthamer.tramagent.ui.theme.TextSecondary
import com.amerthamer.tramagent.ui.theme.TramYellow
import com.amerthamer.tramagent.ui.tracking.TrackingViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val Budapest = LatLng(47.4979, 19.0402)
private val Hm = DateTimeFormatter.ofPattern("HH:mm")

/**
 * DK4 – Térképes kereső: egy jármű valós idejű pozíciója és a menetének megállói a térképen.
 * Ugyanazt a követő logikát használja, mint a DK5 (20 mp-enkénti frissítéssel).
 */
@Composable
fun MapScreen(
    initialVehicle: String?,
    onBack: () -> Unit,
    onTrack: (String) -> Unit,
    vm: TrackingViewModel = viewModel(),
) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    LaunchedEffect(initialVehicle) {
        if (initialVehicle != null && vm.state is TrackingViewModel.State.Idle) vm.track(initialVehicle)
    }
    LifecycleResumeEffect(Unit) {
        vm.resume()
        onPauseOrDispose { vm.pause() }
    }

    val tracking = (vm.state as? TrackingViewModel.State.Tracking)?.data
    val vehiclePos = tracking?.vehicle?.let { v -> if (v.lat != null && v.lon != null) LatLng(v.lat, v.lon) else null }

    val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(Budapest, 12f) }
    // Új menetnél a kamera a járműhöz repül
    LaunchedEffect(tracking?.trip?.tripId) {
        vehiclePos?.let { camera.animate(CameraUpdateFactory.newLatLngZoom(it, 15f), 1200) }
    }

    NightBox {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = camera,
            properties = remember { MapProperties(mapStyleOptions = MapStyleOptions.loadRawResourceStyle(context, R.raw.map_night)) },
            uiSettings = remember { MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false) },
            contentPadding = PaddingValues(top = 110.dp, bottom = 220.dp),
        ) {
            if (tracking != null) TripOverlay(tracking)
            if (vehiclePos != null && tracking != null) {
                val markerState = rememberMarkerState(position = vehiclePos)
                markerState.position = vehiclePos
                MarkerComposable(
                    keys = arrayOf(tracking.trip.line),
                    state = markerState,
                    title = "${tracking.trip.line} → ${tracking.trip.headsign}",
                    zIndex = 10f,
                ) { TramMarker() }
            }
        }

        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp)) {
            // Felső kereső sáv
            Row(
                Modifier.fillMaxWidth().mapPanel().padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Vissza") }
                TramTextField(
                    value = vm.query,
                    onValueChange = { vm.query = it },
                    label = "Pályaszám",
                    modifier = Modifier.weight(1f),
                    onSubmit = { focus.clearFocus(); vm.track() },
                )
                IconButton(
                    onClick = { focus.clearFocus(); vm.track() },
                    enabled = vm.query.isNotBlank(),
                    colors = IconButtonDefaults.iconButtonColors(containerColor = TramYellow, contentColor = Night),
                    modifier = Modifier.padding(start = 6.dp),
                ) { Icon(Icons.Rounded.Search, "Keresés") }
            }

            Spacer(Modifier.weight(1f))

            if (!BuildConfig.HAS_MAPS_KEY) {
                LedMarquee(
                    "★ Nincs Google Maps kulcs – a térkép nem töltődik be (local.properties → maps.apiKey) ★",
                    Modifier.padding(bottom = 8.dp),
                )
            }

            // Alsó információs kártya
            AnimatedContent(
                targetState = vm.state,
                contentKey = { it::class },
                transitionSpec = { (slideInVertically(tween(450)) { it / 2 } + fadeIn()) togetherWith fadeOut(tween(150)) },
                label = "card",
            ) { state ->
                Column(Modifier.fillMaxWidth().mapPanel().padding(16.dp)) {
                    when (state) {
                        TrackingViewModel.State.Idle -> Hint("Írd be egy villamos pályaszámát, és megmutatom, hol jár.")
                        TrackingViewModel.State.Loading -> TramLoader(Modifier.fillMaxWidth(), text = "Jármű keresése…")
                        TrackingViewModel.State.NotFound -> Hint("A(z) „${vm.query}” pályaszámú jármű most nem közlekedik.")
                        is TrackingViewModel.State.Error -> Hint(state.message)
                        is TrackingViewModel.State.Tracking -> VehicleInfo(state.data) { onTrack(vm.query) }
                    }
                }
            }
        }
    }
}

/** Átlátszatlanabb panel, hogy a térkép fölött is olvasható legyen. */
private fun Modifier.mapPanel(): Modifier = this
    .clip(RoundedCornerShape(20.dp))
    .background(Night.copy(alpha = 0.93f))
    .border(1.dp, NightOutline, RoundedCornerShape(20.dp))

@Composable
private fun Hint(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Tram, null, tint = TramYellow)
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun VehicleInfo(t: Dk5.Tracking, onTrack: () -> Unit) {
    val next = t.trip.stops.getOrNull(t.nextIndex)
    Row(verticalAlignment = Alignment.CenterVertically) {
        LineBadge(t.trip.line, size = 42.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = TramYellow, modifier = Modifier.size(16.dp))
                Text(t.trip.headsign, style = MaterialTheme.typography.titleMedium, maxLines = 1,
                    modifier = Modifier.padding(start = 4.dp))
            }
            Text("Pályaszám: ${t.vehicle.licensePlate ?: "?"} · ${delayLabel(t.currentDelaySec)}",
                style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        DelayChip(t.currentDelaySec, large = true)
    }
    if (next != null) {
        Spacer(Modifier.height(10.dp))
        Text(
            (if (t.vehicle.stoppedAtStop) "Most: " else "Következő: ") + next.name +
                (next.bestTime?.let { " · " + Hm.format(Instant.ofEpochSecond(it).atZone(ZoneId.of("Europe/Budapest"))) } ?: ""),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    Spacer(Modifier.height(12.dp))
    TramButton("Részletes nyomkövetés", onClick = onTrack, icon = Icons.Rounded.Timeline)
}

/** A menet útvonala (megtett szakasz halványan) és a megállók. */
@Composable
@com.google.maps.android.compose.GoogleMapComposable
private fun TripOverlay(t: Dk5.Tracking) {
    val pts = t.trip.stops.mapNotNull { s -> if (s.lat != null && s.lon != null) LatLng(s.lat, s.lon) else null }
    if (pts.size < 2) return
    val split = t.nextIndex.coerceIn(0, pts.lastIndex)
    val density = LocalDensity.current.density
    Polyline(points = pts.take(split + 1), color = TramYellow.copy(alpha = 0.35f), width = 5f * density)
    Polyline(points = pts.drop((split - 1).coerceAtLeast(0)), color = TramYellow, width = 6f * density)

    val dot = remember { stopDot(density) }
    t.trip.stops.forEach { s ->
        if (s.lat == null || s.lon == null) return@forEach
        val time = s.bestTime?.let { Hm.format(Instant.ofEpochSecond(it).atZone(ZoneId.of("Europe/Budapest"))) }
        Marker(
            state = rememberMarkerState(key = s.stopId, position = LatLng(s.lat, s.lon)),
            title = s.name,
            snippet = time?.let { (if (s.predicted != null) "Várható érkezés: " else "Menetrendi érkezés: ") + it },
            icon = dot,
            anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
        )
    }
}

/** Kis megállópont (sötét kitöltés, sárga keret) bitmapként. */
private fun stopDot(density: Float): BitmapDescriptor {
    val size = (16 * density).toInt()
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val r = size / 2f
    c.drawCircle(r, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TramYellow.toArgb() })
    c.drawCircle(r, r, r * 0.55f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Night.toArgb() })
    return BitmapDescriptorFactory.fromBitmap(bmp)
}

@Composable
private fun TramMarker() {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(TramYellow).border(3.dp, Night, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Tram, null, tint = Night, modifier = Modifier.size(24.dp))
    }
}
