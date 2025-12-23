package com.sample.image_board.data.repository

import android.content.Context
import android.net.Uri
import com.sample.image_board.data.model.Comment
import com.sample.image_board.data.model.CommentResponse
import com.sample.image_board.data.model.Result
import com.sample.image_board.data.model.ThreadResponse
import com.sample.image_board.data.model.ThreadWithUser
import com.sample.image_board.data.model.toComment
import com.sample.image_board.data.model.toThreadWithUser
import com.sample.image_board.utils.ImageCompressor
import com.sample.image_board.utils.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage

class ThreadRepository {

    private val supabase = SupabaseClient.client

    suspend fun getThreadsPaginated(offset: Int = 0, limit: Int = 20): Result<List<ThreadWithUser>> {
        return try {
            val response = supabase
                .from("threads")
                .select(
                    columns = Columns.raw("""
                        id,
                        title,
                        caption,
                        image_url,
                        user_id,
                        created_at,
                        profiles!inner(username)
                    """.trimIndent())
                ) {
                    // Reverse-chronological: Terbaru di atas
                    order("created_at", order = Order.DESCENDING)

                    // Pagination
                    range(offset.toLong(), (offset + limit - 1).toLong())
                }
            Result.Success(response.decodeList<ThreadResponse>().map { it.toThreadWithUser() })
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Ambil semua thread dengan info user (JOIN profiles)
     * DEPRECATED: Gunakan getThreadsPaginated() untuk performa lebih baik
     */
    @Deprecated("Use getThreadsPaginated() instead", ReplaceWith("getThreadsPaginated()"))
    suspend fun getAllThreads(): Result<List<ThreadWithUser>> {
        return getThreadsPaginated(offset = 0, limit = 100)
    }

    /**
     * Ambil detail 1 thread by ID
     */
    suspend fun getThreadById(threadId: String): Result<ThreadWithUser> {
        return try {
            val response = supabase
                .from("threads")
                .select(
                    columns = Columns.raw("""
                        id,
                        title,
                        caption,
                        image_url,
                        user_id,
                        created_at,
                        profiles!inner(username)
                    """.trimIndent())
                ) {
                    filter {
                        eq("id", threadId)
                    }
                    single()
                }
            Result.Success(response.decodeAs<ThreadResponse>().toThreadWithUser())
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Ambil semua comment untuk 1 thread
     */
    suspend fun getCommentsByThreadId(threadId: String): Result<List<Comment>> {
        return try {
            val response = supabase
                .from("comments")
                .select(
                    columns = Columns.raw("""
                        id,
                        thread_id,
                        user_id,
                        content,
                        created_at,
                        profiles!inner(username)
                    """.trimIndent())
                ) {
                    filter {
                        eq("thread_id", threadId)
                    }
                    order("created_at", order = Order.ASCENDING)
                }
            Result.Success(response.decodeList<CommentResponse>().map { it.toComment() })
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Get comment count untuk 1 thread
     */
    suspend fun getCommentCount(threadId: String): Result<Int> {
        return try {
            val response = supabase
                .from("comments")
                .select(
                    columns = Columns.raw("id") // Select minimal columns
                ) {
                    filter {
                        eq("thread_id", threadId)
                    }
                }
            Result.Success(response.decodeList<CommentResponse>().size)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Get comment counts untuk multiple threads (batch)
     * Using a single query with filter to get all comments at once
     */
    suspend fun getCommentCounts(threadIds: List<String>): Result<Map<String, Int>> {
        if (threadIds.isEmpty()) return Result.Success(emptyMap())

        return try {
            // Fetch all comments for the given thread IDs in one query
            val response = supabase
                .from("comments")
                .select(
                    columns = Columns.raw("""
                        id,
                        thread_id,
                        user_id,
                        content,
                        created_at
                    """.trimIndent())
                ) {
                    filter {
                        // Use 'in' filter to get comments for multiple threads
                        isIn("thread_id", threadIds)
                    }
                }

            // Decode and count by thread_id
            val comments = response.decodeList<CommentResponse>()
            val counts = comments.groupingBy { it.threadId }.eachCount()

            // Create result map with 0 for threads with no comments
            val result = threadIds.associateWith { threadId ->
                counts[threadId] ?: 0
            }

            Result.Success(result)
        } catch (e: Exception) {
            // Log error atau handle sesuai kebutuhan
            println("Error fetching comment counts: ${e.message}")
            Result.Error(e)
        }
    }

    /**
     * Posting comment baru
     */
    suspend fun postComment(threadId: String, content: String): Result<Unit> {
        return try {
            val userId = supabase.auth.currentSessionOrNull()?.user?.id
                ?: throw Exception("User belum login")

            supabase.from("comments").insert(
                mapOf(
                    "thread_id" to threadId,
                    "user_id" to userId,
                    "content" to content
                )
            )
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Delete comment
     * Rules (enforced by RLS):
     * - User bisa hapus own comment
     * - Thread owner bisa hapus comment di thread nya (moderasi)
     * - Admin bisa hapus any comment
     */
    suspend fun deleteComment(commentId: String): Result<Unit> {
        return try {
            supabase.from("comments").delete {
                filter {
                    eq("id", commentId)
                }
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Delete thread
     * Rules (enforced by RLS):
     * - User bisa hapus own thread
     * - Admin bisa hapus any thread
     */
    suspend fun deleteThread(threadId: String): Result<Unit> {
        return try {
            supabase.from("threads").delete {
                filter {
                    eq("id", threadId)
                }
            }
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Buat thread baru dengan gambar (ByteArray yang sudah dikompres)
     * imageData WAJIB karena ini image board
     *
     * @param title Judul thread (wajib)
     * @param caption Caption/deskripsi (opsional)
     * @param imageData ByteArray gambar yang sudah dikompres
     */
    suspend fun createThread(title: String, caption: String?, imageData: ByteArray): Result<Unit> {
        return try {
            val userId = supabase.auth.currentSessionOrNull()?.user?.id
                ?: throw Exception("User belum login")

            // 1. Upload image ke Storage
            val fileName = "img_${userId}_${System.currentTimeMillis()}.jpg"
            val imageUrlResult = uploadImageBytes(fileName, imageData)

            if (imageUrlResult is Result.Success) {
                // 2. Insert thread ke Database dengan image URL
                supabase.from("threads").insert(
                    mapOf(
                        "title" to title,
                        "caption" to caption,
                        "image_url" to imageUrlResult.data,
                        "user_id" to userId
                    )
                )
                Result.Success(Unit)
            } else {
                Result.Error(Exception("Failed to upload image"))
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Buat thread baru (legacy method dengan imageUrl langsung)
     * imageUrl WAJIB karena ini image board
     */
    @Deprecated("Use createThread(title, caption, imageData) instead")
    suspend fun createThreadWithUrl(title: String, caption: String?, imageUrl: String): Result<Unit> {
        return try {
            val userId = supabase.auth.currentSessionOrNull()?.user?.id
                ?: throw Exception("User belum login")

            supabase.from("threads").insert(
                mapOf(
                    "title" to title,
                    "caption" to caption,
                    "image_url" to imageUrl,
                    "user_id" to userId
                )
            )
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Upload gambar ke Supabase Storage dengan kompresi otomatis
     *
     * @param context Context aplikasi untuk akses file system
     * @param imageUri URI gambar yang akan diupload
     * @param fileName Nama file di storage (optional, auto-generate jika null)
     * @return URL publik gambar yang sudah diupload
     */
    suspend fun uploadImage(
        context: Context,
        imageUri: Uri,
        fileName: String? = null
    ): Result<String> {
        return try {
            // Kompresi gambar sebelum upload (max 500KB, quality 80%)
            val compressedBytes = ImageCompressor.compressImage(
                context = context,
                uri = imageUri,
                maxSizeKB = 500, // Max 500KB untuk avoid timeout
                quality = 80,     // Kualitas 80% (balance antara size & quality)
                maxWidth = 1024,  // Max width 1024px
                maxHeight = 1024  // Max height 1024px
            )

            // Log ukuran hasil kompresi
            val sizeKB = ImageCompressor.getFileSizeKB(compressedBytes)
            println("📦 Image compressed: ${ImageCompressor.formatFileSize(compressedBytes.size.toLong())} ($sizeKB KB)")

            // Generate unique filename jika tidak ada
            val actualFileName = fileName ?: "img_${System.currentTimeMillis()}.jpg"

            val bucket = supabase.storage.from("images")

            // Upload file yang sudah dikompres
            bucket.upload(actualFileName, compressedBytes, upsert = false)

            // Return public URL
            Result.Success(bucket.publicUrl(actualFileName))
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Upload gambar dari ByteArray langsung (legacy method)
     * Untuk backward compatibility
     */
    suspend fun uploadImageBytes(fileName: String, byteArray: ByteArray): Result<String> {
        return try {
            val bucket = supabase.storage.from("images")
            bucket.upload(fileName, byteArray, upsert = false)
            Result.Success(bucket.publicUrl(fileName))
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}

