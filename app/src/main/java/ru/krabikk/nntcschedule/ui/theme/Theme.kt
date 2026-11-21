package ru.krabikk.nntcschedule.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.compositionLocalOf
import ru.krabikk.nntcschedule.InterfaceStyle
import androidx.compose.ui.graphics.Color

// Фиксированные оттенки: динамические цвета Material You не подменяют дизайн.
private val LightPalette = lightColorScheme(
    primary = Color(0xFF6D51AD),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFECE5FA),
    onPrimaryContainer = Color(0xFF4D347E),
    secondary = Color(0xFF786691),
    onSecondary = Color.White,
    background = Color(0xFFF9F7FC),
    onBackground = Color(0xFF201C28),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF201C28),
    surfaceVariant = Color(0xFFF0ECF6),
    onSurfaceVariant = Color(0xFF686172),
    outline = Color(0xFFE4DEEB),
    error = Color(0xFFB62936),
    onError = Color.White
)

private val DarkPalette = darkColorScheme(
    primary = Color(0xFFB99DF5),
    onPrimary = Color(0xFF26183C),
    primaryContainer = Color(0xFF3A2B53),
    onPrimaryContainer = Color(0xFFE7D8FF),
    secondary = Color(0xFFC9B9E3),
    onSecondary = Color(0xFF30253F),
    background = Color(0xFF131119),
    onBackground = Color(0xFFF5F1FA),
    surface = Color(0xFF221E2C),
    onSurface = Color(0xFFF5F1FA),
    surfaceVariant = Color(0xFF302A3B),
    onSurfaceVariant = Color(0xFFC9C1D2),
    outline = Color(0xFF494154),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690006)
)

/** Текущий вид и режим освещения доступны визуальным компонентам. */
val LocalInterfaceStyle = compositionLocalOf { InterfaceStyle.MINIMAL }
val LocalAppDarkTheme = compositionLocalOf { false }

// Контрастные непрозрачные поверхности: надписи не смешиваются с фоновыми эффектами.
private val AuroraLightPalette = lightColorScheme(
    primary = Color(0xFF60409F), onPrimary = Color.White,
    primaryContainer = Color(0xFFE9E1FA), onPrimaryContainer = Color(0xFF342050),
    secondary = Color(0xFF5065A5), onSecondary = Color.White,
    background = Color(0xFFF5F2FC), onBackground = Color(0xFF211B31),
    surface = Color(0xFFFFFEFF), onSurface = Color(0xFF211B31),
    surfaceVariant = Color(0xFFEEEAF8), onSurfaceVariant = Color(0xFF50465F),
    outline = Color(0xFFD6CDE6), error = Color(0xFFB3263E), onError = Color.White
)

private val AuroraDarkPalette = darkColorScheme(
    primary = Color(0xFFD5BEFF), onPrimary = Color(0xFF28183C),
    primaryContainer = Color(0xFF46345E), onPrimaryContainer = Color(0xFFF1E8FF),
    secondary = Color(0xFFB7D9FF), onSecondary = Color(0xFF172942),
    background = Color(0xFF111728), onBackground = Color(0xFFF9F7FF),
    surface = Color(0xFF222A42), onSurface = Color(0xFFF9F7FF),
    surfaceVariant = Color(0xFF313B57), onSurfaceVariant = Color(0xFFE4DEF3),
    outline = Color(0xFF536078), error = Color(0xFFFFB4BE), onError = Color(0xFF650016)
)

private val NeonLightPalette = lightColorScheme(
    primary = Color(0xFF82259E), onPrimary = Color.White,
    primaryContainer = Color(0xFFF2E2F9), onPrimaryContainer = Color(0xFF482257),
    secondary = Color(0xFF9E286F), onSecondary = Color.White,
    background = Color(0xFFFCF7FF), onBackground = Color(0xFF25182F),
    surface = Color(0xFFFFFDFF), onSurface = Color(0xFF25182F),
    surfaceVariant = Color(0xFFF4EAF9), onSurfaceVariant = Color(0xFF55445D),
    outline = Color(0xFFDECDE5), error = Color(0xFFB62936), onError = Color.White
)

private val NeonDarkPalette = darkColorScheme(
    primary = Color(0xFFFA9CE7), onPrimary = Color(0xFF331038),
    primaryContainer = Color(0xFF542455), onPrimaryContainer = Color(0xFFFFE6FC),
    secondary = Color(0xFFF5A8D8), onSecondary = Color(0xFF3C1231),
    background = Color(0xFF100A19), onBackground = Color(0xFFFCF7FF),
    surface = Color(0xFF24192F), onSurface = Color(0xFFFCF7FF),
    surfaceVariant = Color(0xFF34243F), onSurfaceVariant = Color(0xFFEADBEF),
    outline = Color(0xFF62416C), error = Color(0xFFFFB4AB), onError = Color(0xFF690006)
)

/** Лавандовая тема: матовые поверхности и мягкий сливово-лиловый акцент. */
private val LavenderLightPalette = lightColorScheme(
    primary = Color(0xFF69458C), onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE1F5), onPrimaryContainer = Color(0xFF39234B),
    secondary = Color(0xFF82536F), onSecondary = Color.White,
    background = Color(0xFFFBF6FA), onBackground = Color(0xFF302333),
    surface = Color(0xFFFFFCFF), onSurface = Color(0xFF302333),
    surfaceVariant = Color(0xFFF4EAF4), onSurfaceVariant = Color(0xFF58465A),
    outline = Color(0xFFE7D8E9), error = Color(0xFFB3263E), onError = Color.White
)

private val LavenderDarkPalette = darkColorScheme(
    primary = Color(0xFFE1BDF2), onPrimary = Color(0xFF362044),
    primaryContainer = Color(0xFF513459), onPrimaryContainer = Color(0xFFFFE9FF),
    secondary = Color(0xFFF1B9DB), onSecondary = Color(0xFF462539),
    background = Color(0xFF1B1422), onBackground = Color(0xFFF9F0FA),
    surface = Color(0xFF2B2033), onSurface = Color(0xFFF9F0FA),
    surfaceVariant = Color(0xFF3D2E45), onSurfaceVariant = Color(0xFFE8D8E9),
    outline = Color(0xFF65536A), error = Color(0xFFFFB4BE), onError = Color(0xFF650016)
)

/** Ocean: холодные синевато-бирюзовые цвета, без ярких обводок. */
private val OceanLightPalette = lightColorScheme(
    primary = Color(0xFF006B80), onPrimary = Color.White,
    primaryContainer = Color(0xFFD1F1F6), onPrimaryContainer = Color(0xFF004453),
    secondary = Color(0xFF315F85), onSecondary = Color.White,
    background = Color(0xFFF3F9FB), onBackground = Color(0xFF162B35),
    surface = Color.White, onSurface = Color(0xFF162B35),
    surfaceVariant = Color(0xFFE5F0F3), onSurfaceVariant = Color(0xFF425A65),
    outline = Color(0xFFD1E0E5), error = Color(0xFFB3263E), onError = Color.White
)

private val OceanDarkPalette = darkColorScheme(
    primary = Color(0xFF91E3F0), onPrimary = Color(0xFF003942),
    primaryContainer = Color(0xFF1C4A55), onPrimaryContainer = Color(0xFFD1F8FF),
    secondary = Color(0xFFB2D5FF), onSecondary = Color(0xFF173550),
    background = Color(0xFF091924), onBackground = Color(0xFFF2FAFC),
    surface = Color(0xFF162B35), onSurface = Color(0xFFF2FAFC),
    surfaceVariant = Color(0xFF233B47), onSurfaceVariant = Color(0xFFD4E6EA),
    outline = Color(0xFF45606C), error = Color(0xFFFFB4BE), onError = Color(0xFF650016)
)

/** «Маршрут»: сохраняет сине-розовый контраст и в тёмной, и в светлой системе. */
private val RouteDarkPalette = darkColorScheme(
    primary = Color(0xFFFF76BD), onPrimary = Color(0xFF3C0828),
    primaryContainer = Color(0xFF562344), onPrimaryContainer = Color(0xFFFFE3F1),
    secondary = Color(0xFFAABAE6), onSecondary = Color(0xFF162442),
    background = Color(0xFF0C1630), onBackground = Color(0xFFF8F5FF),
    surface = Color(0xFF202A46), onSurface = Color(0xFFF8F5FF),
    surfaceVariant = Color(0xFF29334E), onSurfaceVariant = Color(0xFFD3CEE2),
    outline = Color(0xFF53617F), error = Color(0xFFFFBAB6), onError = Color(0xFF65000A)
)

private val RouteLightPalette = lightColorScheme(
    primary = Color(0xFFB60D68), onPrimary = Color.White,
    primaryContainer = Color(0xFFFCE1EE), onPrimaryContainer = Color(0xFF650D3A),
    secondary = Color(0xFF52699B), onSecondary = Color.White,
    background = Color(0xFFFAF7FC), onBackground = Color(0xFF281E3D),
    surface = Color.White, onSurface = Color(0xFF281E3D),
    surfaceVariant = Color(0xFFF0E9F6), onSurfaceVariant = Color(0xFF655A75),
    outline = Color(0xFFD4C8E1), error = Color(0xFFBA1A1A), onError = Color.White
)

/**
 * Вместо мгновенного изменения всей палитры плавно интерполируем ВСЕ цвета,
 * от которых зависят тексты, карточки, кнопки и элементы управления.
 * Не создаём анимаций в бесконечном цикле и не затрагиваем хранилище данных.
 */
@Composable
fun NNTCScheduleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    interfaceStyle: String = InterfaceStyle.MINIMAL,
    content: @Composable () -> Unit
) {
    val safeStyle = InterfaceStyle.normalized(interfaceStyle)
    val target = when (safeStyle) {
        InterfaceStyle.AURORA -> if (darkTheme) AuroraDarkPalette else AuroraLightPalette
        InterfaceStyle.NEON -> if (darkTheme) NeonDarkPalette else NeonLightPalette
        InterfaceStyle.LAVENDER -> if (darkTheme) LavenderDarkPalette else LavenderLightPalette
        InterfaceStyle.OCEAN -> if (darkTheme) OceanDarkPalette else OceanLightPalette
        InterfaceStyle.ROUTE -> if (darkTheme) RouteDarkPalette else RouteLightPalette
        InterfaceStyle.ROUTE_1 -> if (darkTheme) RouteDarkPalette else RouteLightPalette
        InterfaceStyle.ROUTE_2 -> if (darkTheme) RouteDarkPalette else RouteLightPalette
        InterfaceStyle.LAYERS_1, InterfaceStyle.ORBIT_1, InterfaceStyle.FOCUS_1,
        InterfaceStyle.LAYERS_2, InterfaceStyle.HOLOGRAM_1 ->
            if (darkTheme) DarkPalette else LightPalette
        else -> if (darkTheme) DarkPalette else LightPalette
    }
    val spec = tween<Color>(durationMillis = 380, easing = FastOutSlowInEasing)
    val primary by animateColorAsState(target.primary, spec, label = "themePrimary")
    val onPrimary by animateColorAsState(target.onPrimary, spec, label = "themeOnPrimary")
    val primaryContainer by animateColorAsState(target.primaryContainer, spec, label = "themePrimaryContainer")
    val onPrimaryContainer by animateColorAsState(target.onPrimaryContainer, spec, label = "themeOnPrimaryContainer")
    val secondary by animateColorAsState(target.secondary, spec, label = "themeSecondary")
    val onSecondary by animateColorAsState(target.onSecondary, spec, label = "themeOnSecondary")
    val background by animateColorAsState(target.background, spec, label = "themeBackground")
    val onBackground by animateColorAsState(target.onBackground, spec, label = "themeOnBackground")
    val surface by animateColorAsState(target.surface, spec, label = "themeSurface")
    val onSurface by animateColorAsState(target.onSurface, spec, label = "themeOnSurface")
    val surfaceVariant by animateColorAsState(target.surfaceVariant, spec, label = "themeSurfaceVariant")
    val onSurfaceVariant by animateColorAsState(target.onSurfaceVariant, spec, label = "themeOnSurfaceVariant")
    val outline by animateColorAsState(target.outline, spec, label = "themeOutline")
    val error by animateColorAsState(target.error, spec, label = "themeError")
    val onError by animateColorAsState(target.onError, spec, label = "themeOnError")

    val palette = target.copy(
        primary = primary, onPrimary = onPrimary,
        primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
        secondary = secondary, onSecondary = onSecondary,
        background = background, onBackground = onBackground,
        surface = surface, onSurface = onSurface,
        surfaceVariant = surfaceVariant, onSurfaceVariant = onSurfaceVariant,
        outline = outline, error = error, onError = onError
    )
    CompositionLocalProvider(
        LocalInterfaceStyle provides safeStyle,
        LocalAppDarkTheme provides darkTheme
    ) {
        MaterialTheme(colorScheme = palette, typography = Typography) {
            // КЛЮЧЕВОЕ ИСПРАВЛЕНИЕ alpha.6.3: вне Surface Text берёт LocalContentColor,
            // иначе заголовки "Настройки" / "Расписание" на тёмных фонах остаются чёрными.
            CompositionLocalProvider(LocalContentColor provides palette.onBackground) {
                content()
            }
        }
    }
}
