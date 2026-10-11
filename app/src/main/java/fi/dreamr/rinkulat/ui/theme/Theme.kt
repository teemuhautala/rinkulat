package fi.dreamr.rinkulat.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Backdrop = Color(0xFF1B1A2E)
val CaseEdge = Color(0xFFE8EEF5)
val LiquidTop = Color(0xFFF7A23B)
val LiquidBottom = Color(0xFFD9541A)
val ButtonTeal = Color(0xFF7FE3DA)
val ButtonTealDark = Color(0xFF3FB3AA)
val LeafGreen = Color(0xFF1F7A6B)
val FlowerRed = Color(0xFFE2463A)

val RingColors = listOf(
    Color(0xFFFF7EB6), // pink
    Color(0xFFB8E83A), // lime
    Color(0xFFFFB12B), // orange
    Color(0xFF4FC9E8), // sky blue
    Color(0xFFB98CF5), // lilac
)

@Composable
fun RinkulatTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(primary = ButtonTeal, background = Backdrop),
        content = content,
    )
}
