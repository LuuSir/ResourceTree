package com.example.resouretree

import android.app.Application
import androidx.room.Room
import com.example.resouretree.data.local.database.ResourceDatabase
import com.example.resouretree.data.repository.NodeRepository
import com.example.resouretree.data.transfer.DocumentStore
import com.example.resouretree.domain.action.*
import com.example.resouretree.data.apps.AndroidAppCatalog

class ResourceTreeApplication : Application() {
    val database by lazy { Room.databaseBuilder(this, ResourceDatabase::class.java, "resource-tree.db")
        .addMigrations(ResourceDatabase.MIGRATION_1_2, ResourceDatabase.MIGRATION_2_3).build() }
    val repository by lazy { NodeRepository(database) }
    val documents by lazy { DocumentStore(contentResolver, java.io.File(filesDir, "media")) }
    val appCatalog by lazy { AndroidAppCatalog(this) }
    val clipboardDrafts by lazy { com.example.resouretree.data.clipboard.ClipboardDraftInbox(this) }
    val executor by lazy { ActionExecutor(AndroidClipboardWriter(this), AndroidPackageLauncher(this), AndroidContentSharer(this)) }
}
