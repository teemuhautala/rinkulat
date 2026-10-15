package fi.dreamr.rinkulat.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.dreamr.rinkulat.game.GameMode
import fi.dreamr.rinkulat.game.GamePhase
import fi.dreamr.rinkulat.game.ScoreEntry
import fi.dreamr.rinkulat.ui.theme.ButtonTeal
import fi.dreamr.rinkulat.ui.theme.CaseEdge
import fi.dreamr.rinkulat.ui.theme.LiquidBottom
import fi.dreamr.rinkulat.ui.theme.LiquidTop
import java.text.DateFormat
import java.util.Date

/** Text on the orange toy panels. */
val PanelText = Color.White
val PanelTextDim = Color.White.copy(alpha = 0.75f)
private val ButtonText = Color(0xFF0F3F3B)

/** Orange liquid gradient with the clear plastic case edge, matching the toy. */
fun Modifier.toyPanel(shape: Shape = RoundedCornerShape(24.dp)) = this
    .clip(shape)
    .background(Brush.verticalGradient(listOf(LiquidTop, LiquidBottom)))
    .border(4.dp, CaseEdge.copy(alpha = 0.7f), shape)

/** Dimmed backdrop with a centered toy-styled card. */
@Composable
private fun OverlayCard(content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(24.dp)
                .widthIn(max = 420.dp)
                .toyPanel()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

@Composable
private fun Title(text: String) =
    Text(text, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = PanelText)

@Composable
private fun Body(text: String) = Text(text, fontSize = 18.sp, textAlign = TextAlign.Center, color = PanelText)

@Composable
private fun ToyButton(text: String, modifier: Modifier = Modifier, primary: Boolean = true, onClick: () -> Unit) {
    if (primary) {
        Button(
            onClick = onClick,
            modifier = modifier,
            colors = ButtonDefaults.buttonColors(containerColor = ButtonTeal, contentColor = ButtonText),
        ) { Text(text, fontWeight = FontWeight.Bold) }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = PanelText),
            border = BorderStroke(1.dp, PanelText),
        ) { Text(text) }
    }
}

@Composable
private fun WideButton(text: String, primary: Boolean = true, onClick: () -> Unit) = ToyButton(
    text = text,
    primary = primary,
    onClick = onClick,
    modifier = Modifier
        .fillMaxWidth()
        .padding(top = 8.dp),
)

/** Start state: just the two mode buttons over the toy, no dimming. */
@Composable
fun StartButtons(onChallenge: () -> Unit, onFreePlay: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.width(220.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val buttonModifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
            ToyButton("Challenge", buttonModifier, onClick = onChallenge)
            Spacer(Modifier.height(12.dp))
            ToyButton("Free play", buttonModifier, onClick = onFreePlay)
        }
    }
}

@Composable
fun PhaseOverlay(
    phase: GamePhase,
    onBeginLevel: () -> Unit,
    onNextLevel: () -> Unit,
    onPlayAgain: () -> Unit,
    onScores: () -> Unit,
    onQuit: () -> Unit,
) {
    when (phase) {
        GamePhase.Menu, GamePhase.Playing -> Unit
        is GamePhase.LevelIntro -> OverlayCard {
            Title("Level ${phase.level}")
            Text(phase.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = PanelText)
            Spacer(Modifier.height(8.dp))
            Body("${phase.rings} rings · ${formatTime(phase.seconds)}")
            Spacer(Modifier.height(16.dp))
            WideButton("Start", onClick = onBeginLevel)
            WideButton("Quit", primary = false, onClick = onQuit)
        }
        is GamePhase.LevelComplete -> OverlayCard {
            Title("Level ${phase.level} cleared!")
            Spacer(Modifier.height(8.dp))
            Body("+${phase.levelScore} points")
            Body("Total ${phase.totalScore}")
            Spacer(Modifier.height(16.dp))
            WideButton("Next level", onClick = onNextLevel)
            WideButton("Quit", primary = false, onClick = onQuit)
        }
        is GamePhase.RunOver -> OverlayCard {
            Title(if (phase.allCleared) "Challenge complete!" else "Time's up!")
            Spacer(Modifier.height(8.dp))
            Body("Score ${phase.totalScore}")
            Body("Levels cleared: ${phase.levelsCleared}")
            phase.rank?.let { Body("New high score: #${it + 1}") }
            if (phase.practice) {
                Text("Practice run (levels skipped) — not saved", fontSize = 13.sp, color = PanelTextDim, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(16.dp))
            WideButton("Play again", onClick = onPlayAgain)
            WideButton("Scores", primary = false, onClick = onScores)
            WideButton("Quit", primary = false, onClick = onQuit)
        }
        is GamePhase.FreePlayWon -> OverlayCard {
            Title("All rings caught!")
            Spacer(Modifier.height(8.dp))
            Body("Time ${formatMillis((phase.seconds * 1000).toLong())}")
            phase.rank?.let { Body("Best times: #${it + 1}") }
            Spacer(Modifier.height(16.dp))
            WideButton("Play again", onClick = onPlayAgain)
            WideButton("Scores", primary = false, onClick = onScores)
            WideButton("Quit", primary = false, onClick = onQuit)
        }
    }
}

@Composable
fun ScoresOverlay(
    tab: GameMode,
    challengeScores: List<ScoreEntry>,
    freePlayTimes: List<ScoreEntry>,
    latestEntry: Pair<GameMode, Int>?,
    onTabChange: (GameMode) -> Unit,
    onClose: () -> Unit,
) = OverlayCard {
    Title("Scores")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        TabButton("Challenge", tab == GameMode.Challenge) { onTabChange(GameMode.Challenge) }
        TabButton("Free play", tab == GameMode.FreePlay) { onTabChange(GameMode.FreePlay) }
    }
    val entries = if (tab == GameMode.Challenge) challengeScores else freePlayTimes
    val highlight = latestEntry?.takeIf { it.first == tab }?.second
    if (entries.isEmpty()) {
        Spacer(Modifier.height(24.dp))
        Body("No scores yet.")
        Spacer(Modifier.height(24.dp))
    } else {
        LazyColumn(Modifier.heightIn(max = 360.dp)) {
            itemsIndexed(entries) { index, entry -> ScoreRow(index, entry, tab, index == highlight) }
        }
    }
    WideButton("Close", onClick = onClose)
}

@Composable
private fun TabButton(text: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(
            text,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) PanelText else PanelTextDim,
            fontSize = if (selected) 17.sp else 15.sp,
        )
    }
}

@Composable
private fun ScoreRow(index: Int, entry: ScoreEntry, tab: GameMode, highlighted: Boolean) {
    val background = if (highlighted) Color.White.copy(alpha = 0.25f) else Color.Transparent
    Row(
        Modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${index + 1}.", Modifier.width(32.dp), fontWeight = FontWeight.Bold, color = PanelText)
        Column(Modifier.weight(1f)) {
            val main = if (tab == GameMode.Challenge) "${entry.value} pts" else formatMillis(entry.value)
            Text(main, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = PanelText)
            Text(
                DateFormat.getDateInstance(DateFormat.SHORT).format(Date(entry.timestamp)),
                fontSize = 12.sp,
                color = PanelTextDim,
            )
        }
        // Free-play times record which level layout they were played on (0 = before layouts existed).
        if (entry.level > 0) Text("Lv ${entry.level}", fontSize = 14.sp, color = PanelText)
    }
}

fun formatTime(seconds: Int) = "%d:%02d".format(seconds / 60, seconds % 60)

private fun formatMillis(millis: Long) = "%d:%02d.%d".format(millis / 60_000, millis / 1000 % 60, millis / 100 % 10)
