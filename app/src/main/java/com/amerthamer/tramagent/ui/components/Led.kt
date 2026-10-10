package com.amerthamer.tramagent.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amerthamer.tramagent.ui.theme.LedAmber
import com.amerthamer.tramagent.ui.theme.LedFont
import com.amerthamer.tramagent.ui.theme.LedPanel
import com.amerthamer.tramagent.ui.theme.LedPanelDot
import com.amerthamer.tramagent.ui.theme.Night
import com.amerthamer.tramagent.ui.theme.TramYellow
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.ZoneId

/** Pontmátrix szöveg izzó borostyán fénnyel, mint az utastájékoztató kijelzőkön. */
@Composable
fun LedText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 22.sp,
    color: Color = LedAmber,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        maxLines = maxLines,
        style = TextStyle(
            fontFamily = LedFont,
            fontSize = fontSize,
            letterSpacing = 1.sp,
            color = color,
            shadow = Shadow(color = color.copy(alpha = 0.7f), blurRadius = 14f),
        ),
    )
}

/** Fekete kijelzőpanel finom pontrácsos háttérrel. */
fun Modifier.ledPanel(corner: Dp = 10.dp): Modifier = this
    .clip(RoundedCornerShape(corner))
    .background(LedPanel)
    .drawBehind {
        val step = 4.dp.toPx()
        val r = 0.6.dp.toPx()
        var y = step / 2
        while (y < size.height) {
            var x = step / 2
            while (x < size.width) {
                drawCircle(LedPanelDot, r, Offset(x, y))
                x += step
            }
            y += step
        }
    }
    .border(1.dp, Color(0xFF2A2A2A), RoundedCornerShape(corner))

/** Futószöveges kijelzősáv (pl. zavarok, GTFS-állapot). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LedMarquee(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().ledPanel().padding(horizontal = 10.dp, vertical = 4.dp)) {
        LedText(
            text = text,
            maxLines = 1,
            fontSize = 20.sp,
            modifier = Modifier.basicMarquee(
                iterations = Int.MAX_VALUE,
                initialDelayMillis = 600,
                spacing = MarqueeSpacing(48.dp),
                velocity = 45.dp,
            ),
        )
    }
}

/** Élő óra villogó kettősponttal. */
@Composable
fun LedClock(modifier: Modifier = Modifier, fontSize: TextUnit = 34.sp) {
    var now by remember { mutableStateOf(LocalTime.now(ZoneId.of("Europe/Budapest"))) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now(ZoneId.of("Europe/Budapest"))
            delay(500)
        }
    }
    val colon = if (now.second % 2 == 0) ":" else " "
    Box(modifier.ledPanel().padding(horizontal = 10.dp, vertical = 2.dp)) {
        LedText("%02d%s%02d".format(now.hour, colon, now.minute), fontSize = fontSize)
    }
}

/** Kijelzős sorszám-/kódcella (pl. „01”, pályaszám). */
@Composable
fun LedCell(text: String, modifier: Modifier = Modifier, size: Dp = 46.dp) {
    Box(modifier.size(size).ledPanel(8.dp), contentAlignment = Alignment.Center) {
        LedText(text, fontSize = 28.sp, maxLines = 1)
    }
}

/** BKK-stílusú villamos vonaljelzés: sárga lekerekített négyzet, fekete félkövér szám. */
@Composable
fun LineBadge(line: String, modifier: Modifier = Modifier, size: Dp = 34.dp) {
    Box(
        modifier
            .size(width = if (line.length > 2) size * 1.35f else size, height = size)
            .clip(RoundedCornerShape(8.dp))
            .background(TramYellow),
        contentAlignment = Alignment.Center,
    ) {
        Text(line, color = Night, fontWeight = FontWeight.ExtraBold, fontSize = (size.value * 0.48f).sp)
    }
}
