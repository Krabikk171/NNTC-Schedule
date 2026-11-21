package ru.krabikk.nntcschedule.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.krabikk.nntcschedule.InterfaceStyle

/** Декор не перекрывает надписи: они находятся над непрозрачными карточками. */
@Composable
fun AppBackdrop(modifier: Modifier = Modifier) {
    val style = LocalInterfaceStyle.current
    val dark = LocalAppDarkTheme.current
    val base = MaterialTheme.colorScheme.background
    val (top, bottom) = when (style) {
        InterfaceStyle.AURORA -> if (dark) Color(0xFF0F172B) to Color(0xFF241B35)
                                 else Color(0xFFF0EFFC) to Color(0xFFF9F3FD)
        InterfaceStyle.NEON -> if (dark) Color(0xFF100A19) to Color(0xFF1D102A)
                               else Color(0xFFFCF5FF) to Color(0xFFF8EDF9)
        InterfaceStyle.LAVENDER -> if (dark) Color(0xFF1A1423) to Color(0xFF34233B)
                                   else Color(0xFFFFF8FC) to Color(0xFFF1E6F4)
        InterfaceStyle.OCEAN -> if (dark) Color(0xFF091A27) to Color(0xFF102F3C)
                                else Color(0xFFF3FAFC) to Color(0xFFE7F4F7)
        InterfaceStyle.ROUTE -> if (dark) Color(0xFF101831) to Color(0xFF091528)
                                else Color(0xFFFCF9FE) to Color(0xFFF3EFF8)
        InterfaceStyle.ROUTE_1 -> if (dark) Color(0xFF0B111A) to Color(0xFF1C1920)
                                  else Color(0xFFFFFBF4) to Color(0xFFF5EEE2)
        else -> base to base
    }
    val glow1 = when (style) {
        InterfaceStyle.AURORA -> if (dark) Color(0x554A8BFF) else Color(0x4495BCFF)
        InterfaceStyle.NEON -> if (dark) Color(0x557727D9) else Color(0x33DD8CFF)
        InterfaceStyle.LAVENDER -> if (dark) Color(0x558759B5) else Color(0x449A7FD2)
        InterfaceStyle.OCEAN -> if (dark) Color(0x5551A8CC) else Color(0x4458B7C7)
        InterfaceStyle.ROUTE -> if (dark) Color(0x444564BB) else Color(0x22948DBE)
        InterfaceStyle.ROUTE_1 -> if (dark) Color(0x55CE842A) else Color(0x22F3BA58)
        else -> Color.Transparent
    }
    val glow2 = when (style) {
        InterfaceStyle.AURORA -> if (dark) Color(0x55AD5CE6) else Color(0x44FA9FD6)
        InterfaceStyle.NEON -> if (dark) Color(0x55EF3DC8) else Color(0x33FF9BCD)
        InterfaceStyle.LAVENDER -> if (dark) Color(0x558A477A) else Color(0x44DA90C8)
        InterfaceStyle.OCEAN -> if (dark) Color(0x5531B1AD) else Color(0x4462DCD2)
        InterfaceStyle.ROUTE -> if (dark) Color(0x33D3268B) else Color(0x22E3A3C5)
        InterfaceStyle.ROUTE_1 -> if (dark) Color(0x447E4A13) else Color(0x22E5B768)
        else -> Color.Transparent
    }
    val spec = tween<Color>(durationMillis = 480, easing = FastOutSlowInEasing)
    val animatedTop by animateColorAsState(top, spec, label = "wallpaperTop")
    val animatedBottom by animateColorAsState(bottom, spec, label = "wallpaperBottom")
    val animatedGlow1 by animateColorAsState(glow1, spec, label = "wallpaperGlowA")
    val animatedGlow2 by animateColorAsState(glow2, spec, label = "wallpaperGlowB")
    val ribbonAlpha by animateFloatAsState(
        if (style == InterfaceStyle.MINIMAL) 0f else if (dark) .18f else .11f,
        tween(durationMillis = 480), label = "wallpaperRibbon"
    )
    Canvas(modifier) {
        drawRect(brush = Brush.verticalGradient(listOf(animatedTop, animatedBottom)))
        val w = size.width
        val h = size.height
        if (animatedGlow1.alpha > .005f) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(animatedGlow1, Color.Transparent),
                    center = Offset(w * .88f, h * .16f), radius = w * 1.1f
                ),
                radius = w * 1.1f,
                center = Offset(w * .88f, h * .16f)
            )
        }
        if (animatedGlow2.alpha > .005f) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(animatedGlow2, Color.Transparent),
                    center = Offset(w * .12f, h * .76f), radius = w * 1.2f
                ),
                radius = w * 1.2f,
                center = Offset(w * .12f, h * .76f)
            )
        }
        if (style == InterfaceStyle.ROUTE) {
            // Unobtrusive metro-map motif in the upper-right background.
            val routeMapColor = if (dark) Color(0xFF30547C) else Color(0xFFD3CBE1)
            val map = Path().apply {
                moveTo(w * .83f, 0f)
                cubicTo(w * .70f, h * .07f, w * 1.08f, h * .10f, w * .76f, h * .19f)
                lineTo(w * .96f, h * .25f)
            }
            drawPath(map, color = routeMapColor.copy(alpha = if (dark) .45f else .70f),
                style = Stroke(width = 3.5f, cap = StrokeCap.Round))
            val stops = listOf(Offset(w * .83f, h * .03f), Offset(w * .90f, h * .13f))
            stops.forEach { point ->
                drawCircle(routeMapColor, radius = 5f, center = point, style = Stroke(2.5f))
            }
        }
        if (ribbonAlpha > .005f) {
            val ribbon = Path().apply {
                moveTo(-w * .2f, h * .23f)
                cubicTo(w * .23f, h * .12f, w * .62f, h * .30f, w * 1.2f, h * .12f)
            }
            drawPath(
                path = ribbon,
                brush = Brush.linearGradient(listOf(Color.Transparent, animatedGlow2, animatedGlow1, Color.Transparent)),
                style = Stroke(width = w * .10f, cap = StrokeCap.Round),
                alpha = ribbonAlpha
            )
        }
    }
}

/** Непрозрачные карточки сохраняют контраст поверх цветных фоновых эффектов. */
@Composable
fun themedPanelColor(base: Color = MaterialTheme.colorScheme.surface): Color = base

/** Рамки отключены для всех пяти стилей. */
@Composable
fun themedPanelBorder(): BorderStroke? = null

/** Градиентные кнопки и очень мягкая тень: без ярких рамок. */
@Composable
fun themedActionModifier(radius: Dp = 18.dp): Modifier {
    val shape = RoundedCornerShape(radius)
    return when (LocalInterfaceStyle.current) {
        InterfaceStyle.AURORA -> Modifier
            .shadow(3.dp, shape, ambientColor = Color(0x334F4AA0), spotColor = Color(0x334F4AA0))
            .background(Brush.horizontalGradient(listOf(Color(0xFF4D47A7), Color(0xFF3C559D), Color(0xFF7640A4))), shape)
        InterfaceStyle.NEON -> Modifier
            .shadow(4.dp, shape, ambientColor = Color(0x55B42AA0), spotColor = Color(0x55B42AA0))
            .background(Brush.horizontalGradient(listOf(Color(0xFF712CBA), Color(0xFF971A87), Color(0xFF6630AD))), shape)
        InterfaceStyle.LAVENDER -> Modifier
            .shadow(3.dp, shape, ambientColor = Color(0x338453A6), spotColor = Color(0x338453A6))
            .background(Brush.horizontalGradient(listOf(Color(0xFF63408B), Color(0xFF89508E), Color(0xFF714B99))), shape)
        InterfaceStyle.OCEAN -> Modifier
            .shadow(3.dp, shape, ambientColor = Color(0x33006982), spotColor = Color(0x33006982))
            .background(Brush.horizontalGradient(listOf(Color(0xFF006982), Color(0xFF167D90), Color(0xFF215B8B))), shape)
        else -> Modifier
    }
}

/** Единый градиент активной даты и кнопки. */
@Composable
fun selectedDayGradient(): List<Color> = when (LocalInterfaceStyle.current) {
    InterfaceStyle.AURORA -> listOf(Color(0xFF4D47A7), Color(0xFF7640A4))
    InterfaceStyle.NEON -> listOf(Color(0xFF712CBA), Color(0xFF971A87))
    InterfaceStyle.LAVENDER -> listOf(Color(0xFF63408B), Color(0xFF89508E))
    InterfaceStyle.OCEAN -> listOf(Color(0xFF006982), Color(0xFF215B8B))
    else -> listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary)
}
