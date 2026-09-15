package com.example.resouretree

import android.app.Application
import androidx.room.Room
import com.example.resouretree.data.local.database.ResourceDatabase
import com.example.resouretree.data.repository.NodeRepository
import com.example.resouretree.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RoomRepositoryTest {
    private lateinit var db: ResourceDatabase
    private lateinit var repository: NodeRepository
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), ResourceDatabase::class.java).build()
        repository = NodeRepository(db)
    }
    @After fun close() { db.close() }
    @Test fun v1AndV2ImportsWithSameRootNameAppendIndependentTreesWithoutOverwriting() = runTest {
        val root = ResourceNode(UUID.randomUUID().toString(), null, NodeType.FOLDER, "哔哩哔哩", isPinned = true)
        val child = ResourceNode(UUID.randomUUID().toString(), root.id, NodeType.ITEM, "原条目", content = ResourceContent(text = "原内容"))
        repository.save(root, true); repository.save(child, true)
        val original = repository.all.first().associateBy { it.id }
        val v2 = repository.exportJson()
        repository.importJson("""{"schemaVersion":1,"roots":[{"id":"${root.id}","type":"folder","name":"哔哩哔哩","children":[{"id":"${child.id}","type":"item","name":"导入条目","content":"新内容"}]}]}""")
        repository.importJson(v2)
        val all = repository.all.first()
        assertEquals(6, all.size)
        val roots = all.filter { it.parentId == null }
        assertEquals(3, roots.size); assertTrue(roots.all { it.name == "哔哩哔哩" })
        assertEquals(6, all.map { it.id }.toSet().size)
        original.forEach { (id, old) -> assertEquals(old, all.single { it.id == id }) }
        roots.forEach { importedRoot -> assertEquals(1, all.count { it.parentId == importedRoot.id }) }
    }
    @Test fun initializationIsOnceEvenAfterAllNodesDeleted() = runTest {
        repository.initialize(); val initial = repository.all.first()
        assertEquals(3, initial.size)
        repository.initialize(); assertEquals(initial, repository.all.first())
        repository.delete(initial.single { it.parentId == null }.id)
        repository.initialize(); assertTrue(repository.all.first().isEmpty())
    }
    @Test fun subtreeDeleteIsAtomicAndPreservesSibling() = runTest {
        repository.initialize()
        val sibling = ResourceNode(UUID.randomUUID().toString(), null, NodeType.FOLDER, "保留")
        repository.save(sibling, true)
        val root = repository.all.first().single { it.parentId == null && it.id != sibling.id }
        repository.delete(root.id)
        assertEquals(listOf(sibling.id), repository.all.first().map { it.id })
    }
    @Test fun invalidImportDoesNotWriteValidPrefix() = runTest {
        repository.initialize(); val initial = repository.all.first()
        try {
            repository.importJson("""{"schemaVersion":1,"roots":[{"type":"folder","name":"好"},{"type":"bad","name":"坏"}]}""")
            fail("Should reject")
        } catch (_: IllegalArgumentException) { }
        assertEquals(initial, repository.all.first())
    }
    @Test fun insertionFailureRollsBackEntireImport() = runTest {
        repository.initialize(); val initial = repository.all.first()
        // Force the second insertion to fail, exercising a real SQLite transaction rollback.
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_test_node BEFORE INSERT ON nodes WHEN NEW.name = '拒绝' BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        try {
            repository.importJson("""{"schemaVersion":1,"roots":[{"type":"folder","name":"通过"},{"type":"folder","name":"拒绝"}]}""")
            fail("Should reject")
        } catch (_: android.database.sqlite.SQLiteException) { }
        assertEquals(initial, repository.all.first())
    }
    @Test fun databaseExportImportRetainsHierarchyAndActions() = runTest {
        repository.initialize()
        val initial = repository.all.first()
        assertEquals(3, repository.importJson(repository.exportJson()))
        val all = repository.all.first()
        assertEquals(6, all.map { it.id }.distinct().size); Tree.validate(all)
        val items = all.filter { it.type == NodeType.ITEM }
        assertEquals(2, items.size); assertEquals(items[0].action, items[1].action)
        assertEquals(initial.filter { it.type == NodeType.ITEM }.single().tags, items.last().tags)
    }
    @Test fun createEditMoveAndSearchStayConsistent() = runTest {
        val folder = ResourceNode(UUID.randomUUID().toString(), null, NodeType.FOLDER, "目录")
        repository.save(folder, true)
        val item = ResourceNode(UUID.randomUUID().toString(), folder.id, NodeType.ITEM, "条目", content = ResourceContent(text = "ABC123"), tags = listOf("三国"))
        repository.save(item, true)
        assertEquals(listOf(item.id), repository.children(folder.id).first().map { it.id })
        assertEquals(1, repository.search("三国").first().size)
        assertEquals(1, repository.search("abc123").first().size)
        assertEquals(1, repository.search("条目").first().size)
        assertTrue(repository.search("%_").first().isEmpty())
        repository.save(item.copy(name = "改名"), false)
        assertEquals("改名", repository.node(item.id)?.name)
        repository.move(item.id, null)
        assertTrue(repository.children(folder.id).first().isEmpty())
        assertNull(repository.node(item.id)?.parentId)
    }
    @Test fun moveCycleRejectedWithoutMutation() = runTest {
        repository.initialize(); val initial = repository.all.first()
        val root = initial.single { it.parentId == null }; val child = initial.single { it.parentId == root.id }
        try { repository.move(root.id, child.id); fail("Should reject") } catch (_: IllegalArgumentException) { }
        assertEquals(initial, repository.all.first())
    }
    @Test fun foreignKeyPreventsOrphans() = runTest {
        repository.initialize()
        db.nodes().deleteNode(repository.all.first().single { it.parentId == null }.id)
        assertTrue(repository.all.first().isEmpty())
    }
    @Test fun batchCopyClonesSubtreesWithIndependentIdsAndNames() = runTest {
        repository.initialize()
        val before = repository.all.first()
        val root = before.single { it.parentId == null }
        val item = before.single { it.type == NodeType.ITEM }
        assertEquals(1, repository.copyMany(setOf(root.id, item.id), null))
        val all = repository.all.first()
        assertEquals(6, all.size)
        val copy = all.single { it.name == "哔哩哔哩（副本）" }
        val copiedIds = Tree.descendants(all, copy.id)
        assertEquals(3, copiedIds.size)
        assertTrue(copiedIds.intersect(before.map { it.id }.toSet()).isEmpty())
        val copiedItem = all.single { it.id in copiedIds && it.type == NodeType.ITEM }
        assertEquals(item.content, copiedItem.content); assertEquals(item.tags, copiedItem.tags)
        assertEquals(item.action, copiedItem.action)
        repository.copyMany(setOf(root.id), null)
        assertTrue(repository.all.first().any { it.name == "哔哩哔哩（副本 2）" })
        repository.deleteMany(setOf(copy.id, copiedItem.id))
        assertEquals(before, repository.all.first().filter { it.id in before.map { n -> n.id } })
    }
    @Test fun batchMoveKeepsNestedStructureAndAppendsToDestination() = runTest {
        repository.initialize(); val before = repository.all.first()
        val root = before.single { it.parentId == null }
        val item = before.single { it.type == NodeType.ITEM }
        val destination = ResourceNode(UUID.randomUUID().toString(), null, NodeType.FOLDER, "目标")
        val other = ResourceNode(UUID.randomUUID().toString(), null, NodeType.ITEM, "另一项")
        repository.save(destination, true); repository.save(other, true)
        assertEquals(2, repository.moveMany(setOf(root.id, item.id, other.id), destination.id))
        assertEquals(destination.id, repository.node(root.id)?.parentId)
        assertEquals(item.parentId, repository.node(item.id)?.parentId)
        assertEquals(2, repository.children(destination.id).first().size)
        Tree.validate(repository.all.first())
    }
    @Test fun invalidBatchDestinationDoesNotMoveAnySelection() = runTest {
        repository.initialize(); val before = repository.all.first()
        val root = before.single { it.parentId == null }
        val child = before.single { it.parentId == root.id }
        try { repository.moveMany(setOf(root.id, child.id), child.id); fail("Cycle") } catch (_: IllegalArgumentException) { }
        try { repository.moveMany(setOf(root.id), "missing"); fail("Missing") } catch (_: IllegalArgumentException) { }
        assertEquals(before, repository.all.first())
    }
    @Test fun copyingIntoDescendantUsesFiniteSnapshot() = runTest {
        repository.initialize(); val before = repository.all.first()
        val root = before.single { it.parentId == null }; val child = before.single { it.parentId == root.id }
        repository.copyMany(setOf(root.id), child.id)
        assertEquals(6, repository.all.first().size)
        Tree.validate(repository.all.first())
    }
    @Test fun batchCopyFailureRollsBackEveryCopy() = runTest {
        repository.initialize(); val before = repository.all.first()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_copy BEFORE INSERT ON nodes WHEN NEW.type = 'ITEM' BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        try { repository.copyMany(setOf(before.single { it.parentId == null }.id), null); fail("Must roll back") }
        catch (_: android.database.sqlite.SQLiteException) { }
        assertEquals(before, repository.all.first())
    }
    @Test fun batchMoveAndDeleteFailureRollBackEarlierChanges() = runTest {
        val first = ResourceNode(UUID.randomUUID().toString(), null, NodeType.ITEM, "第一")
        val second = ResourceNode(UUID.randomUUID().toString(), null, NodeType.ITEM, "第二")
        val destination = ResourceNode(UUID.randomUUID().toString(), null, NodeType.FOLDER, "目标")
        listOf(first, second, destination).forEach { repository.save(it, true) }
        val before = repository.all.first()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_move BEFORE UPDATE ON nodes WHEN NEW.name = '第二' BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        try { repository.moveMany(setOf(first.id, second.id), destination.id); fail("Must roll back") }
        catch (_: android.database.sqlite.SQLiteException) { }
        assertEquals(before, repository.all.first())
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_delete BEFORE DELETE ON nodes WHEN OLD.name = '第二' BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        try { repository.deleteMany(setOf(first.id, second.id)); fail("Must roll back") }
        catch (_: android.database.sqlite.SQLiteException) { }
        assertEquals(before, repository.all.first())
    }
    @Test fun staleOrEmptyBatchSelectionIsRejected() = runTest {
        repository.initialize(); val before = repository.all.first()
        val root = before.single { it.parentId == null }
        try { repository.deleteMany(setOf(root.id, "missing")); fail("Stale selection") } catch (_: IllegalArgumentException) { }
        try { repository.copyMany(emptySet(), null); fail("Empty selection") } catch (_: IllegalArgumentException) { }
        assertEquals(before, repository.all.first())
    }
    @Test fun manualOrderAndPinSurviveReadAndExport() = runTest {
        val items = (1..4).map { ResourceNode(UUID.randomUUID().toString(), null, NodeType.ITEM, "条目$it") }
        items.forEach { repository.save(it, true) }
        val reverse = items.map { it.id }.reversed()
        repository.reorder(null, reverse)
        assertEquals(reverse, repository.children(null).first().map { it.id })
        repository.setPinned(items[1].id, true)
        repository.setPinned(items[0].id, true)
        val pinned = repository.children(null).first()
        assertEquals(listOf(items[0].id, items[1].id), pinned.take(2).map { it.id })
        val newOrder = listOf(items[1].id, items[0].id, items[2].id, items[3].id)
        repository.reorder(null, newOrder)
        assertEquals(newOrder, repository.children(null).first().map { it.id })
        val codec = com.example.resouretree.data.transfer.TreeJson()
        val exported = codec.toNodes(codec.decode(repository.exportJson()))
        assertEquals(repository.all.first(), exported)
        repository.setPinned(items[1].id, false)
        assertEquals(items[0].id, repository.children(null).first().first().id)
        assertFalse(requireNotNull(repository.node(items[1].id)).isPinned)
    }
    @Test fun reorderRejectsStaleCrossFolderAndPinnedBoundary() = runTest {
        val a = ResourceNode(UUID.randomUUID().toString(), null, NodeType.ITEM, "A")
        val b = ResourceNode(UUID.randomUUID().toString(), null, NodeType.ITEM, "B")
        listOf(a, b).forEach { repository.save(it, true) }
        repository.setPinned(a.id, true)
        val before = repository.all.first()
        for (ids in listOf(listOf(b.id, a.id), listOf(a.id), listOf(a.id, a.id), listOf(a.id, "missing"))) {
            try { repository.reorder(null, ids); fail("Invalid order") } catch (_: IllegalArgumentException) { }
        }
        try { repository.reorder("other", listOf(a.id, b.id)); fail("Cross directory") } catch (_: IllegalArgumentException) { }
        assertEquals(before, repository.all.first())
    }
    @Test fun migrationFromVersionOnePreservesResources() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val fileName = "migration-${UUID.randomUUID()}.db"
        val source = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(fileName), null)
        source.use { old ->
            old.execSQL("CREATE TABLE nodes (id TEXT NOT NULL PRIMARY KEY, parentId TEXT, type TEXT NOT NULL, name TEXT NOT NULL, sortOrder INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, content TEXT NOT NULL, tagsJson TEXT NOT NULL, actionType TEXT NOT NULL, actionText TEXT NOT NULL, packageName TEXT NOT NULL, FOREIGN KEY(parentId) REFERENCES nodes(id) ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED)")
            old.execSQL("CREATE INDEX index_nodes_parentId ON nodes(parentId)")
            old.execSQL("CREATE TABLE metadata (`key` TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)")
            old.execSQL("INSERT INTO nodes VALUES ('legacy', NULL, 'ITEM', '已有资源', 7, 1, 2, '旧内容', '[\"历史\"]', 'COPY', '独立动作文本', '')")
            old.execSQL("INSERT INTO metadata VALUES ('demo_initialized', 'true')")
            old.version = 1
        }
        val migrated = Room.databaseBuilder(context, ResourceDatabase::class.java, fileName)
            .addMigrations(ResourceDatabase.MIGRATION_1_2, ResourceDatabase.MIGRATION_2_3).build()
        try {
            val record = requireNotNull(migrated.nodes().getNode("legacy"))
            assertEquals("已有资源", record.name); assertEquals("旧内容", record.contentText)
            assertEquals("TEXT", record.contentType); assertEquals(7, record.sortOrder)
            assertFalse(record.isPinned)
            assertEquals("true", migrated.nodes().metadata("demo_initialized"))
        } finally { migrated.close(); context.deleteDatabase(fileName) }
    }
}
