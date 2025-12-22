package com.sample.image_board.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sample.image_board.data.model.Comment
import com.sample.image_board.data.model.CommentWithPermissions
import com.sample.image_board.data.model.ThreadWithUser
import com.sample.image_board.data.model.toCommentWithPermissions
import com.sample.image_board.data.repository.AuthRepository
import com.sample.image_board.data.repository.ThreadRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface DetailState {
    data object Loading : DetailState
    data class Success(
        val thread: ThreadWithUser,
        val comments: List<CommentWithPermissions>, // Changed to include permissions
        val canModerate: Boolean // Thread owner atau admin bisa moderate
    ) : DetailState
    data class Error(val message: String) : DetailState
}

sealed interface CommentPostState {
    data object Idle : CommentPostState
    data object Loading : CommentPostState
    data object Success : CommentPostState
    data class Error(val message: String) : CommentPostState
}

sealed interface CommentDeleteState {
    data object Idle : CommentDeleteState
    data object Loading : CommentDeleteState
    data object Success : CommentDeleteState
    data class Error(val message: String) : CommentDeleteState
}

class DetailViewModel : ViewModel() {

    private val repository = ThreadRepository()
    private val authRepository = AuthRepository()

    private val _detailState = MutableStateFlow<DetailState>(DetailState.Loading)
    val detailState = _detailState.asStateFlow()

    private val _commentPostState = MutableStateFlow<CommentPostState>(CommentPostState.Idle)
    val commentPostState = _commentPostState.asStateFlow()

    private val _commentDeleteState = MutableStateFlow<CommentDeleteState>(CommentDeleteState.Idle)
    val commentDeleteState = _commentDeleteState.asStateFlow()

    companion object {
        const val MAX_COMMENT_LENGTH = 500
    }

    /**
     * Load thread detail dan comments
     */
    fun loadThreadDetail(threadId: String) {
        viewModelScope.launch {
            _detailState.value = DetailState.Loading
            try {
                // Load thread detail
                val thread = repository.getThreadById(threadId)

                // Load comments (urutan terlama di atas - ASCENDING)
                val comments = repository.getCommentsByThreadId(threadId)

                // Get current user info
                val currentUserId = authRepository.getCurrentUserId() ?: ""
                val isAdmin = authRepository.isAdmin()

                // Check if current user can moderate (thread owner or admin)
                val canModerate = thread.userId == currentUserId || isAdmin

                // Convert comments to include permissions
                val commentsWithPermissions = comments.map { comment ->
                    comment.toCommentWithPermissions(
                        currentUserId = currentUserId,
                        threadOwnerId = thread.userId,
                        isAdmin = isAdmin
                    )
                }

                _detailState.value = DetailState.Success(
                    thread = thread,
                    comments = commentsWithPermissions,
                    canModerate = canModerate
                )
            } catch (e: Exception) {
                _detailState.value = DetailState.Error(
                    e.message ?: "Gagal memuat detail thread"
                )
            }
        }
    }

    /**
     * Post comment baru
     */
    fun postComment(threadId: String, content: String) {
        viewModelScope.launch {
            _commentPostState.value = CommentPostState.Loading
            try {
                // Validasi content
                if (content.isBlank()) {
                    _commentPostState.value = CommentPostState.Error("Komentar tidak boleh kosong")
                    return@launch
                }

                if (content.length > MAX_COMMENT_LENGTH) {
                    _commentPostState.value = CommentPostState.Error(
                        "Komentar maksimal $MAX_COMMENT_LENGTH karakter"
                    )
                    return@launch
                }

                // Post comment
                repository.postComment(threadId, content.trim())

                _commentPostState.value = CommentPostState.Success

                // Reload thread detail untuk update comments
                loadThreadDetail(threadId)

            } catch (e: Exception) {
                _commentPostState.value = CommentPostState.Error(
                    e.message ?: "Gagal mengirim komentar"
                )
            }
        }
    }

    /**
     * Delete comment
     * Rules (enforced by RLS):
     * - User bisa hapus own comment
     * - Thread owner bisa hapus any comment di thread nya
     * - Admin bisa hapus any comment
     */
    fun deleteComment(threadId: String, commentId: String) {
        viewModelScope.launch {
            _commentDeleteState.value = CommentDeleteState.Loading
            try {
                repository.deleteComment(commentId)
                _commentDeleteState.value = CommentDeleteState.Success

                // Reload thread detail untuk update list
                loadThreadDetail(threadId)

            } catch (e: Exception) {
                _commentDeleteState.value = CommentDeleteState.Error(
                    e.message ?: "Gagal menghapus komentar"
                )
            }
        }
    }

    /**
     * Reset comment post state
     */
    fun resetCommentPostState() {
        _commentPostState.value = CommentPostState.Idle
    }

    /**
     * Reset comment delete state
     */
    fun resetCommentDeleteState() {
        _commentDeleteState.value = CommentDeleteState.Idle
    }
}

