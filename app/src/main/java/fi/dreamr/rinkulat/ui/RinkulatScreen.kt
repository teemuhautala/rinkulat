package fi.dreamr.rinkulat.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import fi.dreamr.rinkulat.game.ChamberWorld
import fi.dreamr.rinkulat.game.GameMode
import fi.dreamr.rinkulat.game.GamePhase
import fi.dreamr.rinkulat.game.HudState
import fi.dreamr.rinkulat.game.GameViewModel
import fi.dreamr.rinkulat.game.rememberTiltReading
import fi.dreamr.rinkulat.ui.theme.Backdrop
import fi.dreamr.rinkulat.ui.theme.ButtonTeal
import fi.dreamr.rinkulat.ui.theme.ButtonTealDark
import fi.dreamr.rinkulat.ui.theme.CaseEdge
import fi.dreamr.rinkulat.ui.theme.FlowerRed
import fi.dreamr.rinkulat.ui.theme.LeafGreen
import fi.dreamr.rinkulat.ui.theme.LiquidBottom
import fi.dreamr.rinkulat.ui.theme.LiquidTop
import fi.dreamr.rinkulat.ui.theme.RingColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

@Composable
fun RinkulatScreen(viewModel: GameViewModel = viewModel()) {
    val tilt = rememberTiltReading()
    var pumping by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else (now - last) / 1_000_000_000f
                last = now
                viewModel.advance(dt, tilt.x, tilt.y, pumping)
            }
        }
    }

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
                showRestart = viewModel.mode == GameMode.FreePlay && viewModel.phase == GamePhase.Playing,
                onRestart = viewModel::startFreePlay,
                onMenu = viewModel::openMenu,
            )
            Spacer(Modifier.height(12.dp))
            ToyCase(
                world = viewModel.world,
                frame = { viewModel.frame },
                pumping = pumping,
                onPumpingChange = { pumping = it },
                modifier = Modifier.weight(1f),
            )
        }
        if (viewModel.phase == GamePhase.Menu) {
            MenuOverlay(
                onFreePlay = viewModel::startFreePlay,
                onTiers = viewModel::startTiers,
                onScores = { viewModel.showScores() },
            )
        }
        PhaseOverlay(
            phase = viewModel.phase,
            onBeginLevel = viewModel::beginLevel,
            onNextLevel = viewModel::nextLevel,
            onPlayAgain = viewModel::playAgain,
            onScores = { viewModel.showScores() },
            onMenu = viewModel::openMenu,
        )
        if (viewModel.scoresVisible) {
            ScoresOverlay(
                tab = viewModel.scoresTab,
                tierScores = viewModel.tierScores,
                freePlayTimes = viewModel.freePlayTimes,
                latestEntry = viewModel.latestEntry,
                onTabChange = viewModel::showScores,
                onClose = viewModel::hideScores,
            )
        }
    }
}

@Composable
private fun Hud(state: HudState, showRestart: Boolean, onRestart: () -> Unit, onMenu: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            state.level?.let { Text("Level $it", color = ButtonTeal, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            Text(
                "Caught ${state.caught}/${state.total}",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            formatTime(state.seconds),
            color = if (state.lowTime) Color(0xFFFF6B6B) else Color.White.copy(alpha = 0.8f),
            fontSize = 22.sp,
            fontWeight = if (state.lowTime) FontWeight.Bold else FontWeight.Normal,
        )
        Row {
            if (showRestart) TextButton(onClick = onRestart) { Text("Restart") }
            TextButton(onClick = onMenu) { Text("Menu") }
        }
    }
}

@Composable
private fun ToyCase(
    world: ChamberWorld,
    frame: () -> Long,
    pumping: Boolean,
    onPumpingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val caseShape = RoundedCornerShape(36.dp)
    Column(
        modifier
            .widthIn(max = 480.dp)
            .fillMaxWidth()
            .clip(caseShape)
            .background(Brush.verticalGradient(listOf(LiquidTop, LiquidBottom)))
            .border(6.dp, CaseEdge.copy(alpha = 0.7f), caseShape)
            .padding(14.dp),
    ) {
        Chamber(
            world = world,
            frame = frame,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp)),
        )
        Spacer(Modifier.height(10.dp))
        PumpButton(
            pumping = pumping,
            onPumpingChange = onPumpingChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
        )
    }
}

@Composable
private fun PumpButton(pumping: Boolean, onPumpingChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val scale by animateFloatAsState(if (pumping) 0.9f else 1f, label = "buttonScale")
    Box(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(116.dp)
                .clip(CircleShape)
                .background(ButtonTealDark.copy(alpha = 0.6f))
                .padding(8.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color.White, ButtonTeal, ButtonTealDark)))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown()
                        onPumpingChange(true)
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        waitForUpOrCancellation()
                        onPumpingChange(false)
                    }
                },
        )
    }
}

@Composable
private fun Chamber(world: ChamberWorld, frame: () -> Long, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        frame() // Read to redraw every simulated frame.
        val scale = min(size.width / ChamberWorld.WIDTH, size.height / ChamberWorld.HEIGHT)
        val offsetX = (size.width - ChamberWorld.WIDTH * scale) / 2f
        val offsetY = size.height - ChamberWorld.HEIGHT * scale

        drawRect(Brush.verticalGradient(listOf(LiquidTop.copy(alpha = 0.85f), LiquidBottom)))
        translate(offsetX, offsetY) {
            drawScenery(scale)
            drawParticles(world, scale)
            drawJet(world, scale)
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

private fun DrawScope.drawScenery(scale: Float) {
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

private fun DrawScope.drawParticles(world: ChamberWorld, scale: Float) {
    world.particles.forEach { p ->
        val center = Offset(p.x * scale, p.y * scale)
        if (p.isBubble) {
            drawCircle(Color.White.copy(alpha = 0.5f), p.size * scale * 1.6f, center, style = Stroke(1.5f))
        } else {
            drawCircle(Color(0xFFFFF3B0).copy(alpha = 0.7f), p.size * scale * 0.6f, center)
        }
    }
}

private fun DrawScope.drawJet(world: ChamberWorld, scale: Float) {
    if (world.jet < 0.02f) return
    val w = ChamberWorld.WIDTH * scale
    val h = ChamberWorld.HEIGHT * scale
    val path = Path().apply {
        moveTo(w / 2f - 0.03f * scale, h)
        lineTo(w / 2f - 0.25f * scale, h * 0.15f)
        lineTo(w / 2f + 0.25f * scale, h * 0.15f)
        lineTo(w / 2f + 0.03f * scale, h)
        close()
    }
    drawPath(
        path,
        Brush.verticalGradient(
            listOf(Color.Transparent, Color.White.copy(alpha = 0.22f * world.jet)),
            startY = h * 0.15f,
            endY = h,
        ),
    )
}

private fun DrawScope.drawPeg(peg: ChamberWorld.Peg, scale: Float) {
    val r = ChamberWorld.PEG_RADIUS * scale
    val x = peg.x * scale
    val top = peg.tipY * scale
    val bottom = peg.baseY * scale
    // Mount below the peg.
    drawRoundRect(
        Color.White.copy(alpha = 0.35f),
        topLeft = Offset(x - r * 3f, bottom),
        size = Size(r * 6f, r * 9f),
        cornerRadius = CornerRadius(r * 2f),
    )
    drawRoundRect(
        Color.White.copy(alpha = 0.55f),
        topLeft = Offset(x - r, top - r),
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

