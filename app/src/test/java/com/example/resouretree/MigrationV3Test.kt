package com.example.resouretree

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.example.resouretree.data.local.database.ResourceDatabase
import com.example.resouretree.data.repository.NodeRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MigrationV3Test {
    @Test fun v2TreeMetadataOrderPinsAndFallbackSurviveWithoutLegacyColumns() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "v2-migration-${UUID.randomUUID()}.db"
        context.getDatabasePath(name).parentFile!!.mkdirs()
        val schema = JSONObject(File("schemas/com.example.resouretree.data.local.database.ResourceDatabase/2.json").readText()).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
            }
            old.execSQL("INSERT INTO nodes VALUES ('root',NULL,'FOLDER','目录',9,100,200,'','[]','NONE','','',1)")
            old.execSQL("INSERT INTO nodes VALUES ('child','root','ITEM','B站',8,101,201,'BV123','[\"标签\"]','COPY_AND_LAUNCH','旧副本','com.bilibili.app.in',1)")
            old.execSQL("INSERT INTO nodes VALUES ('fallback','root','ITEM','兼容',3,102,202,'','[]','COPY','兼容内容','',0)")
            old.execSQL("INSERT INTO metadata VALUES ('demo_initialized','true')")
            old.version = 2
        }
        val db = Room.databaseBuilder(context, ResourceDatabase::class.java, name).addMigrations(ResourceDatabase.MIGRATION_2_3).build()
        try {
            val repo = NodeRepository(db)
            repo.initialize()
            val nodes = repo.all.first()
            assertEquals(3, nodes.size)
            val item = nodes.single { it.id == "child" }
            assertEquals("root", item.parentId); assertEquals(8, item.sortOrder); assertTrue(item.isPinned)
            assertEquals(101L, item.createdAt); assertEquals(201L, item.updatedAt)
            assertEquals(listOf("标签"), item.tags); assertEquals("BV123", item.content.text)
            assertEquals("com.bilibili.app.in", item.action.target)
            assertEquals("兼容内容", nodes.single { it.id == "fallback" }.content.text)
            assertEquals("true", db.nodes().metadata("demo_initialized"))
            db.openHelper.writableDatabase.query("PRAGMA table_info(nodes)").use { cursor ->
                val columns = mutableSetOf<String>()
                while (cursor.moveToNext()) columns.add(cursor.getString(1))
                assertFalse(columns.any { it in setOf("content", "actionText", "packageName") })
            }
            db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
            repo.delete("root")
            assertTrue(repo.all.first().isEmpty())
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
