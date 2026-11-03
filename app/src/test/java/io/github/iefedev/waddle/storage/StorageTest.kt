package io.github.iefedev.waddle.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class StorageTest {
    @Test
    fun sanitizeDropsReservedCharacters() {
        assertEquals("a b c", Storage.sanitize("a/b:c"))
        assertEquals("name", Storage.sanitize("..name. "))
        assertEquals("", Storage.sanitize("  ///  "))
    }

    @Test
    fun sanitizeCapsLength() {
        assertTrue(Storage.sanitize("x".repeat(500)).toByteArray().size <= 200)
    }

    @Test
    fun uniqueBaseCountsUp() {
        val dir = Files.createTempDirectory("waddle").toFile()
        try {
            assertEquals("clip", Storage.uniqueBase(dir, "clip"))
            File(dir, "clip.mp4").createNewFile()
            assertEquals("clip (2)", Storage.uniqueBase(dir, "clip"))
            File(dir, "list 1.mp4").createNewFile()
            assertEquals("list (2)", Storage.uniqueBase(dir, "list", multi = true))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun partialsAreNotMedia() {
        assertTrue(Storage.isMedia(File("video.mp4")))
        assertFalse(Storage.isMedia(File("video.mp4.part")))
        assertFalse(Storage.isMedia(File("video.f137.mp4")))
        assertFalse(Storage.isMedia(File(".hidden.mp4")))
    }

    @Test
    fun escapeTemplate() {
        assertEquals("100%% fun", Storage.escapeTemplate("100% fun"))
    }
}
