package com.example.resouretree.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.resouretree.data.local.dao.NodeDao
import com.example.resouretree.data.local.entity.MetadataEntity
import com.example.resouretree.data.local.entity.NodeEntity

@Database(entities = [NodeEntity::class, MetadataEntity::class], version = 2, exportSchema = true)
abstract class ResourceDatabase : RoomDatabase() {
    abstract fun nodes(): NodeDao
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE nodes ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
