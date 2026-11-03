package io.github.iefedev.waddle

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import io.github.iefedev.waddle.storage.Storage
import java.io.File

/**
 * Developer tools compiled into debug builds only (the release source set has an empty stand-in).
 * The strings here are for developers, so they are not in the app's string resources.
 */
object DebugTools {
    const val AVAILABLE = true
    const val SECTION_TITLE = "Debug"
    const val SEED_TITLE = "Seed demo library"
    const val SEED_HINT = "Adds generated sample pictures to a few demo folders, for screenshots. No network."
    const val SEEDED_MESSAGE = "Demo library added"

    private class Sample(val folder: String, val name: String, val w: Int, val h: Int, val top: Int, val bottom: Int)

    private val samples = listOf(
        Sample(Storage.DEFAULT_CATEGORY, "Morning fog over the hills", 1280, 720, 0xFF8EC5FC.toInt(), 0xFFE0C3FC.toInt()),
        Sample(Storage.DEFAULT_CATEGORY, "City lights timelapse", 720, 1280, 0xFF141E30.toInt(), 0xFF243B55.toInt()),
        Sample(Storage.DEFAULT_CATEGORY, "Sunset at the pier", 1280, 720, 0xFFFF7E5F.toInt(), 0xFFFEB47B.toInt()),
        Sample("Nature", "Alpine lake", 1280, 720, 0xFF43C6AC.toInt(), 0xFF191654.toInt()),
        Sample("Nature", "Desert dunes", 1080, 1080, 0xFFF7B733.toInt(), 0xFFFC4A1A.toInt()),
        Sample("Nature", "Forest trail", 720, 1280, 0xFF5A3F37.toInt(), 0xFF2C7744.toInt()),
        Sample("Nature", "Northern lights", 1280, 720, 0xFF0F2027.toInt(), 0xFF2C5364.toInt()),
        Sample("Music", "Live session in the park", 1280, 720, 0xFFDA4453.toInt(), 0xFF89216B.toInt()),
        Sample("Music", "Piano practice", 1080, 1080, 0xFF606C88.toInt(), 0xFF3F4C6B.toInt()),
        Sample("Lectures", "Intro to astronomy", 1280, 720, 0xFF000428.toInt(), 0xFF004E92.toInt()),
        Sample("Lectures", "Cooking basics", 720, 1280, 0xFFF09819.toInt(), 0xFFEDDE5D.toInt()),
    )

    /** Writes generated JPEGs into demo folders; existing demo files are replaced. Blocking. */
    fun seedDemoLibrary(context: Context) {
        val now = System.currentTimeMillis()
        samples.forEachIndexed { i, s ->
            val dir = Storage.categoryDir(s.folder).apply { mkdirs() }
            val file = File(dir, "${s.name}.jpg")
            val bmp = picture(s)
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            bmp.recycle()
            // Spread the timestamps so the gallery's newest-first order is stable.
            file.setLastModified(now - i * 60_000L)
            Storage.scan(context, file)
        }
    }

    /** A sky gradient with a sun and two layers of hills: enough to read as a thumbnail. */
    private fun picture(s: Sample): Bitmap {
        val bmp = Bitmap.createBitmap(s.w, s.h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val w = s.w.toFloat()
        val h = s.h.toFloat()
        val sky = Paint().apply { shader = LinearGradient(0f, 0f, 0f, h, s.top, s.bottom, Shader.TileMode.CLAMP) }
        c.drawRect(0f, 0f, w, h, sky)
        c.drawCircle(w * 0.72f, h * 0.35f, minOf(w, h) * 0.12f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCCFFFFFF.toInt() })
        fun hills(base: Float, amp: Float, color: Int) {
            val p = Path().apply {
                moveTo(0f, h)
                lineTo(0f, base)
                cubicTo(w * 0.25f, base - amp, w * 0.5f, base + amp, w * 0.75f, base - amp * 0.5f)
                cubicTo(w * 0.85f, base - amp, w * 0.95f, base, w, base - amp * 0.3f)
                lineTo(w, h)
                close()
            }
            c.drawPath(p, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        }
        hills(h * 0.70f, h * 0.08f, 0x66000000)
        hills(h * 0.82f, h * 0.06f, 0x99000000.toInt())
        return bmp
    }
}
