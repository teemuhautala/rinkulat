package fi.dreamr.rinkulat.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.dreamr.rinkulat.game.GameMode
import fi.dreamr.rinkulat.game.GamePhase
import fi.dreamr.rinkulat.game.ScoreEntry
import fi.dreamr.rinkulat.game.Tiers
import fi.dreamr.rinkulat.ui.theme.ButtonTeal
import java.text.DateFormat
import java.util.Date

/** Dimmed backdrop with a centered card. */
@Composable
private fun OverlayCard(content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 420.dp),
        ) {
            Column(
                Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = content,
            )
        }
    }
}

@Composable
private fun Title(text: String) = Text(text, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)

@Composable
private fun Body(text: String) = Text(text, fontSize = 18.sp, textAlign = TextAlign.Center)

@Composable
private fun WideButton(text: String, primary: Boolean = true, onClick: () -> Unit) {
    val modifier = Modifier
        .fillMaxWidth()
        .padding(top = 8.dp)
    if (primary) {
        Button(onClick = onClick, modifier = modifier) { Text(text) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(text) }
    }
}

@Composable
fun MenuOverlay(onFreePlay: () -> Unit, onTiers: () -> Unit, onScores: () -> Unit) = OverlayCard {
    Text("Rinkulat", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = ButtonTeal)
    Spacer(Modifier.height(6.dp))
    Body("Pump the water, tilt the phone, land every ring on the pegs.")
    Spacer(Modifier.height(16.dp))
    WideButton("Tiers", onClick = onTiers)
    Text(
        "Start with ${Tiers.START_RINGS} rings and ${Tiers.START_SECONDS} s. " +
            "Each level adds ${Tiers.RINGS_PER_LEVEL} rings and ${Tiers.SECONDS_PER_LEVEL} s, up to level ${Tiers.MAX_LEVEL}.",
        fontSize = 13.sp,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
    )
    WideButton("Free play", onClick = onFreePlay)
    WideButton("Scores", primary = false, onClick = onScores)
}

@Composable
fun PhaseOverlay(
    phase: GamePhase,
    onBeginLevel: () -> Unit,
    onNextLevel: () -> Unit,
    onPlayAgain: () -> Unit,
    onScores: () -> Unit,
    onMenu: () -> Unit,
) {
    when (phase) {
        GamePhase.Menu, GamePhase.Playing -> Unit
        is GamePhase.LevelIntro -> OverlayCard {
            Title("Level ${phase.level}")
            Spacer(Modifier.height(8.dp))
            Body("${phase.rings} rings · ${formatTime(phase.seconds)}")
            Spacer(Modifier.height(16.dp))
            WideButton("Start", onClick = onBeginLevel)
            WideButton("Menu", primary = false, onClick = onMenu)
        }
        is GamePhase.LevelComplete -> OverlayCard {
            Title("Level ${phase.level} cleared!")
            Spacer(Modifier.height(8.dp))
            Body("+${phase.levelScore} points")
            Body("Total ${phase.totalScore}")
            Spacer(Modifier.height(16.dp))
            WideButton("Next level", onClick = onNextLevel)
            WideButton("Menu", primary = false, onClick = onMenu)
        }
        is GamePhase.RunOver -> OverlayCard {
            Title(if (phase.allCleared) "All tiers cleared!" else "Time's up!")
            Spacer(Modifier.height(8.dp))
            Body("Score ${phase.totalScore}")
            Body("Levels cleared: ${phase.levelsCleared}")
            phase.rank?.let { Body("New high score: #${it + 1}") }
            Spacer(Modifier.height(16.dp))
            WideButton("Play again", onClick = onPlayAgain)
            WideButton("Scores", primary = false, onClick = onScores)
            WideButton("Menu", primary = false, onClick = onMenu)
        }
        is GamePhase.FreePlayWon -> OverlayCard {
            Title("All rings caught!")
            Spacer(Modifier.height(8.dp))
            Body("Time ${formatMillis((phase.seconds * 1000).toLong())}")
            phase.rank?.let { Body("Best times: #${it + 1}") }
            Spacer(Modifier.height(16.dp))
            WideButton("Play again", onClick = onPlayAgain)
            WideButton("Scores", primary = false, onClick = onScores)
            WideButton("Menu", primary = false, onClick = onMenu)
        }
    }
}

@Composable
fun ScoresOverlay(
    tab: GameMode,
    tierScores: List<ScoreEntry>,
    freePlayTimes: List<ScoreEntry>,
    latestEntry: Pair<GameMode, Int>?,
    onTabChange: (GameMode) -> Unit,
    onClose: () -> Unit,
) = OverlayCard {
    Title("Scores")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        TabButton("Tiers", tab == GameMode.Tiers) { onTabChange(GameMode.Tiers) }
        TabButton("Free play", tab == GameMode.FreePlay) { onTabChange(GameMode.FreePlay) }
    }
    val entries = if (tab == GameMode.Tiers) tierScores else freePlayTimes
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
            color = if (selected) ButtonTeal else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun ScoreRow(index: Int, entry: ScoreEntry, tab: GameMode, highlighted: Boolean) {
    val background = if (highlighted) ButtonTeal.copy(alpha = 0.25f) else Color.Transparent
    Row(
        Modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${index + 1}.", Modifier.width(32.dp), fontWeight = FontWeight.Bold)
        Column(Modifier.weight(1f)) {
            val main = if (tab == GameMode.Tiers) "${entry.value} pts" else formatMillis(entry.value)
            Text(main, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(
                DateFormat.getDateInstance(DateFormat.SHORT).format(Date(entry.timestamp)),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        if (tab == GameMode.Tiers) Text("Lv ${entry.level}", fontSize = 14.sp)
    }
}

fun formatTime(seconds: Int) = "%d:%02d".format(seconds / 60, seconds % 60)

private fun formatMillis(millis: Long) = "%d:%02d.%d".format(millis / 60_000, millis / 1000 % 60, millis / 100 % 10)
