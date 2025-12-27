package com.sample.image_board.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ThreadResponse(
        val id: String,
        val title: String,
        val caption: String? = null,
        @SerialName("image_url") val imageUrl: String,
        @SerialName("user_id") val userId: String,
        @SerialName("created_at") val createdAt: String,
        val profiles: ProfileUsername? = null
)

@Serializable data class ProfileUsername(val username: String? = null)

data class ThreadWithUser(
        val id: String,
        val title: String,
        val content: String,
        val imageUrl: String,
        val userId: String,
        val createdAt: String,
        val commentCount: Int,
        val userName: String
)

fun ThreadResponse.toThreadWithUser() =
        ThreadWithUser(
                id = id,
                title = title,
                content = caption ?: "", // caption bisa null, default empty string
                imageUrl = imageUrl,
                userId = userId,
                createdAt = createdAt,
                commentCount = 0, // Akan dihitung manual dari query comments
                userName = profiles?.username ?: "Anonymous"
        )

@Serializable
data class Thread(
        val id: String,
        val title: String,
        val content: String,
        @SerialName("image_url") val imageUrl: String? = null,
        @SerialName("user_id") val userId: String,
        @SerialName("created_at") val createdAt: String
)

@Serializable
data class Comment(
        val id: String,
        @SerialName("thread_id") val threadId: String,
        @SerialName("user_id") val userId: String,
        val content: String,
        @SerialName("created_at") val createdAt: String,
        val userName: String
)

@Serializable
data class CommentResponse(
        val id: String,
        @SerialName("thread_id") val threadId: String,
        @SerialName("user_id") val userId: String,
        val content: String,
        @SerialName("created_at") val createdAt: String,
        val profiles: ProfileUsername? = null
)

fun CommentResponse.toComment() =
        Comment(
                id = id,
                threadId = threadId,
                userId = userId,
                content = content,
                createdAt = createdAt,
                userName = profiles?.username ?: "Anonymous"
        )

@Serializable
data class Profile(
        val id: String,
        val username: String,
        @SerialName("full_name") val fullName: String? = null,
        @SerialName("avatar_url") val avatarUrl: String? = null,
        val role: String = "member" // member, admin, moderator
)

// Extended models untuk UI dengan user permissions
data class CommentWithPermissions(
        val id: String,
        val threadId: String,
        val userId: String,
        val content: String,
        val createdAt: String,
        val userName: String,
        val canDelete: Boolean // User bisa delete comment ini?
)

data class ThreadWithPermissions(
        val id: String,
        val title: String,
        val content: String,
        val imageUrl: String,
        val userId: String,
        val createdAt: String,
        val commentCount: Int,
        val userName: String,
        val canDelete: Boolean, // User bisa delete thread ini?
        val canModerate: Boolean // User bisa hapus comment di thread ini?
)

fun Comment.toCommentWithPermissions(
        currentUserId: String,
        threadOwnerId: String,
        isAdmin: Boolean
) =
        CommentWithPermissions(
                id = id,
                threadId = threadId,
                userId = userId,
                content = content,
                createdAt = createdAt,
                userName = userName,
                canDelete = userId == currentUserId || threadOwnerId == currentUserId || isAdmin
        )

fun ThreadWithUser.toThreadWithPermissions(currentUserId: String, isAdmin: Boolean) =
        ThreadWithPermissions(
                id = id,
                title = title,
                content = content,
                imageUrl = imageUrl,
                userId = userId,
                createdAt = createdAt,
                commentCount = commentCount,
                userName = userName,
                canDelete = userId == currentUserId || isAdmin,
                canModerate = userId == currentUserId || isAdmin // Thread owner bisa moderate
        )

@Serializable
data class ProfileWithRole(
        val id: String,
        val username: String,
        @SerialName("full_name") val fullName: String? = null,
        @SerialName("avatar_url") val avatarUrl: String? = null
)
