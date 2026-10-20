package fi.dreamr.rinkulat.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import fi.dreamr.rinkulat.game.ChamberWorld
import fi.dreamr.rinkulat.game.GameMode
import fi.dreamr.rinkulat.game.GamePhase
import fi.dreamr.rinkulat.game.HudState
import fi.dreamr.rinkulat.game.Jet
import fi.dreamr.rinkulat.game.LevelLayout
import fi.dreamr.rinkulat.game.Obstacle
import fi.dreamr.rinkulat.game.Wormhole
import fi.dreamr.rinkulat.game.GameViewModel
import fi.dreamr.rinkulat.game.rememberTiltReading
import fi.dreamr.rinkulat.ui.theme.Backdrop
import fi.dreamr.rinkulat.ui.theme.CaseEdge
import fi.dreamr.rinkulat.ui.theme.FlowerRed
import fi.dreamr.rinkulat.ui.theme.LeafGreen
import fi.dreamr.rinkulat.ui.theme.LocalToyTheme
import fi.dreamr.rinkulat.ui.theme.RingColors
import fi.dreamr.rinkulat.ui.theme.Scenery
import fi.dreamr.rinkulat.ui.theme.ToyTheme
import fi.dreamr.rinkulat.ui.theme.toyThemeFor
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

@Composable
fun RinkulatScreen(viewModel: GameViewModel = viewModel()) {
    val tilt = rememberTiltReading()
    // Bit i is set while the button for jet i is held.
    val pressedJets = remember { mutableIntStateOf(0) }
    // A new layout brings new buttons; drop presses held on the old ones.
    LaunchedEffect(viewModel.layout) { pressedJets.intValue = 0 }

    LaunchedEffect(viewModel) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else (now - last) / 1_000_000_000f
                last = now
                viewModel.advance(dt, tilt.x, tilt.y, pressedJets.intValue, tilt.shakeX, tilt.shakeY)
            }
        }
    }

    CompositionLocalProvider(LocalToyTheme provides toyThemeFor(viewModel.themeId)) {
        GameContent(viewModel, pressedJets.intValue) { jet, down ->
            val bit = 1 shl jet
            pressedJets.intValue = if (down) pressedJets.intValue or bit else pressedJets.intValue and bit.inv()
        }
    }
}

@Composable
private fun GameContent(viewModel: GameViewModel, pressedJets: Int, onJetPressChange: (jet: Int, down: Boolean) -> Unit) {
    val phase = viewModel.phase
    val atStart = phase == GamePhase.Menu || phase == GamePhase.LevelSelect
    Box(
        Modifier
            .fillMaxSize()
            .background(Backdrop)
            .systemBarsPadding(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Hud(
                state = viewModel.hud,
                atStart = atStart,
                showRestart = viewModel.mode == GameMode.FreePlay && viewModel.phase == GamePhase.Playing,
                onRestart = viewModel::startFreePlay,
                onHome = viewModel::openMenu,
                onScores = { viewModel.showScores() },
            )
            Spacer(Modifier.height(12.dp))
            ToyCase(
                world = viewModel.world,
                layout = viewModel.layout,
                frame = { viewModel.frame },
                pressedJets = pressedJets,
                onSwipe = if (viewModel.hud.canSwipeLevels) viewModel::changeLevel else null,
                onJetPressChange = onJetPressChange,
                modifier = Modifier.weight(1f),
            )
        }
        if (phase == GamePhase.Menu && !viewModel.scoresVisible) {
            StartButtons(
                challengeStart = viewModel.challengeStart,
                canChangeCheckpoint = viewModel.selectableCheckpoints.size > 1,
                onChangeCheckpoint = viewModel::changeCheckpoint,
                onChallenge = viewModel::startChallenge,
                onFreePlay = viewModel::openLevelSelect,
            )
        }
        if (phase == GamePhase.LevelSelect) {
            LevelSelectOverlay(
                tiles = viewModel.levelTiles,
                allowLocked = viewModel.isDebug,
                onPick = viewModel::pickFreePlayLevel,
                onClose = viewModel::openMenu,
            )
        }
        PhaseOverlay(
            phase = phase,
            onBeginLevel = viewModel::beginLevel,
            onNextLevel = viewModel::nextLevel,
            onPlayAgain = viewModel::playAgain,
            onChooseLevel = viewModel::openLevelSelect,
            namingEntry = viewModel.namingEntry,
            playerName = viewModel.playerName,
            onNameChange = viewModel::updatePlayerName,
            onScores = { viewModel.showScores() },
            onQuit = viewModel::openMenu,
        )
        if (viewModel.scoresVisible) {
            ScoresOverlay(
                tab = viewModel.scoresTab,
                challengeScores = viewModel.challengeScores,
                freePlayTimes = viewModel.freePlayTimes,
                freePlayLevel = viewModel.scoresLevel,
                latestEntry = viewModel.latestEntry,
                onTabChange = viewModel::showScores,
                onFreePlayLevelChange = viewModel::changeScoresLevel,
                onClose = viewModel::hideScores,
            )
        }
    }
}

@Composable
private fun Hud(
    state: HudState,
    atStart: Boolean,
    showRestart: Boolean,
    onRestart: () -> Unit,
    onHome: () -> Unit,
    onScores: () -> Unit,
) {
    // Fixed height and widths so the chamber below never changes size between levels.
    Row(
        Modifier
            .widthIn(max = 480.dp)
            .fillMaxWidth()
            .height(60.dp)
            .toyPanel(RoundedCornerShape(20.dp))
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            val topLine = when {
                atStart -> "Pump, tilt, catch the rings"
                state.level == null -> ""
                state.canSwipeLevels -> "‹ Level ${state.level} ›  ${state.levelTitle}"
                else -> "Level ${state.level} · ${state.levelTitle}"
            }
            Text(
                topLine,
                color = PanelTextDim,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (atStart) "Rinkulat" else "Caught ${state.caught}/${state.total}",
                color = PanelText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
        Text(
            if (atStart) "" else formatTime(state.seconds),
            modifier = Modifier.width(72.dp),
            textAlign = TextAlign.Center,
            color = if (state.lowTime) LowTimeRed else PanelText,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Row(Modifier.width(96.dp), horizontalArrangement = Arrangement.End) {
            if (atStart) {
                IconButton(onClick = onScores) {
                    Icon(Icons.Default.Star, contentDescription = "Scores", tint = PanelText)
                }
            } else {
                if (showRestart) {
                    IconButton(onClick = onRestart) {
                        Icon(Icons.Default.Refresh, contentDescription = "Restart", tint = PanelText)
                    }
                }
                IconButton(onClick = onHome) {
                    Icon(Icons.Default.Home, contentDescription = "Quit to start", tint = PanelText)
                }
            }
        }
    }
}

/** Readable on the orange panel, unlike a bright red. */
private val LowTimeRed = Color(0xFF7A0010)

@Composable
private fun ToyCase(
    world: ChamberWorld,
    layout: LevelLayout,
    frame: () -> Long,
    pressedJets: Int,
    onJetPressChange: (jet: Int, down: Boolean) -> Unit,
    onSwipe: ((delta: Int) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val caseShape = RoundedCornerShape(36.dp)
    val theme = LocalToyTheme.current
    val indexedJets = layout.jets.withIndex().toList()
    val topJets = indexedJets.filter { it.value.fromTop }
    val bottomJets = indexedJets.filterNot { it.value.fromTop }

    // Both strips are always shown at the same size, so the chamber never changes between levels.
    @Composable
    fun Strip(jets: List<IndexedValue<Jet>>) = ButtonStrip(
        jets = jets,
        pressedJets = pressedJets,
        onJetPressChange = onJetPressChange,
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp),
    )

    Column(
        modifier
            .widthIn(max = 480.dp)
            .fillMaxWidth()
            .clip(caseShape)
            .background(Brush.verticalGradient(listOf(theme.liquidTop, theme.liquidBottom)))
            .border(6.dp, CaseEdge.copy(alpha = 0.7f), caseShape)
            .padding(14.dp),
    ) {
        Strip(topJets)
        Spacer(Modifier.height(10.dp))
        // Keep the chamber at the simulation's aspect ratio so walls, jets and the center line up with what's drawn.
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Chamber(
                world = world,
                theme = theme,
                frame = frame,
                modifier = Modifier
                    .aspectRatio(ChamberWorld.WIDTH / ChamberWorld.HEIGHT, matchHeightConstraintsFirst = true)
                    .clip(RoundedCornerShape(24.dp))
                    .then(if (onSwipe != null) Modifier.swipeLevels(onSwipe) else Modifier),
            )
        }
        Spacer(Modifier.height(10.dp))
        Strip(bottomJets)
    }
}

/** Horizontal swipe: left goes to the next level (+1), right to the previous (-1). */
private fun Modifier.swipeLevels(onSwipe: (delta: Int) -> Unit) = pointerInput(onSwipe) {
    val threshold = 64.dp.toPx()
    var total = 0f
    detectHorizontalDragGestures(
        onDragStart = { total = 0f },
        onDragEnd = { if (abs(total) > threshold) onSwipe(if (total < 0f) 1 else -1) },
        onHorizontalDrag = { _, dragAmount -> total += dragAmount },
    )
}

/** A row of button slots (corner, quarter, center, three quarters, corner); each jet's button sits in its slot. */
@Composable
private fun ButtonStrip(
    jets: List<IndexedValue<Jet>>,
    pressedJets: Int,
    onJetPressChange: (jet: Int, down: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (slot in 0 until LevelLayout.SLOTS_PER_STRIP) {
            val jet = jets.firstOrNull { it.value.slot == slot }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                val buttonModifier = Modifier
                    .padding(4.dp)
                    .widthIn(max = 58.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                // Unused slots stay empty but keep their space, so buttons don't move between levels.
                if (jet != null) {
                    val index = jet.index
                    PumpButton(
                        pressed = pressedJets and (1 shl index) != 0,
                        onPressChange = { onJetPressChange(index, it) },
                        modifier = buttonModifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun PumpButton(pressed: Boolean, onPressChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val currentOnPressChange by rememberUpdatedState(onPressChange)
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, label = "buttonScale")
    val theme = LocalToyTheme.current
    Box(
        modifier
            .clip(CircleShape)
            .background(theme.buttonDark.copy(alpha = 0.6f))
            .padding(4.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color.White, theme.button, theme.buttonDark)))
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    currentOnPressChange(true)
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    waitForUpOrCancellation()
                    currentOnPressChange(false)
                }
            },
    )
}

@Composable
private fun Chamber(world: ChamberWorld, theme: ToyTheme, frame: () -> Long, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        frame() // Read to redraw every simulated frame.
        val scale = min(size.width / ChamberWorld.WIDTH, size.height / ChamberWorld.HEIGHT)
        val offsetX = (size.width - ChamberWorld.WIDTH * scale) / 2f
        val offsetY = size.height - ChamberWorld.HEIGHT * scale

        drawRect(Brush.verticalGradient(listOf(theme.liquidTop.copy(alpha = 0.85f), theme.liquidBottom)))
        translate(offsetX, offsetY) {
            when (theme.scenery) {
                Scenery.Garden -> drawGardenScenery(scale)
                Scenery.Sea -> drawSeaScenery(scale)
                Scenery.Space -> drawSpaceScenery(scale)
            }
            drawParticles(world, theme.particle, scale)
            drawJets(world, scale)
            world.layout.obstacles.forEach { drawObstacle(it, scale, world.layout.attractingObstacles) }
            world.layout.wormholes?.let { (a, b) ->
                // The two mouths swirl in opposite directions: in one, out the other.
                drawWormhole(a, scale, world.clock)
                drawWormhole(b, scale, -world.clock)
            }
            drawCaughtRings(world, scale, frontHalf = false)
            world.pegs.forEach { drawPeg(it, scale) }
            drawCaughtRings(world, scale, frontHalf = true)
            world.rings.filter { !it.isCaught }.forEach { drawRing(it, scale, Arc.Full) }
        }
        // Glass glare.
        drawRect(
            Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.18f), Color.Transparent),
                start = Offset.Zero,
                end = Offset(size.width * 0.5f, size.height * 0.3f),
            ),
        )
    }
}

private enum class Arc { Full, Back, Front }

private fun DrawScope.drawGardenScenery(scale: Float) {
    val w = ChamberWorld.WIDTH * scale
    val h = ChamberWorld.HEIGHT * scale
    // Simple leaves rising from the bottom corners.
    listOf(
        Triple(0.08f, -0.35f, 1f),
        Triple(0.18f, -0.15f, 0.8f),
        Triple(0.9f, 0.35f, 1.1f),
        Triple(0.8f, 0.1f, 0.7f),
    ).forEach { (x, angle, size) -> drawLeaf(Offset(x * w, h), angle, size * scale * 0.42f) }
    // A couple of flowers.
    drawFlower(Offset(w * 0.15f, h * 0.18f), scale * 0.07f)
    drawFlower(Offset(w * 0.86f, h * 0.55f), scale * 0.05f)
}

private fun DrawScope.drawLeaf(base: Offset, angle: Float, length: Float) {
    rotate(angle * 180f / PI.toFloat(), pivot = base) {
        val path = Path().apply {
            moveTo(base.x, base.y)
            quadraticTo(base.x + length * 0.35f, base.y - length * 0.5f, base.x, base.y - length)
            quadraticTo(base.x - length * 0.35f, base.y - length * 0.5f, base.x, base.y)
            close()
        }
        drawPath(path, LeafGreen.copy(alpha = 0.55f))
        drawLine(
            LeafGreen.copy(alpha = 0.8f),
            base,
            Offset(base.x, base.y - length),
            strokeWidth = length * 0.025f,
        )
    }
}

private fun DrawScope.drawFlower(center: Offset, radius: Float) {
    for (i in 0 until 5) {
        val a = i * 2f * PI.toFloat() / 5f
        drawCircle(
            FlowerRed.copy(alpha = 0.45f),
            radius * 0.55f,
            Offset(center.x + cos(a) * radius * 0.6f, center.y + sin(a) * radius * 0.6f),
        )
    }
    drawCircle(Color(0xFFFFE07A).copy(alpha = 0.6f), radius * 0.3f, center)
}

/** Blue water chapter: light rays from the surface, seaweed from the floor, a starfish and a shell. */
private fun DrawScope.drawSeaScenery(scale: Float) {
    val w = ChamberWorld.WIDTH * scale
    val h = ChamberWorld.HEIGHT * scale
    listOf(0.2f to 0.12f, 0.55f to 0.08f, 0.8f to 0.1f).forEach { (x, width) ->
        val path = Path().apply {
            moveTo((x - width / 2f) * w, 0f)
            lineTo((x + width / 2f) * w, 0f)
            lineTo((x + width) * w, h * 0.7f)
            lineTo(x * w, h * 0.7f)
            close()
        }
        drawPath(
            path,
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.14f), Color.Transparent), endY = h * 0.7f),
        )
    }
    listOf(
        Triple(0.07f, 0.42f, 0f),
        Triple(0.15f, 0.3f, 1.3f),
        Triple(0.86f, 0.38f, 2.1f),
        Triple(0.93f, 0.26f, 0.7f),
    ).forEach { (x, height, phase) -> drawSeaweed(Offset(x * w, h), height * scale, phase) }
    drawStarfish(Offset(w * 0.3f, h - scale * 0.03f), scale * 0.045f)
    drawShell(Offset(w * 0.7f, h - scale * 0.025f), scale * 0.035f)
}

/** Space chapter: a starfield, a ringed planet and a small moon. Fixed positions so it doesn't flicker. */
private fun DrawScope.drawSpaceScenery(scale: Float) {
    val w = ChamberWorld.WIDTH * scale
    val h = ChamberWorld.HEIGHT * scale
    val stars = kotlin.random.Random(7)
    repeat(45) {
        val size = scale * (0.002f + stars.nextFloat() * 0.005f)
        drawCircle(Color.White.copy(alpha = 0.3f + stars.nextFloat() * 0.5f), size, Offset(stars.nextFloat() * w, stars.nextFloat() * h))
    }
    // Ringed planet, low on the right.
    val planet = Offset(w * 0.8f, h * 0.82f)
    val r = scale * 0.11f
    drawCircle(
        Brush.radialGradient(listOf(Color(0xFFFFB27A), Color(0xFFB0457A)), center = planet - Offset(r * 0.4f, r * 0.4f), radius = r * 1.6f),
        r,
        planet,
        alpha = 0.6f,
    )
    rotate(-18f, pivot = planet) {
        drawOval(
            Color(0xFFFFE1B5).copy(alpha = 0.45f),
            topLeft = Offset(planet.x - r * 1.8f, planet.y - r * 0.35f),
            size = Size(r * 3.6f, r * 0.7f),
            style = Stroke(r * 0.12f),
        )
    }
    // Small moon, high on the left.
    val moon = Offset(w * 0.18f, h * 0.12f)
    drawCircle(Color(0xFFDDE3F0).copy(alpha = 0.45f), scale * 0.045f, moon)
    drawCircle(Color(0xFF9AA3B8).copy(alpha = 0.4f), scale * 0.01f, moon + Offset(scale * 0.012f, scale * 0.008f))
}

private val SeaweedGreen = Color(0xFF2E9E7A)

private fun DrawScope.drawSeaweed(base: Offset, height: Float, phase: Float) {
    val path = Path().apply {
        moveTo(base.x, base.y)
        val segments = 4
        for (i in 1..segments) {
            val y = base.y - height * i / segments
            val sway = sin(phase + i * 1.4f) * height * 0.12f
            quadraticTo(base.x + sway * 1.6f, y + height / segments / 2f, base.x + sway, y)
        }
    }
    drawPath(path, SeaweedGreen.copy(alpha = 0.6f), style = Stroke(width = height * 0.07f))
}

private fun DrawScope.drawStarfish(center: Offset, radius: Float) {
    val path = Path().apply {
        for (i in 0 until 10) {
            val a = -PI.toFloat() / 2f + i * PI.toFloat() / 5f
            val r = if (i % 2 == 0) radius else radius * 0.45f
            val point = Offset(center.x + cos(a) * r, center.y + sin(a) * r)
            if (i == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
        }
        close()
    }
    drawPath(path, Color(0xFFFF8A65).copy(alpha = 0.7f))
}

private fun DrawScope.drawShell(center: Offset, radius: Float) {
    drawArc(
        Color(0xFFFFE0C2).copy(alpha = 0.7f),
        180f,
        180f,
        true,
        Offset(center.x - radius, center.y - radius),
        Size(radius * 2f, radius * 2f),
    )
    for (i in 1..4) {
        val a = PI.toFloat() + i * PI.toFloat() / 5f
        drawLine(
            Color(0xFFC9906B).copy(alpha = 0.7f),
            Offset(center.x, center.y),
            Offset(center.x + cos(a) * radius, center.y + sin(a) * radius),
            strokeWidth = radius * 0.08f,
        )
    }
}

private fun DrawScope.drawParticles(world: ChamberWorld, color: Color, scale: Float) {
    world.particles.forEach { p ->
        val center = Offset(p.x * scale, p.y * scale)
        if (p.isBubble) {
            drawCircle(Color.White.copy(alpha = 0.5f), p.size * scale * 1.6f, center, style = Stroke(1.5f))
        } else {
            drawCircle(color.copy(alpha = 0.7f), p.size * scale * 0.6f, center)
        }
    }
}

private fun DrawScope.drawJets(world: ChamberWorld, scale: Float) {
    world.layout.jets.forEachIndexed { i, jet ->
        val strength = world.jetStrengths.getOrElse(i) { 0f }
        if (strength < 0.02f) return@forEachIndexed
        // A widening cone from the nozzle along the jet direction.
        val nozzle = Offset(jet.x * scale, jet.y * scale)
        val dir = Offset(jet.dirX, jet.dirY)
        val perp = Offset(-jet.dirY, jet.dirX)
        val length = (if (jet.fromTop || jet.dirX != 0f) 1.0f else 1.25f) * scale
        val far = nozzle + dir * length
        val nearWidth = (if (jet.big) 0.03f else 0.02f) * scale
        val farWidth = (if (jet.big) 0.25f else 0.18f) * scale
        val path = Path().apply {
            moveTo(nozzle.x - perp.x * nearWidth, nozzle.y - perp.y * nearWidth)
            lineTo(far.x - perp.x * farWidth, far.y - perp.y * farWidth)
            lineTo(far.x + perp.x * farWidth, far.y + perp.y * farWidth)
            lineTo(nozzle.x + perp.x * nearWidth, nozzle.y + perp.y * nearWidth)
            close()
        }
        drawPath(
            path,
            Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.22f * strength), Color.Transparent),
                start = nozzle,
                end = far,
            ),
        )
    }
}

private fun DrawScope.drawWormhole(hole: Wormhole, scale: Float, time: Float) {
    val center = Offset(hole.x * scale, hole.y * scale)
    val r = hole.radius * scale
    drawCircle(
        Brush.radialGradient(
            0f to Color.Black.copy(alpha = 0.85f),
            0.6f to Color(0xFF5B2A9E).copy(alpha = 0.7f),
            1f to Color(0xFFB58CFF).copy(alpha = 0.0f),
            center = center,
            radius = r * 1.3f,
        ),
        r * 1.3f,
        center,
    )
    // Spiral arms turning with time.
    rotate(time * 90f, pivot = center) {
        for (i in 0 until 3) {
            rotate(i * 120f, pivot = center) {
                drawArc(
                    Color(0xFFE2D1FF).copy(alpha = 0.55f),
                    startAngle = 0f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(center.x - r * 0.75f, center.y - r * 0.45f),
                    size = Size(r * 1.5f, r * 0.9f),
                    style = Stroke(r * 0.08f),
                )
            }
        }
    }
}

private fun DrawScope.drawObstacle(obstacle: Obstacle, scale: Float, attracting: Boolean) {
    val center = Offset(obstacle.x * scale, obstacle.y * scale)
    val r = obstacle.radius * scale
    if (attracting) {
        // Faint halo out to the reach of the pull.
        val reach = r + ChamberWorld.ATTRACT_REACH * scale
        drawCircle(
            Brush.radialGradient(
                0f to Color.White.copy(alpha = 0.18f),
                r / reach to Color.White.copy(alpha = 0.12f),
                1f to Color.Transparent,
                center = center,
                radius = reach,
            ),
            reach,
            center,
        )
    }
    drawCircle(Color.White.copy(alpha = 0.4f), r, center)
    drawCircle(Color.White.copy(alpha = 0.85f), r, center, style = Stroke(r * 0.12f))
    drawCircle(Color.White.copy(alpha = 0.7f), r * 0.25f, center + Offset(-r * 0.35f, -r * 0.35f))
}

private fun DrawScope.drawPeg(peg: ChamberWorld.Peg, scale: Float) {
    val r = ChamberWorld.PEG_RADIUS * scale
    val x = peg.x * scale
    val top = min(peg.tipY, peg.baseY) * scale
    val bottom = max(peg.tipY, peg.baseY) * scale
    // Mount beyond the base end of the peg.
    val mountHeight = r * 9f
    drawRoundRect(
        Color.White.copy(alpha = 0.35f),
        topLeft = Offset(x - r * 3f, if (peg.sink > 0f) bottom else top - mountHeight),
        size = Size(r * 6f, mountHeight),
        cornerRadius = CornerRadius(r * 2f),
    )
    drawRoundRect(
        Color.White.copy(alpha = 0.55f),
        topLeft = Offset(x - r, if (peg.sink > 0f) top - r else top),
        size = Size(r * 2f, bottom - top + r),
        cornerRadius = CornerRadius(r),
    )
    drawLine(Color.White.copy(alpha = 0.9f), Offset(x - r * 0.4f, top), Offset(x - r * 0.4f, bottom), strokeWidth = r * 0.4f)
}

private fun DrawScope.drawCaughtRings(world: ChamberWorld, scale: Float, frontHalf: Boolean) {
    world.rings.filter { it.isCaught }.forEach { drawRing(it, scale, if (frontHalf) Arc.Front else Arc.Back) }
}

private fun DrawScope.drawRing(ring: ChamberWorld.Ring, scale: Float, arc: Arc) {
    val base = RingColors[ring.colorIndex % RingColors.size]
    val rx = ChamberWorld.RING_RADIUS * scale
    val ry = rx * max(abs(cos(ring.phi)), 0.12f)
    val stroke = Stroke(ChamberWorld.RING_TUBE * scale)
    val topLeft = Offset(ring.x * scale - rx, ring.y * scale - ry)
    val ovalSize = Size(rx * 2f, ry * 2f)
    val dark = base.copy(red = base.red * 0.7f, green = base.green * 0.7f, blue = base.blue * 0.7f)
    rotate(ring.roll * 180f / PI.toFloat(), pivot = Offset(ring.x * scale, ring.y * scale)) {
        when (arc) {
            Arc.Full -> {
                // Darker far half, brighter near half.
                drawArc(dark, 180f, 180f, false, topLeft, ovalSize, style = stroke)
                drawArc(base, 0f, 180f, false, topLeft, ovalSize, style = stroke)
            }
            Arc.Back -> drawArc(dark, 180f, 180f, false, topLeft, ovalSize, style = stroke)
            Arc.Front -> drawArc(base, 0f, 180f, false, topLeft, ovalSize, style = stroke)
        }
    }
}

