package com.example.resouretree.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.resouretree.data.local.dao.NodeDao
import com.example.resouretree.data.local.entity.MetadataEntity
import com.example.resouretree.data.local.entity.NodeEntity

@Database(entities = [NodeEntity::class, MetadataEntity::class], version = 3, exportSchema = true)
abstract class ResourceDatabase : RoomDatabase() {
    abstract fun nodes(): NodeDao
    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Snapshot has no foreign key: removing the old self-referencing tree cannot cascade into it.
                // Recreate with the final name instead of relying on SQLite-version-specific FK rename behavior.
                db.execSQL("""CREATE TABLE nodes_content_backup AS SELECT id, parentId, type, name, sortOrder, createdAt, updatedAt,
                    'TEXT' AS contentType, CASE WHEN content = '' THEN actionText ELSE content END AS contentText,
                    '' AS contentPath, '' AS contentMimeType, tagsJson, actionType, packageName AS actionTarget, isPinned FROM nodes""")
                db.execSQL("DROP TABLE nodes")
                db.execSQL("""CREATE TABLE nodes (
                    id TEXT NOT NULL PRIMARY KEY, parentId TEXT, type TEXT NOT NULL, name TEXT NOT NULL,
                    sortOrder INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL,
                    contentType TEXT NOT NULL, contentText TEXT NOT NULL, contentPath TEXT NOT NULL,
                    contentMimeType TEXT NOT NULL, tagsJson TEXT NOT NULL, actionType TEXT NOT NULL,
                    actionTarget TEXT NOT NULL, isPinned INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY(parentId) REFERENCES nodes(id) ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED)""")
                db.execSQL("INSERT INTO nodes SELECT * FROM nodes_content_backup")
                db.execSQL("DROP TABLE nodes_content_backup")
                db.execSQL("CREATE INDEX index_nodes_parentId ON nodes(parentId)")
            }
        }
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE nodes ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
