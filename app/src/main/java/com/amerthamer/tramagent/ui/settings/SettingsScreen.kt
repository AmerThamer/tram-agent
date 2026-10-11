package com.amerthamer.tramagent.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amerthamer.tramagent.data.AlertsRepository
import com.amerthamer.tramagent.data.ApiKeyStore
import com.amerthamer.tramagent.data.BkkApi
import com.amerthamer.tramagent.ui.components.GlassCard
import com.amerthamer.tramagent.ui.components.LedText
import com.amerthamer.tramagent.ui.components.TramButton
import com.amerthamer.tramagent.ui.components.TramLoader
import com.amerthamer.tramagent.ui.components.TramScreen
import com.amerthamer.tramagent.ui.components.TramTextField
import com.amerthamer.tramagent.ui.components.ledPanel
import com.amerthamer.tramagent.ui.theme.LedAmber
import com.amerthamer.tramagent.ui.theme.OnTimeGreen
import com.amerthamer.tramagent.ui.theme.TextSecondary
import com.amerthamer.tramagent.ui.theme.TramYellow
import kotlinx.coroutines.launch

private val LedRed = androidx.compose.ui.graphics.Color(0xFFFF5A4E)

private sealed interface Check {
    data object None : Check
    data object Running : Check
    data object Ok : Check
    /** A kulcsot a szerver elutasította – nem mentjük el. */
    data class Rejected(val message: String) : Check
    /** Hálózati hiba – a kulcsot elmentjük, de nem tudtuk ellenőrizni. */
    data class Unverified(val message: String) : Check
}

/** Beállítások – a BKK API-kulcs megadása az appon belül. */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val userKey by ApiKeyStore.userKey.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf(userKey.orEmpty()) }
    var visible by rememberSaveable { mutableStateOf(false) }
    var check by remember { mutableStateOf<Check>(Check.None) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val uri = LocalUriHandler.current

    /**
     * Próbahívás az új kulccsal (a 4-es villamos járművei); csak működő kulcsot mentünk el.
     * Ha a szerver nem érhető el, elmentjük, de jelezzük, hogy nem sikerült ellenőrizni.
     */
    fun verifyAndSave(candidate: String) {
        check = Check.Running
        scope.launch {
            check = try {
                BkkApi.getJson("${BkkApi.GO_BASE}/vehicles-for-route?routeId=BKK_3040&version=2&dialect=otp", key = candidate)
                ApiKeyStore.setUserKey(candidate)
                Check.Ok
            } catch (e: BkkApi.ApiException) {
                if (e.code != null) Check.Rejected(e.message ?: "Hibás kulcs")
                else { ApiKeyStore.setUserKey(candidate); Check.Unverified(e.message ?: "Hálózati hiba") }
            } catch (e: Exception) {
                ApiKeyStore.setUserKey(candidate)
                Check.Unverified(e.message ?: "Hálózati hiba")
            }
            AlertsRepository.invalidate()
        }
    }

    TramScreen(kicker = "Beállítások", title = "BKK API-kulcs", onBack = onBack) {
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(Modifier.fillMaxWidth().ledPanel(12.dp).padding(horizontal = 14.dp, vertical = 10.dp)) {
                    when {
                        userKey != null -> {
                            LedText("Kulcs: appban megadva", fontSize = 26.sp, color = OnTimeGreen)
                            LedText("••••" + userKey!!.takeLast(4), fontSize = 18.sp, color = OnTimeGreen.copy(alpha = 0.7f))
                        }
                        ApiKeyStore.buildKey != null -> {
                            LedText("Kulcs: buildből", fontSize = 26.sp)
                            LedText("local.properties → bkk.apiKey", fontSize = 18.sp, color = LedAmber.copy(alpha = 0.6f))
                        }
                        else -> {
                            LedText("Nincs kulcs", fontSize = 26.sp, color = LedRed)
                            LedText("A valós idejű adatok nem érhetők el", fontSize = 18.sp, color = LedRed.copy(alpha = 0.7f))
                        }
                    }
                }
            }
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("Mire kell?", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "A FUTÁR valós idejű adataihoz (pályaszám kereső, zavarok, térkép, nyomkövetés) " +
                            "saját BKK API-kulcs kell, amit ingyen lehet igényelni a BKK nyílt adat portálján. " +
                            "A kulcs csak ezen a készüléken tárolódik.",
                        style = MaterialTheme.typography.bodyMedium, color = TextSecondary,
                    )
                    TextButton(onClick = { uri.openUri("https://opendata.bkk.hu/") }, contentPadding = PaddingValues(0.dp)) {
                        Text("opendata.bkk.hu megnyitása", color = TramYellow)
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, tint = TramYellow, modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
            item {
                TramTextField(
                    value = input,
                    onValueChange = { input = it.trim(); check = Check.None },
                    label = "BKK API-kulcs",
                    placeholder = "pl. 1a2b3c4d-…",
                    leadingIcon = Icons.Rounded.Key,
                    capitalization = KeyboardCapitalization.None,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailing = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(
                                if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (visible) "Elrejtés" else "Megjelenítés",
                            )
                        }
                    },
                    onSubmit = { focus.clearFocus() },
                )
            }
            item {
                TramButton(
                    "Ellenőrzés és mentés",
                    onClick = {
                        focus.clearFocus()
                        verifyAndSave(input)
                    },
                    icon = Icons.Rounded.VerifiedUser,
                    enabled = input.isNotBlank() && check != Check.Running,
                )
            }
            item {
                when (val c = check) {
                    Check.None -> Unit
                    Check.Running -> TramLoader(Modifier.fillMaxWidth(), text = "Kulcs ellenőrzése…")
                    Check.Ok -> Text("✓ A kulcs működik és elmentettem. A valós idejű adatok elérhetők.", color = OnTimeGreen,
                        style = MaterialTheme.typography.titleMedium)
                    is Check.Rejected -> Text("A BKK elutasította a kulcsot (${c.message}), ezért nem mentettem el.",
                        color = LedRed, style = MaterialTheme.typography.titleMedium)
                    is Check.Unverified -> Text("Elmentettem, de most nem tudtam ellenőrizni: ${c.message}",
                        color = LedAmber, style = MaterialTheme.typography.titleMedium)
                }
            }
            if (userKey != null) {
                item {
                    TextButton(onClick = {
                        ApiKeyStore.setUserKey(null)
                        AlertsRepository.invalidate()
                        input = ""
                        check = Check.None
                    }) { Text("Megadott kulcs törlése", color = TextSecondary) }
                }
            }
        }
    }
}
