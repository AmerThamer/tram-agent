package com.amerthamer.tramagent.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.amerthamer.tramagent.ui.theme.Night
import com.amerthamer.tramagent.ui.theme.NightBackground
import com.amerthamer.tramagent.ui.theme.NightOutline
import com.amerthamer.tramagent.ui.theme.NightSurface
import com.amerthamer.tramagent.ui.theme.TextPrimary
import com.amerthamer.tramagent.ui.theme.TextSecondary
import com.amerthamer.tramagent.ui.theme.TramYellow

/** Az éjszakai háttér, ami minden képernyő alatt ott van. */
@Composable
fun NightBox(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Night).background(NightBackground)) {
        CompositionLocalProvider(LocalContentColor provides TextPrimary) { content() }
    }
}

/**
 * Egységes aloldal-váz: vissza gomb, sárga „kicker” felirat, nagy cím, alatta a villamos-sín,
 * majd a tartalom.
 */
@Composable
fun TramScreen(
    kicker: String,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    NightBox {
        Column(modifier.fillMaxSize().safeDrawingPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 16.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(42.dp).clip(CircleShape).background(NightSurface)
                        .border(1.dp, NightOutline, CircleShape),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Vissza")
                }
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(kicker.uppercase(), style = MaterialTheme.typography.labelSmall, color = TramYellow)
                    Text(title, style = MaterialTheme.typography.headlineSmall)
                }
                actions()
            }
            TramTrack(Modifier.padding(top = 6.dp))
            Column(Modifier.fillMaxSize().padding(horizontal = 18.dp), content = content)
        }
    }
}

/** Fő akciógomb: sárga, fekete félkövér felirattal. */
@Composable
fun TramButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = MaterialTheme.shapes.large,
        colors = ButtonDefaults.buttonColors(containerColor = TramYellow, contentColor = Night),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

/** Üres / hiba / „előbb töltsd le” állapot illusztrációval és opcionális gombbal. */
@Composable
fun StateMessage(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconChip(icon, size = 64.dp)
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, textAlign = TextAlign.Center)
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(4.dp))
            TramButton(actionText, onAction, Modifier.padding(horizontal = 32.dp))
        }
    }
}
