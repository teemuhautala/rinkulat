package fi.dreamr.rinkulat.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.dreamr.rinkulat.game.GameMode
import fi.dreamr.rinkulat.game.GamePhase
import fi.dreamr.rinkulat.game.LevelTile
import fi.dreamr.rinkulat.game.ScoreEntry
import fi.dreamr.rinkulat.game.ScoreList
import fi.dreamr.rinkulat.game.Tiers
import fi.dreamr.rinkulat.ui.theme.CaseEdge
import fi.dreamr.rinkulat.ui.theme.LocalToyTheme
import fi.dreamr.rinkulat.ui.theme.ToyTheme
import fi.dreamr.rinkulat.ui.theme.toyThemeFor
import java.text.DateFormat
import java.util.Date

/** Text on the orange toy panels. */
val PanelText = Color.White
val PanelTextDim = Color.White.copy(alpha = 0.75f)
/** The current theme's liquid gradient with the clear plastic case edge, matching the toy. */
@Composable
fun Modifier.toyPanel(shape: Shape = RoundedCornerShape(24.dp)) = toyPanel(LocalToyTheme.current, shape)

fun Modifier.toyPanel(theme: ToyTheme, shape: Shape = RoundedCornerShape(24.dp)) = this
    .clip(shape)
    .background(Brush.verticalGradient(listOf(theme.liquidTop, theme.liquidBottom)))
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
        val theme = LocalToyTheme.current
        Button(
            onClick = onClick,
            modifier = modifier,
            colors = ButtonDefaults.buttonColors(containerColor = theme.button, contentColor = theme.buttonText),
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

/**
 * Start state: the two mode buttons over the toy, no dimming. When more than one checkpoint is
 * available, a selector under Challenge picks which one the run starts from.
 */
@Composable
fun StartButtons(
    challengeStart: Int,
    canChangeCheckpoint: Boolean,
    onChangeCheckpoint: (delta: Int) -> Unit,
    onChallenge: () -> Unit,
    onFreePlay: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.width(240.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val buttonModifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
            ToyButton("Challenge", buttonModifier, onClick = onChallenge)
            if (canChangeCheckpoint) {
                Row(
                    Modifier
                        .padding(top = 6.dp)
                        .fillMaxWidth()
                        .toyPanel(RoundedCornerShape(16.dp)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { onChangeCheckpoint(-1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous checkpoint", tint = PanelText)
                    }
                    Text(
                        "Start at level $challengeStart",
                        Modifier.weight(1f),
                        color = PanelText,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    IconButton(onClick = { onChangeCheckpoint(1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next checkpoint", tint = PanelText)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            ToyButton("Free play", buttonModifier, onClick = onFreePlay)
        }
    }
}

/**
 * Free-play level grid: one row per chapter in that chapter's colors. Locked levels are dimmed with a
 * lock; they can only be started when [allowLocked] (debug builds).
 */
@Composable
fun LevelSelectOverlay(
    tiles: List<LevelTile>,
    allowLocked: Boolean,
    onPick: (Int) -> Unit,
    onClose: () -> Unit,
) = OverlayCard {
    Title("Free play")
    Text("Levels unlock as you reach them in Challenge", fontSize = 13.sp, color = PanelTextDim, textAlign = TextAlign.Center)
    if (allowLocked) Text("Debug: locked levels playable", fontSize = 12.sp, color = PanelTextDim)
    Spacer(Modifier.height(8.dp))
    Column(
        Modifier
            .heightIn(max = 420.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        tiles.chunked(Tiers.CHAPTER_LENGTH).forEach { chapter ->
            val first = chapter.first().number
            Text(
                "Levels $first–${chapter.last().number}",
                Modifier.padding(top = 8.dp, bottom = 4.dp),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = PanelText,
            )
            val theme = toyThemeFor(Tiers.themeFor(first))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                chapter.forEach { tile ->
                    LevelTileButton(tile, theme, enabled = tile.unlocked || allowLocked, Modifier.weight(1f)) { onPick(tile.number) }
                }
                // Keep tile size even in a short last row.
                repeat(Tiers.CHAPTER_LENGTH - chapter.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    WideButton("Back", primary = false, onClick = onClose)
}

@Composable
private fun LevelTileButton(tile: LevelTile, theme: ToyTheme, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .aspectRatio(0.8f)
            .alpha(if (tile.unlocked) 1f else 0.55f)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(theme.liquidTop, theme.liquidBottom)))
            .border(2.dp, CaseEdge.copy(alpha = 0.8f), shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${tile.number}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = PanelText)
            if (tile.unlocked) {
                Text(tile.bestMillis?.let { formatMillis(it) } ?: "—", fontSize = 10.sp, color = PanelText, maxLines = 1)
            } else {
                Icon(Icons.Default.Lock, contentDescription = "Locked", tint = PanelText, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
fun PhaseOverlay(
    phase: GamePhase,
    onBeginLevel: () -> Unit,
    onNextLevel: () -> Unit,
    onPlayAgain: () -> Unit,
    onChooseLevel: () -> Unit,
    onScores: () -> Unit,
    onQuit: () -> Unit,
    /** A new high score was set: show the name field. */
    namingEntry: Boolean,
    playerName: String,
    onNameChange: (String) -> Unit,
) {
    when (phase) {
        GamePhase.Menu, GamePhase.Playing, GamePhase.LevelSelect -> Unit
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
            if (phase.startLevel > 1) Body("Started at level ${phase.startLevel}")
            Body("Levels cleared: ${phase.levelsCleared}")
            phase.rank?.let { Body("New high score: #${it + 1}") }
            if (namingEntry) NameField(playerName, onNameChange)
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
            if (phase.newBest) Body("New best for level ${phase.level}!")
            phase.rank?.let { Body("Level ${phase.level} best times: #${it + 1}") }
            if (namingEntry) NameField(playerName, onNameChange)
            Spacer(Modifier.height(16.dp))
            WideButton("Play again", onClick = onPlayAgain)
            WideButton("Choose level", primary = false, onClick = onChooseLevel)
            WideButton("Scores", primary = false, onClick = onScores)
            WideButton("Quit", primary = false, onClick = onQuit)
        }
    }
}

/** Name for the new high score; saved when the player leaves the card. */
@Composable
private fun NameField(name: String, onNameChange: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = name,
        onValueChange = onNameChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        label = { Text("Your name") },
        placeholder = { Text("Up to ${ScoreList.MAX_NAME_LENGTH} letters") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = PanelText,
            unfocusedTextColor = PanelText,
            focusedBorderColor = PanelText,
            unfocusedBorderColor = PanelTextDim,
            focusedLabelColor = PanelText,
            unfocusedLabelColor = PanelTextDim,
            focusedPlaceholderColor = PanelTextDim,
            unfocusedPlaceholderColor = PanelTextDim,
            cursorColor = PanelText,
        ),
    )
}

@Composable
fun ScoresOverlay(
    tab: GameMode,
    challengeScores: List<ScoreEntry>,
    freePlayTimes: List<ScoreEntry>,
    /** Free-play lists are kept per level; this one is shown. */
    freePlayLevel: Int,
    latestEntry: Pair<GameMode, ScoreEntry>?,
    onTabChange: (GameMode) -> Unit,
    onFreePlayLevelChange: (delta: Int) -> Unit,
    onClose: () -> Unit,
) = OverlayCard {
    Title("Scores")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        TabButton("Challenge", tab == GameMode.Challenge) { onTabChange(GameMode.Challenge) }
        TabButton("Free play", tab == GameMode.FreePlay) { onTabChange(GameMode.FreePlay) }
    }
    if (tab == GameMode.FreePlay) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onFreePlayLevelChange(-1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous level", tint = PanelText)
            }
            Text("Level $freePlayLevel", Modifier.width(96.dp), color = PanelText, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            IconButton(onClick = { onFreePlayLevelChange(1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next level", tint = PanelText)
            }
        }
    }
    val entries = if (tab == GameMode.Challenge) challengeScores else ScoreList.forLevel(freePlayTimes, freePlayLevel)
    val highlight = latestEntry?.takeIf { it.first == tab }?.second
    if (entries.isEmpty()) {
        Spacer(Modifier.height(24.dp))
        Body("No scores yet.")
        Spacer(Modifier.height(24.dp))
    } else {
        LazyColumn(Modifier.heightIn(max = 360.dp)) {
            itemsIndexed(entries) { index, entry -> ScoreRow(index, entry, tab, entry == highlight) }
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
            Text(
                entry.name.ifEmpty { "—" },
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = PanelText,
                maxLines = 1,
            )
            val date = DateFormat.getDateInstance(DateFormat.SHORT).format(Date(entry.timestamp))
            // Challenge entries record the last level cleared; free-play lists are already per level.
            val detail = if (tab == GameMode.Challenge && entry.level > 0) "$date · Lv ${entry.level}" else date
            Text(detail, fontSize = 12.sp, color = PanelTextDim)
        }
        val main = if (tab == GameMode.Challenge) "${entry.value} pts" else formatMillis(entry.value)
        Text(main, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = PanelText)
    }
}

fun formatTime(seconds: Int) = "%d:%02d".format(seconds / 60, seconds % 60)

private fun formatMillis(millis: Long) = "%d:%02d.%d".format(millis / 60_000, millis / 1000 % 60, millis / 100 % 10)
