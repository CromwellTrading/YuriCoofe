package com.cafeteria.gestion.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * Identidad visual de YuriCoofe.
 *
 * - Base cálida y neutra (no crema): papel "azúcar" y tinta espresso.
 * - Primario por paleta (Adenium por defecto: verde bosque del logo).
 * - Semántica fija en TODAS las paletas: secundario = turquesa (dinero / listo / sincronizado),
 *   terciario = ámbar (pendiente / stock bajo), error = rojo.
 * - Títulos y cifras grandes en serif (como una pizarra de menú); la interfaz en sans.
 */
private class Seed(
    val light: Color,
    val onLight: Color,
    val lightContainer: Color,
    val onLightContainer: Color,
    val dark: Color,
    val onDark: Color,
    val darkContainer: Color,
    val onDarkContainer: Color
)

private fun seedFor(palette: String): Seed = when (palette) {
    "PURPLE" -> Seed(Color(0xFF5B47CF), Color.White, Color(0xFFE6E1FF), Color(0xFF241A66), Color(0xFFC6BCFF), Color(0xFF2B1F78), Color(0xFF42379A), Color(0xFFE6E1FF))
    "GREEN" -> Seed(Color(0xFF1F7A57), Color.White, Color(0xFFD3F0E2), Color(0xFF0A3524), Color(0xFF8ADBB8), Color(0xFF00382A), Color(0xFF14513B), Color(0xFFD3F0E2))
    "BLUE" -> Seed(Color(0xFF2F63B5), Color.White, Color(0xFFDCE8FF), Color(0xFF0E2D5E), Color(0xFFAFC9FF), Color(0xFF0C2B5C), Color(0xFF214A86), Color(0xFFDCE8FF))
    "ORANGE" -> Seed(Color(0xFFB8581C), Color.White, Color(0xFFFFE0CC), Color(0xFF4A2005), Color(0xFFFFB68C), Color(0xFF4F2200), Color(0xFF7A3A0E), Color(0xFFFFE0CC))
    "ROSE" -> Seed(Color(0xFFAE3D6D), Color.White, Color(0xFFFFDDE8), Color(0xFF4C0F29), Color(0xFFFFB0CB), Color(0xFF5A1236), Color(0xFF7E2650), Color(0xFFFFDDE8))
    else -> Seed(Color(0xFF1E5B3A), Color.White, Color(0xFFD5EBDD), Color(0xFF0A2E1C), Color(0xFF8FD3AE), Color(0xFF00391F), Color(0xFF1B4D33), Color(0xFFD5EBDD))
}

fun yuriColorScheme(palette: String, dark: Boolean): ColorScheme {
    val s = seedFor(palette)
    return if (dark) {
        darkColorScheme(
            primary = s.dark, onPrimary = s.onDark, primaryContainer = s.darkContainer, onPrimaryContainer = s.onDarkContainer,
            secondary = Color(0xFF6FD6C2), onSecondary = Color(0xFF00382F),
            secondaryContainer = Color(0xFF12493F), onSecondaryContainer = Color(0xFFC4F0E6),
            tertiary = Color(0xFFFFB86B), onTertiary = Color(0xFF4A2700),
            tertiaryContainer = Color(0xFF5A3305), onTertiaryContainer = Color(0xFFFFE3C2),
            error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
            errorContainer = Color(0xFF7A1F18), onErrorContainer = Color(0xFFFFDAD6),
            background = Color(0xFF16110E), onBackground = Color(0xFFF1E8E1),
            surface = Color(0xFF211A16), onSurface = Color(0xFFF1E8E1),
            surfaceVariant = Color(0xFF33291F), onSurfaceVariant = Color(0xFFCDBFB3),
            outline = Color(0xFF8A7B6F), outlineVariant = Color(0xFF433730),
            inverseSurface = Color(0xFFF1E8E1), inverseOnSurface = Color(0xFF241812)
        )
    } else {
        lightColorScheme(
            primary = s.light, onPrimary = s.onLight, primaryContainer = s.lightContainer, onPrimaryContainer = s.onLightContainer,
            secondary = Color(0xFF0E7C6B), onSecondary = Color.White,
            secondaryContainer = Color(0xFFCDEFE7), onSecondaryContainer = Color(0xFF06382F),
            tertiary = Color(0xFFB25E09), onTertiary = Color.White,
            tertiaryContainer = Color(0xFFFFE3C2), onTertiaryContainer = Color(0xFF4A2700),
            error = Color(0xFFB3261E), onError = Color.White,
            errorContainer = Color(0xFFFAD8D4), onErrorContainer = Color(0xFF5C0F0A),
            background = Color(0xFFF5F1EC), onBackground = Color(0xFF241812),
            surface = Color.White, onSurface = Color(0xFF241812),
            surfaceVariant = Color(0xFFECE5DD), onSurfaceVariant = Color(0xFF6D6159),
            outline = Color(0xFFB9ABA0), outlineVariant = Color(0xFFE2D8CE),
            inverseSurface = Color(0xFF3A2D25), inverseOnSurface = Color(0xFFF8F1EA)
        )
    }
}

val YuriTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 27.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 27.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.sp)
)

val YuriShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun YuriTheme(palette: String, dark: Boolean, content: @Composable () -> Unit) {
    val scheme = yuriColorScheme(palette, dark)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                @Suppress("DEPRECATION")
                window.statusBarColor = android.graphics.Color.argb(
                    255, (scheme.background.red * 255).toInt(), (scheme.background.green * 255).toInt(), (scheme.background.blue * 255).toInt())
                @Suppress("DEPRECATION")
                window.navigationBarColor = android.graphics.Color.argb(
                    255, (scheme.surface.red * 255).toInt(), (scheme.surface.green * 255).toInt(), (scheme.surface.blue * 255).toInt())
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = scheme, typography = YuriTypography, shapes = YuriShapes, content = content)
}

/** Resuelve el modo oscuro a partir de la preferencia guardada (SYSTEM / LIGHT / DARK). */
@Composable
fun resolveDark(appearance: String): Boolean = when (appearance) {
    "DARK" -> true
    "LIGHT" -> false
    else -> isSystemInDarkTheme()
}

/** Motivo de azulejo cubano (rombo + puntos) muy sutil, para cabeceras. */
fun Modifier.tileBackdrop(color: Color): Modifier = this
    .clipToBounds()
    .drawBehind {
        val cell = 44.dp.toPx()
        val stroke = Stroke(width = 1.2.dp.toPx())
        var y = 0f
        while (y < size.height + cell) {
            var x = 0f
            while (x < size.width + cell) {
                val cx = x + cell / 2f
                val cy = y + cell / 2f
                val r = cell * 0.34f
                val diamond = Path().apply {
                    moveTo(cx, cy - r); lineTo(cx + r, cy); lineTo(cx, cy + r); lineTo(cx - r, cy); close()
                }
                drawPath(diamond, color, style = stroke)
                drawCircle(color, radius = cell * 0.07f, center = Offset(cx, cy))
                drawCircle(color, radius = cell * 0.05f, center = Offset(x, y))
                x += cell
            }
            y += cell
        }
    }
