package com.example.resouretree.data.local.dao

import androidx.room.*
import com.example.resouretree.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NodeDao {
    @Query("SELECT * FROM nodes WHERE parentId IS :parentId ORDER BY isPinned DESC, sortOrder, createdAt, id")
    fun getChildren(parentId: String?): Flow<List<NodeEntity>>
    @Query("SELECT * FROM nodes WHERE id = :id")
    suspend fun getNode(id: String): NodeEntity?
    @Query("SELECT * FROM nodes ORDER BY isPinned DESC, sortOrder, createdAt, id")
    fun observeAll(): Flow<List<NodeEntity>>
    @Query("SELECT * FROM nodes ORDER BY isPinned DESC, sortOrder, createdAt, id")
    suspend fun getAll(): List<NodeEntity>
    @Insert suspend fun insertNode(node: NodeEntity)
    @Insert suspend fun insertNodes(nodes: List<NodeEntity>)
    @Update suspend fun updateNode(node: NodeEntity)
    @Query("DELETE FROM nodes WHERE id = :id") suspend fun deleteNode(id: String)
    @Query("SELECT * FROM nodes WHERE type = 'ITEM' AND (instr(lower(name), lower(:query)) > 0 OR instr(lower(content), lower(:query)) > 0 OR instr(lower(tagsJson), lower(:query)) > 0) ORDER BY isPinned DESC, sortOrder, createdAt, id")
    fun searchItems(query: String): Flow<List<NodeEntity>>
    @Query("SELECT value FROM metadata WHERE `key` = :key") suspend fun metadata(key: String): String?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun setMetadata(metadata: MetadataEntity)
}

