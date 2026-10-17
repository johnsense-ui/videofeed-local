package br.gohan.videofeed.service

import br.gohan.videofeed.config.R2Config
import br.gohan.videofeed.db.UserTable
import br.gohan.videofeed.db.VideoTable
import br.gohan.videofeed.dto.FeedResponse
import br.gohan.videofeed.dto.PresignResponse
import br.gohan.videofeed.dto.VideoDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import java.time.Duration
import java.time.Instant
import java.util.UUID

class VideoService(private val thumbnailService: ThumbnailService) {
    private val scope = CoroutineScope(Dispatchers.IO)

    /** Generates a short-lived upload URL so the app can push the video file directly to cloud storage (R2/S3)
     * without routing the raw bytes through this server. The app uploads to that URL, then calls createVideo. */
    fun presign(filename: String): PresignResponse {
        val videoKey = "videos/${UUID.randomUUID()}/$filename"
        val presignRequest = PutObjectPresignRequest.builder()
            .signatureDuration(Duration.ofMinutes(15))
            .putObjectRequest(
                PutObjectRequest.builder()
                    .bucket(R2Config.bucket)
                    .key(videoKey)
                    .build()
            )
            .build()
        val url = R2Config.presigner.presignPutObject(presignRequest).url().toString()
        return PresignResponse(uploadUrl = url, videoKey = videoKey)
    }

    /** Saves the video metadata (title, CDN URL, uploader) to the database.
     * Thumbnail extraction runs in the background so the response is returned immediately without making the app wait. */
    fun createVideo(videoKey: String, title: String, uploaderId: String): VideoDto {
        val cdnUrl = "${R2Config.publicUrl}/$videoKey"
        val uploaderUuid = UUID.fromString(uploaderId)

        val videoId = transaction {
            VideoTable.insert {
                it[VideoTable.videoKey] = videoKey
                it[VideoTable.title] = title
                it[VideoTable.cdnUrl] = cdnUrl
                it[VideoTable.uploaderId] = uploaderUuid
                it[VideoTable.createdAt] = Instant.now()
            } get VideoTable.id
        }

        val uploaderName = transaction {
            UserTable.selectAll()
                .where { UserTable.id eq uploaderUuid }
                .single()[UserTable.email]
                .substringBefore("@")
        }

        // Generate thumbnail asynchronously — does not block the response
        scope.launch {
            val thumbnailKey = thumbnailService.extractAndUpload(videoKey)
            val thumbnailUrl = "${R2Config.publicUrl}/$thumbnailKey"
            transaction {
                VideoTable.update({ VideoTable.id eq videoId }) {
                    it[VideoTable.thumbnailUrl] = thumbnailUrl
                }
            }
        }

        return VideoDto(
            id = videoId.toString(),
            title = title,
            cdnUrl = cdnUrl,
            thumbnailUrl = null, // populated asynchronously
            uploaderName = uploaderName
        )
    }

    /** Returns a paginated list of videos ordered by newest first.
     * Fetches one extra row to know if there is a next page (same pattern as Android Paging 3's "load size + 1" trick). */
    fun getFeed(page: Int, limit: Int): FeedResponse {
        val offset = ((page - 1) * limit).toLong()
        val rows = transaction {
            VideoTable
                .join(UserTable, JoinType.LEFT, VideoTable.uploaderId, UserTable.id)
                .selectAll()
                .orderBy(VideoTable.createdAt, SortOrder.DESC)
                .limit(limit + 1)
                .offset(offset)
                .toList()
        }
        val hasMore = rows.size > limit
        val videos = rows.take(limit).map { row ->
            VideoDto(
                id = row[VideoTable.id].toString(),
                title = row[VideoTable.title],
                cdnUrl = row[VideoTable.cdnUrl],
                thumbnailUrl = row[VideoTable.thumbnailUrl],
                uploaderName = row[UserTable.email].substringBefore("@")
            )
        }
        return FeedResponse(videos = videos, page = page, hasMore = hasMore)
    }

    /** Looks up a single video by ID, or returns null if not found. */
    fun getVideo(id: String): VideoDto? = try {
        val uuid = UUID.fromString(id)
        transaction {
            VideoTable
                .join(UserTable, JoinType.LEFT, VideoTable.uploaderId, UserTable.id)
                .selectAll()
                .where { VideoTable.id eq uuid }
                .singleOrNull()
                ?.let { row ->
                    VideoDto(
                        id = row[VideoTable.id].toString(),
                        title = row[VideoTable.title],
                        cdnUrl = row[VideoTable.cdnUrl],
                        thumbnailUrl = row[VideoTable.thumbnailUrl],
                        uploaderName = row[UserTable.email].substringBefore("@")
                    )
                }
        }
    } catch (e: IllegalArgumentException) {
        null
    }
}
