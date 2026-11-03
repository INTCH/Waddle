package io.github.iefedev.waddle.download

import io.github.iefedev.waddle.engine.Section
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadsTest {
    @Test
    fun itemSpecFoldsRuns() {
        assertEquals("1-5,7,9-12", Downloads.itemSpec(listOf(12, 1, 2, 3, 4, 5, 7, 9, 10, 11, 11)))
        assertEquals("3", Downloads.itemSpec(listOf(3)))
    }

    @Test
    fun parseItemsRoundTrips() {
        val items = listOf(1, 2, 3, 7, 9, 10)
        assertEquals(items, Downloads.parseItems(Downloads.itemSpec(items)))
        assertNull(Downloads.parseItems(null))
        assertNull(Downloads.parseItems("x"))
    }

    @Test
    fun parseSection() {
        assertEquals(Section(80, 125), Downloads.parseSection("80-125"))
        assertNull(Downloads.parseSection("80"))
        assertNull(Downloads.parseSection("a-b"))
        assertNull(Downloads.parseSection(null))
    }
}
