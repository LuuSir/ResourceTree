package com.example.resouretree

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest
import android.os.Bundle

/** Compares the installed user's data across an upgrade without writing target application data. */
@RunWith(AndroidJUnit4::class)
class InstalledDataPreservationInstrumentedTest {
    @Test fun installedTreeAndRulesSurviveUpdate() {
        val arguments = InstrumentationRegistry.getArguments()
        val mode = arguments.getString("preservationMode")?.takeIf { it.isNotBlank() }
        assumeTrue("Manual upgrade check: pass preservationMode=snapshot or compare", mode != null)
        assertTrue("preservationMode must be snapshot or compare", mode == "snapshot" || mode == "compare")
        val keys = listOf("snapshotNodes", "snapshotTree", "snapshotRules", "snapshotSchema")
        if (mode == "compare") {
            val missing = keys.filter { arguments.getString(it).isNullOrBlank() }
            assertTrue("Compare requires the complete snapshot baseline; missing: ${missing.joinToString()}", missing.isEmpty())
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as ResourceTreeApplication
        val nodes = runBlocking { app.repository.all.first() }.sortedBy { it.id }
        fun digest(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val current = mapOf(
            "snapshotNodes" to nodes.size.toString(),
            "snapshotTree" to digest(nodes.joinToString("\n") { it.toString() }),
            "snapshotRules" to digest(app.clipboardRules.rules.value.joinToString("\n") { it.toString() }),
            "snapshotSchema" to app.database.openHelper.readableDatabase.version.toString()
        )
        if (mode == "compare") {
            for ((key, value) in current) assertEquals("Installed data changed: $key", arguments.getString(key), value)
        }
        instrumentation.addResults(Bundle().apply { current.forEach { (key, value) -> putString(key, value) } })
    }
}
