package com.example.resouretree.data.local.entity

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.resouretree.domain.model.*
import kotlinx.serialization.json.*

@Entity(tableName = "nodes", indices = [Index("parentId")], foreignKeys = [ForeignKey(
    entity = NodeEntity::class, parentColumns = ["id"], childColumns = ["parentId"],
    onDelete = ForeignKey.CASCADE, deferred = true
)])
data class NodeEntity(
    @PrimaryKey val id: String,
    val parentId: String?,
    val type: String,
    val name: String,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val contentType: String,
    val contentText: String,
    val contentPath: String,
    val contentMimeType: String,
    val tagsJson: String,
    val actionType: String,
    val actionTarget: String,
    @ColumnInfo(defaultValue = "0") val isPinned: Boolean = false
)

@Entity(tableName = "metadata")
data class MetadataEntity(@PrimaryKey val key: String, val value: String)

object TagsCodec {
    fun encode(tags: List<String>): String = JsonArray(tags.map(::JsonPrimitive)).toString()
    fun decode(json: String): List<String> = Json.parseToJsonElement(json).jsonArray.map { it.jsonPrimitive.content }
    fun parseInput(text: String): List<String> = text.split(',', '，').map(String::trim).filter(String::isNotEmpty).distinct()
}

fun NodeEntity.toDomain() = ResourceNode(id, parentId, NodeType.valueOf(type), name, sortOrder,
    createdAt, updatedAt, ResourceContent(ContentType.valueOf(contentType), contentText, contentPath, contentMimeType), TagsCodec.decode(tagsJson),
    ResourceAction(ActionType.valueOf(actionType), actionTarget), isPinned)

fun ResourceNode.toEntity() = NodeEntity(id, parentId, type.name, name, sortOrder, createdAt,
    updatedAt, content.type.name, content.text, content.path, content.mimeType, TagsCodec.encode(tags), action.type.name, action.target, isPinned)
