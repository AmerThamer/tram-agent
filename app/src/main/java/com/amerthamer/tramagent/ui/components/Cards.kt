package com.amerthamer.tramagent.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.amerthamer.tramagent.ui.theme.AlertRed
import com.amerthamer.tramagent.ui.theme.Night
import com.amerthamer.tramagent.ui.theme.NightOutline
import com.amerthamer.tramagent.ui.theme.NightSurface
import com.amerthamer.tramagent.ui.theme.TramYellow
import com.amerthamer.tramagent.ui.theme.TramYellowDeep
import kotlinx.coroutines.delay

/**
 * „Beálló szerelvény” belépő animáció listaelemekhez: jobbról becsúszik és lefékez.
 * Az [index] alapján egymás után érkeznek az elemek.
 */
@Composable
fun Modifier.tramArrival(index: Int, distance: Dp = 60.dp): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 70L)
        progress.animateTo(1f, tween(700, easing = TramArriveEasing))
    }
    return graphicsLayer {
        translationX = (1f - progress.value) * distance.toPx()
        alpha = progress.value
    }
}

/** Üvegszerű kártya; kattintáskor enyhén „besüpped”. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    corner: Dp = 20.dp,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, label = "press")
    val shape = RoundedCornerShape(corner)

    Column(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(NightSurface)
            .border(1.dp, NightOutline, shape)
            .let { if (onClick != null) it.clickable(interaction, indication = null, onClick = onClick) else it }
            .padding(contentPadding),
        content = content,
    )
}

/** Sárga ikoncsempe a kártyák bal felső sarkába. */
@Composable
fun IconChip(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 42.dp, color: Color = TramYellow) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(12.dp)).background(color),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Night, modifier = Modifier.size(size * 0.55f))
    }
}

/** Sárga, pulzáló piros ponttal kiemelt figyelmeztető sáv (pl. aktív zavarok). */
@Composable
fun AlertBanner(text: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val p by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Restart), label = "p")
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.horizontalGradient(listOf(TramYellow, TramYellowDeep)))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(10.dp)
                .drawBehind {
                    drawCircle(AlertRed.copy(alpha = 0.7f * (1f - p)), radius = size.minDimension / 2 + 10.dp.toPx() * p)
                }
                .clip(CircleShape)
                .background(AlertRed)
        )
        Text(text, color = Night, style = MaterialTheme.typography.titleMedium)
    }
}
