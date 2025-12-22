package com.sample.image_board.data.repository

import com.sample.image_board.data.model.Comment
import com.sample.image_board.data.model.CommentResponse
import com.sample.image_board.data.model.toComment
import com.sample.image_board.data.model.ThreadWithUser
import com.sample.image_board.data.model.ThreadResponse
import com.sample.image_board.data.model.toThreadWithUser
import com.sample.image_board.utils.SupabaseClient
import com.sample.image_board.utils.ImageCompressor
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.storage.storage
import android.content.Context
import android.net.Uri

class ThreadRepository {

    private val supabase = SupabaseClient.client

    /**
     * Ambil thread dengan pagination (untuk infinite scroll)
     * @param offset Mulai dari index berapa
     * @param limit Berapa banyak data yang diambil (default 20)
     */
    suspend fun getThreadsPaginated(offset: Int = 0, limit: Int = 20): List<ThreadWithUser> {
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

        // Parse response and convert to ThreadWithUser
        return response.decodeList<ThreadResponse>().map { it.toThreadWithUser() }
    }

    /**
     * Ambil semua thread dengan info user (JOIN profiles)
     * DEPRECATED: Gunakan getThreadsPaginated() untuk performa lebih baik
     */
    @Deprecated("Use getThreadsPaginated() instead", ReplaceWith("getThreadsPaginated()"))
    suspend fun getAllThreads(): List<ThreadWithUser> {
        return getThreadsPaginated(offset = 0, limit = 100)
    }

    /**
     * Ambil detail 1 thread by ID
     */
    suspend fun getThreadById(threadId: String): ThreadWithUser {
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

        return response.decodeAs<ThreadResponse>().toThreadWithUser()
    }

    /**
     * Ambil semua comment untuk 1 thread
     */
    suspend fun getCommentsByThreadId(threadId: String): List<Comment> {
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

        return response.decodeList<CommentResponse>().map { it.toComment() }
    }

    /**
     * Get comment count untuk 1 thread
     */
    suspend fun getCommentCount(threadId: String): Int {
        val response = supabase
            .from("comments")
            .select()
            {
                filter {
                    eq("thread_id", threadId)
                }
            }

        return response.decodeList<CommentResponse>().size
    }

    /**
     * Get comment counts untuk multiple threads (batch)
     */
    suspend fun getCommentCounts(threadIds: List<String>): Map<String, Int> {
        if (threadIds.isEmpty()) return emptyMap()

        val counts = mutableMapOf<String, Int>()

        // Untuk setiap thread, hitung comments
        // Note: Ini bisa di-optimize dengan SQL aggregate, tapi untuk sekarang cukup
        threadIds.forEach { threadId ->
            val count = getCommentCount(threadId)
            counts[threadId] = count
        }

        return counts
    }

    /**
     * Posting comment baru
     */
    suspend fun postComment(threadId: String, content: String) {
        val userId = supabase.auth.currentSessionOrNull()?.user?.id
            ?: throw Exception("User belum login")

        supabase.from("comments").insert(
            mapOf(
                "thread_id" to threadId,
                "user_id" to userId,
                "content" to content
            )
        )
    }

    /**
     * Delete comment
     * Rules (enforced by RLS):
     * - User bisa hapus own comment
     * - Thread owner bisa hapus comment di thread nya (moderasi)
     * - Admin bisa hapus any comment
     */
    suspend fun deleteComment(commentId: String) {
        supabase.from("comments").delete {
            filter {
                eq("id", commentId)
            }
        }
    }

    /**
     * Delete thread
     * Rules (enforced by RLS):
     * - User bisa hapus own thread
     * - Admin bisa hapus any thread
     */
    suspend fun deleteThread(threadId: String) {
        supabase.from("threads").delete {
            filter {
                eq("id", threadId)
            }
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
    suspend fun createThread(title: String, caption: String?, imageData: ByteArray) {
        val userId = supabase.auth.currentSessionOrNull()?.user?.id
            ?: throw Exception("User belum login")

        // 1. Upload image ke Storage
        val fileName = "img_${userId}_${System.currentTimeMillis()}.jpg"
        val imageUrl = uploadImageBytes(fileName, imageData)

        // 2. Insert thread ke Database dengan image URL
        supabase.from("threads").insert(
            mapOf(
                "title" to title,
                "caption" to caption,
                "image_url" to imageUrl,
                "user_id" to userId
            )
        )
    }

    /**
     * Buat thread baru (legacy method dengan imageUrl langsung)
     * imageUrl WAJIB karena ini image board
     */
    @Deprecated("Use createThread(title, caption, imageData) instead")
    suspend fun createThreadWithUrl(title: String, caption: String?, imageUrl: String) {
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
    ): String {
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
        return bucket.publicUrl(actualFileName)
    }

    /**
     * Upload gambar dari ByteArray langsung (legacy method)
     * Untuk backward compatibility
     */
    suspend fun uploadImageBytes(fileName: String, byteArray: ByteArray): String {
        val bucket = supabase.storage.from("images")
        bucket.upload(fileName, byteArray, upsert = false)
        return bucket.publicUrl(fileName)
    }
}

