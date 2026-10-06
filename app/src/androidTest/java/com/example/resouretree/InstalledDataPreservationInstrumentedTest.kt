package com.example.resouretree

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest
import android.os.Bundle

/** Compares the installed user's data across an upgrade without writing target application data. */
@RunWith(AndroidJUnit4::class)
class InstalledDataPreservationInstrumentedTest {
    @Test fun installedTreeAndRulesSurviveUpdate() {
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
        val arguments = InstrumentationRegistry.getArguments()
        if (arguments.getString("preservationMode") != "snapshot") {
            for ((key, value) in current) assertEquals("Installed data changed: $key", arguments.getString(key), value)
        }
        instrumentation.addResults(Bundle().apply { current.forEach { (key, value) -> putString(key, value) } })
    }
}
