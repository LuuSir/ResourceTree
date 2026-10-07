package com.example.resouretree

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Pure window dump parsing needs neither a Compose host nor an ActivityScenario launch. */
@RunWith(AndroidJUnit4::class)
class OverlayWindowDumpInstrumentedTest {
    @Test fun dumpMatchesRealOverlayFramesAcrossWindowFormats() {
        val dump = """
            WINDOW MANAGER WINDOWS (dumpsys window windows)
              Window #0 Window{aa u0 com.example.resouretree/.MainActivity}:
                mAttrs={(0,0)(fillxfill) ty=BASE_APPLICATION}
                Frames: frame=[0,0][720,1600]

              Window #1 Window{bb u0 com.example.resouretree}:
                mAttrs={(552,110)(168x168) ty=APPLICATION_OVERLAY}
                Frames: parent=[0,0][720,1600] display=[0,0][720,1600] frame=[552,110][720,278]

              Window #2 Window{cc u0 com.example.otherapp}:
                mAttrs={(0,0)(200x200) ty=APPLICATION_OVERLAY}
                mFrame=[0,0][200,200]
              Window #3 Window{dd u0 com.example.resouretree}:
                mAttrs={(12,24)(80x80) ty=2038}
                mFrame=[12, 24][92, 104]
        """.trimIndent()
        assertEquals(listOf(168 to 168, 80 to 80), overlayWindowFrames(dump, "com.example.resouretree"))
    }
}

internal fun overlayWindowFrames(dump: String, packageName: String): List<Pair<Int, Int>> {
    val windows = dump.split(Regex("(?m)(?=^[ \\t]*Window #\\d+ Window\\{)"))
    val frame = Regex("\\b(?:mFrame|frame)=\\[(-?\\d+),\\s*(-?\\d+)\\]\\[(-?\\d+),\\s*(-?\\d+)\\]")
    return windows.filter {
        it.lineSequence().firstOrNull { line -> line.trimStart().startsWith("Window #") }?.contains(packageName) == true &&
            Regex("\\bty=(?:APPLICATION_OVERLAY|2038)\\b").containsMatchIn(it)
    }.mapNotNull { window ->
        frame.find(window)?.destructured?.let { (left, top, right, bottom) ->
            (right.toInt() - left.toInt()) to (bottom.toInt() - top.toInt())
        }
    }
}
