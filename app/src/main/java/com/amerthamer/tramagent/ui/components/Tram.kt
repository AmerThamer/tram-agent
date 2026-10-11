package com.amerthamer.tramagent.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.amerthamer.tramagent.ui.theme.Night
import com.amerthamer.tramagent.ui.theme.TextSecondary
import com.amerthamer.tramagent.ui.theme.TramYellow

/** Beálló szerelvény: gyorsan érkezik, finoman fékez. */
val TramArriveEasing = CubicBezierEasing(0.15f, 0.85f, 0.25f, 1f)

/** Induló szerelvény: lassan indul, majd felgyorsul. */
val TramDepartEasing = CubicBezierEasing(0.55f, 0f, 0.85f, 0.4f)

private val TramWidth = 86.dp
private val TramHeight = 26.dp

/**
 * Egy oldalnézeti villamos kirajzolása a (left, top) ponttól, [TramWidth] × [TramHeight] méretben.
 * A pantográf a tető közepéről a felsővezetékig ér ([wireY]).
 */
private fun DrawScope.drawTram(
    left: Float,
    top: Float,
    wireY: Float,
    color: Color,
    facingRight: Boolean = true,
    spark: Boolean = false,
) {
    val w = TramWidth.toPx()
    val h = TramHeight.toPx()
    val bodyTop = top + h * 0.27f
    val bodyH = h * 0.58f
    val dark = Night.copy(alpha = 0.8f)

    scale(scaleX = if (facingRight) 1f else -1f, scaleY = 1f, pivot = Offset(left + w / 2, top + h / 2)) {
        // Pantográf: rombusz alakú áramszedő a vezetékig
        val px = left + w * 0.5f
        val stroke = 1.5.dp.toPx()
        drawLine(color, Offset(px - w * 0.08f, bodyTop), Offset(px, wireY), stroke)
        drawLine(color, Offset(px + w * 0.08f, bodyTop), Offset(px, wireY), stroke)
        drawLine(color, Offset(px - w * 0.07f, wireY), Offset(px + w * 0.07f, wireY), stroke)
        if (spark) {
            drawCircle(Color.White, radius = 2.5.dp.toPx(), center = Offset(px, wireY))
            drawCircle(Color(0xFF8FD3FF).copy(alpha = 0.6f), radius = 5.dp.toPx(), center = Offset(px, wireY))
        }

        // Kocsiszekrény
        drawRoundRect(color, Offset(left, bodyTop), Size(w, bodyH), CornerRadius(h * 0.22f))

        // Ablakok
        val winTop = bodyTop + bodyH * 0.18f
        val winH = bodyH * 0.38f
        val windows = listOf(0.07f to 0.12f, 0.24f to 0.12f, 0.41f to 0.17f, 0.63f to 0.12f, 0.79f to 0.15f)
        windows.forEach { (x, ww) ->
            drawRoundRect(dark, Offset(left + w * x, winTop), Size(w * ww, winH), CornerRadius(2.dp.toPx()))
        }

        // Fényszóró elöl
        drawCircle(Color.White, radius = 1.6.dp.toPx(), center = Offset(left + w * 0.975f, bodyTop + bodyH * 0.78f))

        // Kerekek
        val wheelY = bodyTop + bodyH + 1.dp.toPx()
        drawCircle(Color(0xFF333333), radius = 3.dp.toPx(), center = Offset(left + w * 0.18f, wheelY))
        drawCircle(Color(0xFF333333), radius = 3.dp.toPx(), center = Offset(left + w * 0.82f, wheelY))
    }
}

/**
 * Felsővezeték + sín, rajta a villamos: begurul, megáll a „megállóban”, majd továbbmegy. Végtelenítve.
 */
@Composable
fun TramTrack(
    modifier: Modifier = Modifier,
    color: Color = TramYellow,
    periodMillis: Int = 7000,
) {
    val transition = rememberInfiniteTransition(label = "track")
    val f by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = periodMillis
                0f at 0 using TramArriveEasing
                0.5f at (periodMillis * 0.4f).toInt()
                0.5f at (periodMillis * 0.6f).toInt() using TramDepartEasing
                1f at periodMillis
            }
        ),
        label = "tramPos",
    )
    // Szikra a vezetéken – csak mozgás közben, rövid felvillanásokkal
    val sparkClock by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "spark",
    )

    Canvas(modifier.fillMaxWidth().height(36.dp)) {
        val wireY = 3.dp.toPx()
        val railY = size.height - 4.dp.toPx()
        drawLine(Color.White.copy(alpha = 0.25f), Offset(0f, wireY), Offset(size.width, wireY), 1.5.dp.toPx())
        drawLine(color.copy(alpha = 0.5f), Offset(0f, railY), Offset(size.width, railY), 3.dp.toPx())

        val tw = TramWidth.toPx()
        val x = -tw + f * (size.width + tw)
        val moving = f !in 0.49f..0.51f
        drawTram(
            left = x,
            top = railY - TramHeight.toPx() - 1.dp.toPx(),
            wireY = wireY,
            color = color,
            spark = moving && sparkClock in 0.0f..0.06f,
        )
    }
}

/**
 * Töltésjelző: a villamos ingázik a sínen két végállomás között (mindig előre néz).
 */
@Composable
fun TramLoader(
    modifier: Modifier = Modifier,
    text: String = "Villamos érkezik…",
    trackWidth: Dp = 220.dp,
) {
    val transition = rememberInfiniteTransition(label = "loader")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    Column(modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Canvas(Modifier.fillMaxWidth().height(40.dp)) {
            val usable = trackWidth.toPx().coerceAtMost(size.width)
            val start = (size.width - usable) / 2
            val wireY = 3.dp.toPx()
            val railY = size.height - 4.dp.toPx()
            drawLine(Color.White.copy(alpha = 0.25f), Offset(start, wireY), Offset(start + usable, wireY), 1.5.dp.toPx())
            drawLine(TramYellow.copy(alpha = 0.5f), Offset(start, railY), Offset(start + usable, railY), 3.dp.toPx())
            // Végállomás-jelek
            listOf(start, start + usable).forEach {
                drawLine(TramYellow, Offset(it, railY - 8.dp.toPx()), Offset(it, railY + 2.dp.toPx()), 2.dp.toPx())
            }

            val forward = phase < 1f
            val local = if (forward) phase else phase - 1f
            val eased = TramArriveEasing.transform(local.coerceIn(0f, 1f))
            val p = if (forward) eased else 1f - eased
            val tw = TramWidth.toPx()
            drawTram(
                left = start + p * (usable - tw),
                top = railY - TramHeight.toPx() - 1.dp.toPx(),
                wireY = wireY,
                color = TramYellow,
                facingRight = forward,
            )
        }
        if (text.isNotEmpty()) Text(text, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
}

/**
 * Haladásjelző: a villamos a [progress] (0..1) arányában jár a két végállomás között,
 * a megtett szakasz sárgán világít. Ha [progress] null, ingázó töltésjelzőként viselkedik.
 */
@Composable
fun TramProgress(progress: Float?, modifier: Modifier = Modifier) {
    if (progress == null) {
        TramLoader(modifier, text = "")
        return
    }
    val animated by androidx.compose.animation.core.animateFloatAsState(
        progress.coerceIn(0f, 1f), tween(400), label = "progress",
    )
    Canvas(modifier.fillMaxWidth().height(44.dp)) {
        val wireY = 3.dp.toPx()
        val railY = size.height - 4.dp.toPx()
        val tw = TramWidth.toPx()
        val x = animated * (size.width - tw)
        drawLine(Color.White.copy(alpha = 0.25f), Offset(0f, wireY), Offset(size.width, wireY), 1.5.dp.toPx())
        drawLine(TramYellow.copy(alpha = 0.2f), Offset(0f, railY), Offset(size.width, railY), 3.dp.toPx())
        drawLine(TramYellow, Offset(0f, railY), Offset(x + tw / 2, railY), 3.dp.toPx())
        listOf(0f, size.width).forEach {
            drawLine(TramYellow, Offset(it, railY - 8.dp.toPx()), Offset(it, railY + 2.dp.toPx()), 2.dp.toPx())
        }
        drawTram(left = x, top = railY - TramHeight.toPx() - 1.dp.toPx(), wireY = wireY, color = TramYellow)
    }
}
