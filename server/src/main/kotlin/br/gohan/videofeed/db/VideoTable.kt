package br.gohan.videofeed.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object VideoTable : Table("videos") {
    val id = uuid("id").autoGenerate()
    val title = varchar("title", 255)
    val videoKey = varchar("video_key", 512)
    val cdnUrl = varchar("cdn_url", 512)
    val thumbnailUrl = varchar("thumbnail_url", 512).nullable()
    val uploaderId = uuid("uploader_id").references(UserTable.id)
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}
