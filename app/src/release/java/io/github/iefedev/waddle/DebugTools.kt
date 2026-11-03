package io.github.iefedev.waddle

import android.content.Context

/** Release stand-in for the debug-only developer tools: nothing is shown and nothing runs. */
object DebugTools {
    const val AVAILABLE = false
    const val SECTION_TITLE = ""
    const val SEED_TITLE = ""
    const val SEED_HINT = ""
    const val SEEDED_MESSAGE = ""

    @Suppress("UNUSED_PARAMETER")
    fun seedDemoLibrary(context: Context) = Unit
}
