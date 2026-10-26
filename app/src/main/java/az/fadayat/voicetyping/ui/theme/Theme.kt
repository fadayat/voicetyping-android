package az.fadayat.voicetyping.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Color(0xFF355CA8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E2FF),
    onPrimaryContainer = Color(0xFF071B41),
    secondary = Color(0xFF545F75),
    background = Color(0xFFFBF8F3),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF1A1C1E),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFAEC6FF),
    onPrimary = Color(0xFF002E69),
    primaryContainer = Color(0xFF16458F),
    onPrimaryContainer = Color(0xFFD9E2FF),
    secondary = Color(0xFFBBC7DB),
    background = Color(0xFF121316),
    surface = Color(0xFF1A1C1E),
    onSurface = Color(0xFFE3E2E6),
    error = Color(0xFFFFB4AB),
)

@Composable
fun VoiceTypingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
