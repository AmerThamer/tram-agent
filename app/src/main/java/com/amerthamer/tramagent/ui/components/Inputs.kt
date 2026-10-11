package com.amerthamer.tramagent.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amerthamer.tramagent.domain.Futar
import com.amerthamer.tramagent.ui.theme.AlertRed
import com.amerthamer.tramagent.ui.theme.LateBlue
import com.amerthamer.tramagent.ui.theme.Night
import com.amerthamer.tramagent.ui.theme.NightOutline
import com.amerthamer.tramagent.ui.theme.NightSurface
import com.amerthamer.tramagent.ui.theme.OnTimeGreen
import com.amerthamer.tramagent.ui.theme.TextSecondary
import com.amerthamer.tramagent.ui.theme.TramYellow
import androidx.compose.ui.graphics.Color

/** Egysoros beviteli mező az app stílusában (üveg háttér, sárga fókusz). */
@Composable
fun TramTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    onSubmit: () -> Unit = {},
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = TextSecondary.copy(alpha = 0.6f)) } },
        leadingIcon = leadingIcon?.let { { Icon(it, contentDescription = null) } },
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            capitalization = KeyboardCapitalization.Characters,
            imeAction = ImeAction.Search,
        ),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = TramYellow,
            unfocusedBorderColor = NightOutline,
            focusedLabelColor = TramYellow,
            unfocusedLabelColor = TextSecondary,
            cursorColor = TramYellow,
            focusedContainerColor = NightSurface,
            unfocusedContainerColor = NightSurface,
            focusedLeadingIconColor = TramYellow,
            unfocusedLeadingIconColor = TextSecondary,
        ),
    )
}

/**
 * Késés-chip a FUTÁR fedélzeti kijelző színeivel:
 * siet (≤ −31 mp) piros, pontos (−30…+180 mp) zöld, késik (≥ +181 mp) kék.
 */
@Composable
fun DelayChip(delaySec: Int?, modifier: Modifier = Modifier, large: Boolean = false) {
    val (bg, fg) = when (delaySec?.let(Futar::classify)) {
        Futar.DelayClass.Early -> AlertRed to Color.White
        Futar.DelayClass.OnTime -> OnTimeGreen to Night
        Futar.DelayClass.Late -> LateBlue to Night
        null -> NightSurface to TextSecondary
    }
    Box(
        modifier
            .clip(RoundedCornerShape(if (large) 10.dp else 7.dp))
            .background(bg)
            .padding(horizontal = if (large) 12.dp else 7.dp, vertical = if (large) 2.dp else 0.dp),
    ) {
        Text(
            delaySec?.let(Futar::formatDelay) ?: "—",
            color = fg,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = com.amerthamer.tramagent.ui.theme.LedFont,
                fontSize = if (large) 30.sp else 18.sp,
            ),
        )
    }
}

/** Rövid szöveges értelmezés a késéshez. */
fun delayLabel(delaySec: Int?): String = when (delaySec?.let(Futar::classify)) {
    Futar.DelayClass.Early -> "Siet"
    Futar.DelayClass.OnTime -> "Menetrend szerint"
    Futar.DelayClass.Late -> "Késik"
    null -> "Nincs valós idejű adat"
}
