package io.github.iefedev.waddle.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** The fixed Waddle palette, taken from the launcher icon: penguin navy, beak orange, ice blue. */
object Waddle {
    val Night = Color(0xFF0B1426)
    val Card = Color(0xFF14223D)
    val Raised = Color(0xFF1C2E52)
    val Line = Color(0xFF26385E)
    val Text = Color(0xFFEEF3FA)
    val Muted = Color(0xFF8796B3)
    val Beak = Color(0xFFFF8A2A)
    val OnBeak = Color(0xFF2A1300)
    val Ice = Color(0xFF7EC6FA)
    val Error = Color(0xFFE24B4A)
    val ErrorText = Color(0xFFF09595)
    val PenguinNavy = Color(0xFF10234A)
}

private val colors = darkColorScheme(
    primary = Waddle.Beak,
    onPrimary = Waddle.OnBeak,
    primaryContainer = Waddle.Beak.copy(alpha = 0.2f),
    onPrimaryContainer = Waddle.Beak,
    secondary = Waddle.Ice,
    onSecondary = Waddle.Night,
    // The navigation bar's selected-tab pill.
    secondaryContainer = Waddle.Beak.copy(alpha = 0.2f),
    onSecondaryContainer = Waddle.Beak,
    tertiary = Waddle.Ice,
    background = Waddle.Night,
    onBackground = Waddle.Text,
    surface = Waddle.Night,
    onSurface = Waddle.Text,
    surfaceVariant = Waddle.Card,
    onSurfaceVariant = Waddle.Muted,
    surfaceContainerLowest = Waddle.Night,
    surfaceContainerLow = Waddle.Night,
    surfaceContainer = Waddle.Card,
    surfaceContainerHigh = Waddle.Card,
    surfaceContainerHighest = Waddle.Raised,
    outline = Waddle.Line,
    outlineVariant = Waddle.Line,
    error = Waddle.Error,
    onError = Color.White,
)

private val shapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(18.dp),
)

/** Dark only, by design: the app has a single fixed look. */
@Composable
fun WaddleTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = colors, shapes = shapes, content = content)

/** Full-screen variant: gives screens outside a Scaffold the theme's background and text colour. */
@Composable
fun WaddleScreen(content: @Composable () -> Unit) = WaddleTheme {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, content = content)
}
