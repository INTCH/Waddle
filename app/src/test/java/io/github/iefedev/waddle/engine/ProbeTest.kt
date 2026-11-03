package io.github.iefedev.waddle.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProbeTest {
    @Test
    fun singleVideo() {
        val p = Probe.parse("""{"title":"Sample clip","duration":95.4}""")
        assertEquals("Sample clip", p.title)
        assertEquals(95L, p.durationSec)
        assertFalse(p.multi)
        assertFalse(p.channel)
    }

    @Test
    fun playlistWithUnavailableEntry() {
        val p = Probe.parse(
            """{"title":"List","entries":[
                {"title":"One","duration":10,"thumbnail":"https://example.com/1.jpg"},
                {"title":"[Private video]"},
                {"title":"Three","thumbnails":[{"url":"https://example.com/3.jpg"}]}
            ]}""",
        )
        val entries = p.entries!!
        assertTrue(p.multi)
        assertEquals(3, entries.size)
        assertTrue(entries[0].available)
        assertFalse(entries[1].available)
        assertEquals("https://example.com/3.jpg", entries[2].thumb)
    }

    @Test
    fun playlistOfOneReadsAsThatVideo() {
        val p = Probe.parse("""{"title":"List","entries":[{"title":"Only","duration":42}]}""")
        assertEquals("Only", p.title)
        assertEquals(42L, p.durationSec)
        assertNull(p.entries)
    }

    @Test
    fun channelTabsAreNotDownloadable() {
        val p = Probe.parse("""{"title":"Channel","entries":[{"_type":"playlist","title":"Videos"}]}""")
        assertTrue(p.channel)
    }

    @Test
    fun clockParsing() {
        assertEquals(80L, Section.parseClock("1:20"))
        assertEquals(80L, Section.parseClock("80"))
        assertEquals(3723L, Section.parseClock("1:02:03"))
        assertNull(Section.parseClock("1:x"))
        assertEquals("1:02:03", Section.clock(3723))
        assertEquals("1.20-2.05", Section(80, 125).fileLabel)
    }
}
