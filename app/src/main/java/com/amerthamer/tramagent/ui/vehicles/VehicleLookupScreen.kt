package com.amerthamer.tramagent.ui.vehicles

import android.app.Application
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amerthamer.tramagent.domain.Dk1
import com.amerthamer.tramagent.domain.TramLines
import com.amerthamer.tramagent.ui.components.GlassCard
import com.amerthamer.tramagent.ui.components.LedCell
import com.amerthamer.tramagent.ui.components.LineBadge
import com.amerthamer.tramagent.ui.components.TramButton
import com.amerthamer.tramagent.ui.components.TramLoader
import com.amerthamer.tramagent.ui.components.TramScreen
import com.amerthamer.tramagent.ui.components.TramTextField
import com.amerthamer.tramagent.ui.components.tramArrival
import com.amerthamer.tramagent.ui.theme.AlertRed
import com.amerthamer.tramagent.ui.theme.TextSecondary
import com.amerthamer.tramagent.ui.theme.TramYellow
import kotlinx.coroutines.launch

class VehicleLookupViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("dk1_prefs", Context.MODE_PRIVATE)

    /** Kiválasztott route ID-k; a választás megmarad a következő megnyitásig. */
    var selected by mutableStateOf(prefs.getStringSet(KEY_SELECTED, emptySet()).orEmpty().toSet())
        private set
    var manual by mutableStateOf("")
    var results by mutableStateOf<List<Dk1.LineResult>?>(null)
        private set
    var loading by mutableStateOf(false)
        private set

    fun toggle(routeId: String) {
        selected = if (routeId in selected) selected - routeId else selected + routeId
        prefs.edit().putStringSet(KEY_SELECTED, selected).apply()
    }

    fun clearSelection() {
        selected = emptySet()
        prefs.edit().remove(KEY_SELECTED).apply()
    }

    /** A lekérdezendő route ID-k a vonalak sorrendjében, a kézzel beírttal a végén. */
    private fun routeIds(): List<String> {
        val ordered = TramLines.all.map { it.routeId }.filter { it in selected }
        val extra = manual.trim().uppercase().takeIf { it.isNotEmpty() }
            ?.let { if (it.startsWith("BKK_")) it else "BKK_$it" }
        return ordered + listOfNotNull(extra)
    }

    val canQuery: Boolean get() = routeIds().isNotEmpty() && !loading

    fun query() {
        val ids = routeIds()
        if (ids.isEmpty()) return
        loading = true
        viewModelScope.launch {
            results = Dk1.query(ids)
            loading = false
        }
    }

    private companion object {
        const val KEY_SELECTED = "selected_routes"
    }
}

/** DK1 – Pályaszám kereső. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VehicleLookupScreen(
    onBack: () -> Unit,
    onTrack: (String) -> Unit,
    onShowOnMap: (String) -> Unit,
    vm: VehicleLookupViewModel = viewModel(),
) {
    val focus = LocalFocusManager.current
    TramScreen(kicker = "DK1 · FUTÁR", title = "Pályaszám kereső", onBack = onBack) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Villamosvonalak", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (vm.selected.isEmpty()) "Koppints a vonalakra" else "${vm.selected.size} kiválasztva",
                                style = MaterialTheme.typography.bodySmall, color = TextSecondary,
                            )
                        }
                        if (vm.selected.isNotEmpty()) {
                            TextButton(onClick = vm::clearSelection) { Text("Törlés", color = TramYellow) }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TramLines.all.forEach { line ->
                            LineBadge(
                                line.name,
                                size = 40.dp,
                                dimmed = line.routeId !in vm.selected,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.toggle(line.routeId) },
                            )
                        }
                    }
                }
            }
            item {
                TramTextField(
                    value = vm.manual,
                    onValueChange = { vm.manual = it },
                    label = "Egyéb viszonylat (route ID)",
                    placeholder = "pl. BKK_3040",
                    leadingIcon = Icons.Rounded.Route,
                    onSubmit = { focus.clearFocus(); vm.query() },
                )
            }
            item {
                TramButton(
                    "Járművek lekérése",
                    onClick = { focus.clearFocus(); vm.query() },
                    icon = Icons.Rounded.Search,
                    enabled = vm.canQuery,
                )
            }

            val results = vm.results
            when {
                vm.loading -> item { TramLoader(Modifier.fillMaxWidth(), text = "Járművek keresése…") }
                results != null -> results.forEach { r ->
                    item(key = "h_${r.routeId}") { LineHeader(r) }
                    items(r.vehicles.withIndex().toList(), key = { "${r.routeId}_${it.value.vehicle.vehicleId}_${it.index}" }) { (i, v) ->
                        VehicleCard(
                            v,
                            modifier = Modifier.tramArrival(i),
                            onTrack = { v.vehicle.licensePlate?.let(onTrack) },
                            onMap = { v.vehicle.licensePlate?.let(onShowOnMap) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LineHeader(r: Dk1.LineResult) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        LineBadge(r.line, size = 30.dp)
        Text(
            when {
                r.error != null -> r.error
                r.vehicles.isEmpty() -> "Most nem közlekedik jármű"
                else -> "${r.vehicles.size} jármű a vonalon"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (r.error != null) AlertRed else TextSecondary,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

@Composable
private fun VehicleCard(v: Dk1.LineVehicle, modifier: Modifier, onTrack: () -> Unit, onMap: () -> Unit) {
    GlassCard(modifier.fillMaxWidth(), onClick = onTrack, contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                LedCell(v.blockNo ?: "–", size = 52.dp)
                Text("forgalmi", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text("PÁLYASZÁM", style = MaterialTheme.typography.labelSmall, color = TramYellow)
                Text(v.vehicle.licensePlate ?: "ismeretlen", style = MaterialTheme.typography.headlineSmall)
                v.headsign?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null,
                            tint = TextSecondary, modifier = Modifier.size(14.dp),
                        )
                        Text(
                            it, style = MaterialTheme.typography.bodySmall, color = TextSecondary,
                            maxLines = 1, modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }
            if (v.vehicle.licensePlate != null) {
                IconButton(onClick = onMap) {
                    Icon(Icons.Rounded.Map, contentDescription = "Térképen", tint = TramYellow)
                }
            }
        }
    }
}
