package com.amerthamer.tramagent.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amerthamer.tramagent.data.BkkApi
import com.amerthamer.tramagent.domain.Dk5
import com.amerthamer.tramagent.ui.components.DelayChip
import com.amerthamer.tramagent.ui.components.LineBadge
import com.amerthamer.tramagent.ui.components.NightBox
import com.amerthamer.tramagent.ui.components.TramButton
import com.amerthamer.tramagent.ui.components.TramLoader
import com.amerthamer.tramagent.ui.components.TramTextField
import com.amerthamer.tramagent.ui.components.delayLabel
import com.amerthamer.tramagent.ui.theme.Night
import com.amerthamer.tramagent.ui.theme.NightHigh
import com.amerthamer.tramagent.ui.theme.NightOutline
import com.amerthamer.tramagent.ui.theme.TextSecondary
import com.amerthamer.tramagent.ui.theme.TramYellow
import com.amerthamer.tramagent.ui.tracking.TrackingViewModel
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val Budapest = GeoPoint(47.4979, 19.0402)
private val Hm = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Éjszakai színszűrő az OpenStreetMap csempékhez: szürkeárnyalat → invertálás → éjkék tónus,
 * így a hivatalos OSM térkép (kulcs nélkül) illeszkedik az app sötét dizájnjához.
 */
private val NightTileFilter = ColorMatrixColorFilter(
    ColorMatrix().apply {
        setSaturation(0f)
        postConcat(ColorMatrix(floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        )))
        // Éjkék tónus és visszafogott kontraszt (a legsötétebb pont ≈ az app háttere)
        postConcat(ColorMatrix(floatArrayOf(
            0.42f, 0f, 0f, 0f, 11f,
            0f, 0.50f, 0f, 0f, 20f,
            0f, 0f, 0.66f, 0f, 38f,
            0f, 0f, 0f, 1f, 0f,
        )))
    }
)

/**
 * DK4 – Térképes kereső: egy jármű valós idejű pozíciója és a menetének megállói
 * OpenStreetMap térképen (osmdroid, API-kulcs nélkül; a forrásmegjelölés kötelező – CopyrightOverlay). Ugyanazt a követő logikát használja, mint a DK5 (20 mp-enkénti frissítéssel).
 */
@Composable
fun MapScreen(
    initialVehicle: String?,
    onBack: () -> Unit,
    onTrack: (String) -> Unit,
    onOpenSettings: () -> Unit,
    vm: TrackingViewModel = viewModel(),
) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    LaunchedEffect(initialVehicle) {
        if (initialVehicle != null && vm.state is TrackingViewModel.State.Idle) vm.track(initialVehicle)
    }

    val mapView = remember { createMapView(context) }
    val icons = remember { MapIcons(context) }
    LifecycleResumeEffect(Unit) {
        vm.resume()
        mapView.onResume()
        onPauseOrDispose {
            vm.pause()
            mapView.onPause()
        }
    }
    DisposableEffect(Unit) { onDispose { mapView.onDetach() } }

    val tracking = (vm.state as? TrackingViewModel.State.Tracking)?.data

    // Új menetnél a térkép a járműhöz repül
    LaunchedEffect(tracking?.trip?.tripId) {
        tracking?.vehiclePoint()?.let { mapView.controller.animateTo(it, 15.5, 1200L) }
    }

    NightBox {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { map -> renderTracking(map, tracking, icons) },
        )

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

            // Alsó információs kártya (a jobb alsó forrásmegjelölés fölött)
            AnimatedContent(
                targetState = vm.state,
                contentKey = { it::class },
                transitionSpec = { (slideInVertically(tween(450)) { it / 2 } + fadeIn()) togetherWith fadeOut(tween(150)) },
                label = "card",
                modifier = Modifier.padding(bottom = 14.dp),
            ) { state ->
                Column(Modifier.fillMaxWidth().mapPanel().padding(16.dp)) {
                    when (state) {
                        TrackingViewModel.State.Idle -> Hint("Írd be egy villamos pályaszámát, és megmutatom, hol jár.")
                        TrackingViewModel.State.Loading -> TramLoader(Modifier.fillMaxWidth(), text = "Jármű keresése…")
                        TrackingViewModel.State.NotFound -> Hint("A(z) „${vm.query}” pályaszámú jármű most nem közlekedik.")
                        is TrackingViewModel.State.Error -> {
                            Hint(state.message)
                            if (!BkkApi.hasApiKey) {
                                TextButton(onClick = onOpenSettings) { Text("Kulcs megadása", color = TramYellow) }
                            }
                        }
                        is TrackingViewModel.State.Tracking -> VehicleInfo(state.data) { onTrack(vm.query) }
                    }
                }
            }
        }
    }
}

private fun createMapView(context: Context) = MapView(context).apply {
    setTileSource(TileSourceFactory.MAPNIK)
    overlayManager.tilesOverlay.apply {
        setColorFilter(NightTileFilter)
        loadingBackgroundColor = Night.toArgb()
        loadingLineColor = NightHigh.toArgb()
    }
    setMultiTouchControls(true)
    isTilesScaledToDpi = true
    zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
    minZoomLevel = 10.0
    setBackgroundColor(Night.toArgb())
    controller.setZoom(12.5)
    controller.setCenter(Budapest)
    overlays += CopyrightOverlay(context).apply { setTextColor(TextSecondary.toArgb()) }
}

private fun Dk5.Tracking.vehiclePoint(): GeoPoint? =
    vehicle.lat?.let { lat -> vehicle.lon?.let { lon -> GeoPoint(lat, lon) } }

/** Útvonal (megtett szakasz halványan), megállók és a villamos kirajzolása; a régi rétegeket lecseréli. */
private fun renderTracking(map: MapView, t: Dk5.Tracking?, icons: MapIcons) {
    map.overlays.removeAll { it is Polyline || it is Marker }
    if (t != null) {
        val stops = t.trip.stops.filter { it.lat != null && it.lon != null }
        val pts = stops.map { GeoPoint(it.lat!!, it.lon!!) }
        val nextStopId = t.trip.stops.getOrNull(t.nextIndex)?.stopId
        val split = stops.indexOfFirst { it.stopId == nextStopId }.let { if (it < 0) 0 else it }
        val density = map.context.resources.displayMetrics.density

        if (pts.size >= 2) {
            map.overlays += Polyline(map).apply {
                setPoints(pts.take(split + 1))
                outlinePaint.color = TramYellow.copy(alpha = 0.35f).toArgb()
                outlinePaint.strokeWidth = 5f * density
                outlinePaint.strokeCap = Paint.Cap.ROUND
            }
            map.overlays += Polyline(map).apply {
                setPoints(pts.drop((split - 1).coerceAtLeast(0)))
                outlinePaint.color = TramYellow.toArgb()
                outlinePaint.strokeWidth = 6f * density
                outlinePaint.strokeCap = Paint.Cap.ROUND
            }
        }
        stops.forEachIndexed { i, s ->
            map.overlays += Marker(map).apply {
                position = pts[i]
                icon = icons.stop
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                title = s.name
                snippet = s.bestTime?.let {
                    (if (s.predicted != null) "Várható érkezés: " else "Menetrendi érkezés: ") +
                        Hm.format(Instant.ofEpochSecond(it).atZone(ZoneId.of("Europe/Budapest")))
                }
            }
        }
        t.vehiclePoint()?.let { p ->
            map.overlays += Marker(map).apply {
                position = p
                icon = icons.tram
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                title = "${t.trip.line} → ${t.trip.headsign}"
                snippet = "Pályaszám: ${t.vehicle.licensePlate ?: "?"}"
            }
        }
    }
    map.invalidate()
}

/** A térképjelölők bitmapjei (egyszer rajzolva). */
private class MapIcons(context: Context) {
    private val d = context.resources.displayMetrics.density
    private val res = context.resources

    /** Kis megállópont: sárga gyűrű sötét közepű. */
    val stop: BitmapDrawable = draw(16) { c, s ->
        val r = s / 2f
        c.drawCircle(r, r, r, paint(TramYellow.toArgb()))
        c.drawCircle(r, r, r * 0.55f, paint(Night.toArgb()))
    }

    /** Villamos-jelölő: sárga kör sötét kerettel, benne stilizált villamos elölnézetből. */
    val tram: BitmapDrawable = draw(42) { c, s ->
        val r = s / 2f
        c.drawCircle(r, r, r, paint(Night.toArgb()))
        c.drawCircle(r, r, r - 3 * d, paint(TramYellow.toArgb()))
        val dark = paint(Night.toArgb())
        // áramszedő
        c.drawRect(r - 0.8f * d, s * 0.18f, r + 0.8f * d, s * 0.30f, dark)
        c.drawRect(r - 5 * d, s * 0.18f, r + 5 * d, s * 0.18f + 1.6f * d, dark)
        // kocsiszekrény + szélvédő
        c.drawRoundRect(RectF(s * 0.30f, s * 0.30f, s * 0.70f, s * 0.74f), 4 * d, 4 * d, dark)
        c.drawRoundRect(RectF(s * 0.35f, s * 0.35f, s * 0.65f, s * 0.52f), 2 * d, 2 * d, paint(TramYellow.toArgb()))
        c.drawCircle(s * 0.38f, s * 0.65f, 1.6f * d, paint(android.graphics.Color.WHITE))
        c.drawCircle(s * 0.62f, s * 0.65f, 1.6f * d, paint(android.graphics.Color.WHITE))
    }

    private fun paint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    private fun draw(sizeDp: Int, block: (Canvas, Float) -> Unit): BitmapDrawable {
        val px = (sizeDp * d).toInt()
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        block(Canvas(bmp), px.toFloat())
        return BitmapDrawable(res, bmp)
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
