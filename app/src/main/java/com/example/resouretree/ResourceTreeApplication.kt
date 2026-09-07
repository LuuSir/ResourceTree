package com.example.resouretree

import android.app.Application
import androidx.room.Room
import com.example.resouretree.data.local.database.ResourceDatabase
import com.example.resouretree.data.repository.NodeRepository
import com.example.resouretree.data.transfer.DocumentStore
import com.example.resouretree.domain.action.*

class ResourceTreeApplication : Application() {
    val database by lazy { Room.databaseBuilder(this, ResourceDatabase::class.java, "resource-tree.db")
        .addMigrations(ResourceDatabase.MIGRATION_1_2).build() }
    val repository by lazy { NodeRepository(database) }
    val documents by lazy { DocumentStore(contentResolver) }
    val executor by lazy { ActionExecutor(AndroidClipboardWriter(this), AndroidPackageLauncher(this)) }
}
