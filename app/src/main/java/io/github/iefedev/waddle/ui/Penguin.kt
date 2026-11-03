package io.github.iefedev.waddle.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** What the mascot is doing; each mood is its own looping animation. */
enum class Mood {
    Idle, Ready, Walk, Happy, Sad,
    /** Queued: taps a foot and glances around. */
    Wait,
    /** Cancelled: shrugs it off. */
    Shrug,
    /** No downloads yet: fishing at an ice hole, dozes off until something bites. */
    Fish,
    /** Empty folder: leans into an empty box, then shrugs at you. */
    Peek,
}

/**
 * The launcher-icon penguin (res/drawable/ic_launcher_foreground.xml), drawn part by part so the
 * body, flippers, feet and eyes can move on their own. Coordinates are the icon's 108-unit viewport.
 */
@Composable
fun Penguin(mood: Mood, size: Dp, modifier: Modifier = Modifier) {
    val time by produceState(0f) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { value = (it - start) / 1e9f }
    }
    // The canvas is 68 x 72 viewport units: the penguin plus room to hop and lean.
    Canvas(modifier.size(size, size * (72f / 68f))) {
        val t = time
        withTransform({
            scale(this@Canvas.size.width / 68f, this@Canvas.size.width / 68f, Offset.Zero)
            translate(-20f, -16f)
        }) {
            when (mood) {
                Mood.Fish -> drawFishing(t)
                Mood.Peek -> drawPeeking(t)
                else -> drawPenguin(pose(mood, t))
            }
        }
    }
}

private object Parts {
    private fun p(d: String): Path = PathParser().parsePathString(d).toPath()
    val flipperL = p("M37,46C31,50 26,56 25,62C25,64.5 27.5,65 30,63C32,61 34,58 35,56Z")
    val flipperR = p("M71,46C77,50 82,56 83,62C83,64.5 80.5,65 78,63C76,61 74,58 73,56Z")
    val footL = p("M36,79C36,76.5 40,76 44,76.5C48,77 49.5,79 48.5,81.5C47.5,84 44,84.5 41.5,84C38.5,83.5 36,82 36,79Z")
    val footR = p("M72,79C72,76.5 68,76 64,76.5C60,77 58.5,79 59.5,81.5C60.5,84 64,84.5 66.5,84C69.5,83.5 72,82 72,79Z")
    val body = p("M54,24C65,24 71.5,32 72.5,44C73.5,52 76,58 76,66C76,75 67,80 54,80C41,80 32,75 32,66C32,58 34.5,52 35.5,44C36.5,32 43,24 54,24Z")
    val belly = p("M54,37.5C56,33.5 59.5,32 63,32.5C67.5,33 70,37 69.8,41.5C69.6,46 66.5,49 62,50C60.5,50.3 59.8,51 59.8,52.5L59.8,59.5C59.8,60.5 60.3,61 61.3,61L66.5,61C68,61 68.7,62.6 67.7,63.8L55.6,75.5C54.8,76.4 53.2,76.4 52.4,75.5L40.3,63.8C39.3,62.6 40,61 41.5,61L46.7,61C47.7,61 48.2,60.5 48.2,59.5L48.2,52.5C48.2,51 47.5,50.3 46,50C41.5,49 38.4,46 38.2,41.5C38,37 40.5,33 45,32.5C48.5,32 52,33.5 54,37.5Z")
    val beak = p("M50.8,43.2L57.2,43.2C58,43.2 58.3,44 57.8,44.6L54.7,47.4C54.3,47.7 53.7,47.7 53.3,47.4L50.2,44.6C49.7,44 50,43.2 50.8,43.2Z")
    val boxFlapL = p("M64,68L57,61L61,59.5L67,68Z")
    val boxFlapR = p("M86,68L88,60L84,59.5L83,68Z")
}

/** A thin ice rim keeps the navy penguin visible on the navy background. */
private val rim = Stroke(width = 1.4f)
private val rimColor = Waddle.Ice.copy(alpha = 0.5f)

private val Wood = Color(0xFFC9A27A)
private val Cardboard = Color(0xFFB07A48)
private val CardboardDark = Color(0xFF5C3A1E)
private val Water = Color(0xFF050B17)

private fun DrawScope.part(path: Path, color: Color) {
    drawPath(path, rimColor, style = rim)
    drawPath(path, color)
}

/** 0..1..0 over [period] seconds. */
private fun wave(t: Float, period: Float) = (1 - cos(2 * PI * t / period).toFloat()) / 2

/** 0..1 eased. */
private fun ease(x: Float) = (1 - cos(PI * x.coerceIn(0f, 1f)).toFloat()) / 2

/** Within a loop at phase [p] (seconds): rises over [up], stays for [stay], falls over [down], else 0. */
private fun hold(p: Float, start: Float, up: Float, stay: Float, down: Float): Float = when {
    p < start -> 0f
    p < start + up -> ease((p - start) / up)
    p < start + up + stay -> 1f
    p < start + up + stay + down -> 1 - ease((p - start - up - stay) / down)
    else -> 0f
}

/** How the body is posed for one frame. Flaps are degrees outward; [look] shifts the pupils sideways. */
private class Pose(
    var lean: Float = 0f,
    var lift: Float = 0f,
    var squashX: Float = 1f,
    var squashY: Float = 1f,
    var flapL: Float = 0f,
    var flapR: Float = 0f,
    var stepL: Float = 0f,
    var stepR: Float = 0f,
    var eyes: Float = 1f,
    var look: Float = 0f,
)

private fun pose(mood: Mood, t: Float): Pose {
    val p = Pose(eyes = blink(t))
    when (mood) {
        Mood.Idle, Mood.Fish, Mood.Peek -> { val b = wave(t, 3f); p.squashX = 1 + 0.02f * b; p.squashY = 1 - 0.02f * b }
        Mood.Ready -> { val b = wave(t, 0.9f); p.lift = -3f * b; p.lean = -3f * b }
        Mood.Walk -> {
            val a = (2 * PI * t / 0.46f).toFloat()
            p.lean = -8f * cos(a)
            p.stepL = -3f * max(0f, sin(a))
            p.stepR = -3f * max(0f, -sin(a))
        }
        Mood.Happy -> {
            val ph = (t % 0.8f) / 0.8f
            p.lift = if (ph < 0.7f) -12f * sin(PI * ph / 0.7f).toFloat() else 0f
            p.squashY = 1 + 0.05f * sin(2 * PI * ph).toFloat()
            p.squashX = 2 - p.squashY
            p.flapL = 40f * sin(PI * ph).toFloat(); p.flapR = p.flapL
        }
        Mood.Sad -> { p.lean = -12f + 3f * wave(t, 1.6f); p.lift = 1.5f; p.eyes = 0.45f }
        Mood.Wait -> {
            // Two quick taps, a pause; eyes drift from side to side.
            val ph = t % 2.4f
            if (ph < 1.2f) p.stepR = -2.5f * max(0f, sin(2 * PI * ph / 0.6f).toFloat())
            val side = sin(2 * PI * t / 4.8f).toFloat()
            p.look = 1.3f * side
            p.lean = 2f * side
        }
        Mood.Shrug -> {
            val s = hold(t % 2.6f, 0f, 0.35f, 0.9f, 0.4f)
            p.flapL = 38f * s; p.flapR = p.flapL
            p.lift = -1.5f * s
            p.lean = 6f * s
            p.eyes = min(p.eyes, 1 - 0.45f * s)
            p.look = -1f * s
        }
    }
    return p
}

private val feet = Offset(54f, 84f)

private fun DrawScope.drawPenguin(p: Pose) {
    translate(0f, p.lift) {
        rotate(p.lean, feet) {
            scale(p.squashX, p.squashY, feet) {
                rotate(p.flapL, Offset(36f, 47f)) { part(Parts.flipperL, Waddle.PenguinNavy) }
                rotate(-p.flapR, Offset(72f, 47f)) { part(Parts.flipperR, Waddle.PenguinNavy) }
                translate(0f, p.stepL) { part(Parts.footL, Waddle.Beak) }
                translate(0f, p.stepR) { part(Parts.footR, Waddle.Beak) }
                part(Parts.body, Waddle.PenguinNavy)
                drawPath(Parts.belly, Color.White)
                scale(1f, p.eyes, Offset(54f, 40.7f)) {
                    drawCircle(Waddle.PenguinNavy, 2.6f, Offset(46.3f + p.look, 40.7f))
                    drawCircle(Waddle.PenguinNavy, 2.6f, Offset(61.7f + p.look, 40.7f))
                    drawCircle(Color.White, 0.8f, Offset(47.2f + p.look, 39.5f))
                    drawCircle(Color.White, 0.8f, Offset(62.6f + p.look, 39.5f))
                }
                drawPath(Parts.beak, Waddle.Beak)
            }
        }
    }
}

// Scenes with a prop draw the penguin smaller on the left so the prop fits beside it.
private const val SMALL = 0.72f
private val smallShift = Offset(-14f, 2f)

private fun DrawScope.drawSmall(p: Pose) {
    translate(smallShift.x, smallShift.y) { scale(SMALL, SMALL, feet) { drawPenguin(p) } }
}

/** Where a point on the right flipper ends up in a small scene, so a held prop follows the flipper. */
private fun heldPoint(point: Offset, p: Pose): Offset {
    val a = -p.flapR * PI.toFloat() / 180f
    val d = point - Offset(72f, 47f)
    val r = Offset(72f + d.x * cos(a) - d.y * sin(a), 47f + d.x * sin(a) + d.y * cos(a) + p.lift)
    return (r - feet) * SMALL + feet + smallShift
}

private fun DrawScope.drawFishing(t: Float) {
    // An 8 s loop: watch the float, doze off, then something bites and the penguin jolts awake.
    val ph = t % 8f
    val doze = hold(ph, 4.6f, 1.2f, 0.4f, 0.1f)
    val bite = hold(ph, 6.3f, 0.1f, 0.35f, 0.5f)
    val p = pose(Mood.Fish, t).apply {
        flapR = 20f + 18f * bite
        look = 1.3f * (1 - doze)
        eyes = if (bite > 0f) 1f else min(eyes, 1 - 0.75f * doze)
        lean = 4f * doze - 3f * bite
        lift = -3f * bite
    }

    val hole = Offset(80f, 84.5f)
    drawOval(Water, Offset(hole.x - 8f, hole.y - 2.6f), Size(16f, 5.2f))
    drawOval(rimColor, Offset(hole.x - 8f, hole.y - 2.6f), Size(16f, 5.2f), style = Stroke(1f))
    if (bite > 0f) {
        val r = 3f + 6f * (1 - bite)
        drawOval(Waddle.Ice.copy(alpha = 0.6f * bite), Offset(hole.x - r, hole.y - r * 0.32f), Size(2 * r, r * 0.64f), style = Stroke(0.6f))
    }

    drawSmall(p)

    val grip = heldPoint(Offset(81f, 62f), p)
    val tip = grip + Offset(24f, -36f - 3f * bite)
    val bob = Offset(hole.x, hole.y - 2.2f + 0.6f * sin(2 * PI * t / 1.7f).toFloat() + 2.4f * bite)
    val line = Path().apply {
        moveTo(tip.x, tip.y)
        quadraticTo(tip.x - 1f, (tip.y + bob.y) / 2 + 6f * (1 - bite), bob.x, bob.y)
    }
    drawPath(line, Waddle.Text.copy(alpha = 0.55f), style = Stroke(0.5f))
    drawLine(Wood, grip, tip, strokeWidth = 1.6f, cap = StrokeCap.Round)
    drawCircle(Waddle.Beak, 1.7f, bob)
    drawCircle(Color.White, 0.8f, bob + Offset(0f, -0.9f))
}

private fun DrawScope.drawPeeking(t: Float) {
    // A 5 s loop: lean into the box, look around inside, straighten up, shrug at the viewer.
    val ph = t % 5f
    val inBox = hold(ph, 0.2f, 0.7f, 1.4f, 0.6f)
    val shrug = hold(ph, 3.1f, 0.3f, 0.6f, 0.4f)
    val p = pose(Mood.Peek, t).apply {
        lean = inBox * (24f + 2f * sin(2 * PI * t / 0.9f).toFloat())
        lift = -2f * inBox - 1f * shrug
        look = 1.4f * inBox
        flapL = 30f * shrug + 8f * inBox; flapR = 30f * shrug
        eyes = min(eyes, 1 - 0.3f * shrug)
    }
    drawSmall(p)

    drawPath(Parts.boxFlapL, Cardboard)
    drawPath(Parts.boxFlapR, Cardboard)
    drawRect(CardboardDark, Offset(64f, 66f), Size(22f, 3f))
    drawRect(Cardboard, Offset(64f, 68f), Size(22f, 18f))
    drawRect(CardboardDark.copy(alpha = 0.35f), Offset(73f, 68f), Size(4f, 18f))
}

/** Eyes close for a moment every four seconds. */
private fun blink(t: Float): Float {
    val p = (t % 4f) / 4f
    return if (p < 0.93f) 1f else 1f - 0.9f * sin(PI * (p - 0.93f) / 0.07f).toFloat()
}
