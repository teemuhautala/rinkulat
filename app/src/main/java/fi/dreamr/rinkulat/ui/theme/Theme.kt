package fi.dreamr.rinkulat.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import fi.dreamr.rinkulat.game.ThemeId

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

enum class Scenery { Garden, Sea, Space, Mechanicals }

/** Colors and decoration of the toy for one chapter of levels. */
data class ToyTheme(
    val liquidTop: Color,
    val liquidBottom: Color,
    val button: Color,
    val buttonDark: Color,
    val buttonText: Color,
    /** Floating specks in the liquid. */
    val particle: Color,
    val scenery: Scenery,
)

private val OrangeTheme = ToyTheme(
    liquidTop = LiquidTop,
    liquidBottom = LiquidBottom,
    button = ButtonTeal,
    buttonDark = ButtonTealDark,
    buttonText = Color(0xFF0F3F3B),
    particle = Color(0xFFFFF3B0),
    scenery = Scenery.Garden,
)

private val SeaTheme = ToyTheme(
    liquidTop = Color(0xFF5CC6EE),
    liquidBottom = Color(0xFF1B4E9B),
    button = Color(0xFFFFC94D),
    buttonDark = Color(0xFFE8833A),
    buttonText = Color(0xFF5A2A00),
    particle = Color(0xFFD8F6FF),
    scenery = Scenery.Sea,
)

private val SpaceTheme = ToyTheme(
    liquidTop = Color(0xFF3A2C78),
    liquidBottom = Color(0xFF0E0B2A),
    button = Color(0xFFE59BFF),
    buttonDark = Color(0xFF8E4FD6),
    buttonText = Color(0xFF2A0B45),
    particle = Color(0xFFFFFFFF),
    scenery = Scenery.Space,
)

private val MechanicalTheme = ToyTheme(
    liquidTop = Color(0xFF655044),
    liquidBottom = Color(0xFF241F24),
    button = Color(0xFFE5BE72),
    buttonDark = Color(0xFF9D6337),
    buttonText = Color(0xFF302218),
    particle = Color(0xFFF7DFA0),
    scenery = Scenery.Mechanicals,
)

fun toyThemeFor(id: ThemeId) = when (id) {
    ThemeId.Orange -> OrangeTheme
    ThemeId.Sea -> SeaTheme
    ThemeId.Space -> SpaceTheme
    ThemeId.Mechanicals -> MechanicalTheme
}

/** Theme of the chapter currently shown. */
val LocalToyTheme = staticCompositionLocalOf { OrangeTheme }

@Composable
fun RinkulatTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(primary = ButtonTeal, background = Backdrop),
        content = content,
    )
}
