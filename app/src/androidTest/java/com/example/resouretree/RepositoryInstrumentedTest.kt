package com.example.resouretree

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.resouretree.data.local.database.ResourceDatabase
import com.example.resouretree.data.repository.NodeRepository
import com.example.resouretree.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RepositoryInstrumentedTest {
    private lateinit var db: ResourceDatabase
    private lateinit var repository: NodeRepository
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, ResourceDatabase::class.java).build()
        repository = NodeRepository(db)
    }
    @After fun close() { db.close() }
    @Test fun demoIsNotRecreatedAfterDeletingEverything() = runTest {
        repository.initialize()
        val first = repository.all.first()
        repository.initialize()
        assertEquals(first, repository.all.first())
        first.filter { it.parentId == null }.forEach { repository.delete(it.id) }
        repository.initialize()
        assertTrue(repository.all.first().isEmpty())
    }
    @Test fun deletionRemovesDescendantsAndPreservesSiblings() = runTest {
        repository.initialize()
        val sibling = ResourceNode(UUID.randomUUID().toString(), null, NodeType.FOLDER, "保留")
        repository.save(sibling, true)
        val root = repository.all.first().first { it.parentId == null && it.id != sibling.id }
        repository.delete(root.id)
        assertEquals(listOf(sibling.id), repository.all.first().map { it.id })
    }
    @Test fun importFailureLeavesDatabaseUnchanged() = runTest {
        repository.initialize(); val before = repository.all.first()
        try {
            repository.importJson("""{"schemaVersion":1,"roots":[{"type":"folder","name":"合法"},{"type":"bad","name":"非法"}]}""")
            fail("Import should fail")
        } catch (_: IllegalArgumentException) { }
        assertEquals(before, repository.all.first())
    }
    @Test fun exportedTreeCanBeImportedTwiceWithoutIdCollisions() = runTest {
        repository.initialize(); val original = repository.all.first()
        val json = repository.exportJson()
        assertEquals(3, repository.importJson(json)); assertEquals(3, repository.importJson(json))
        val all = repository.all.first()
        assertEquals(9, all.size); assertEquals(9, all.map { it.id }.distinct().size)
        Tree.validate(all)
        assertEquals(original.single { it.type == NodeType.ITEM }.action, all.last { it.type == NodeType.ITEM }.action)
    }
    @Test fun searchMatchesNamesContentsAndTags() = runTest {
        repository.initialize()
        assertEquals(1, repository.search("三国").first().size)
        assertEquals(1, repository.search("BV1Futr6xEkb").first().size)
        assertEquals(1, repository.search("Bilibili").first().size)
        assertTrue(repository.search("%_").first().isEmpty())
    }
    @Test fun moveRejectsCyclesAndLeavesDatabaseIntact() = runTest {
        repository.initialize(); val before = repository.all.first()
        val root = before.single { it.parentId == null }; val child = before.single { it.parentId == root.id }
        try { repository.move(root.id, child.id); fail("Move should fail") } catch (_: IllegalArgumentException) { }
        assertEquals(before, repository.all.first())
    }
}
