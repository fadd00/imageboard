package com.sample.image_board.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sample.image_board.data.repository.AuthRepository
import com.sample.image_board.data.repository.ThreadRepository
import com.sample.image_board.data.model.ThreadWithUser
import com.sample.image_board.data.model.ThreadWithPermissions
import com.sample.image_board.data.model.toThreadWithPermissions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface HomeState {
    data object Loading : HomeState
    data object LoadingMore : HomeState // Loading untuk pagination
    data object Refreshing : HomeState // Loading untuk pull-to-refresh
    data class Success(
        val threads: List<ThreadWithPermissions>, // Changed to include permissions
        val hasMore: Boolean = true // Ada data berikutnya?
    ) : HomeState
    data class Error(val message: String) : HomeState
}

sealed interface ThreadDeleteState {
    data object Idle : ThreadDeleteState
    data object Loading : ThreadDeleteState
    data object Success : ThreadDeleteState
    data class Error(val message: String) : ThreadDeleteState
}

class HomeViewModel : ViewModel() {

    private val repository = ThreadRepository()
    private val authRepository = AuthRepository()

    private val _homeState = MutableStateFlow<HomeState>(HomeState.Loading)
    val homeState = _homeState.asStateFlow()

    private val _threadDeleteState = MutableStateFlow<ThreadDeleteState>(ThreadDeleteState.Idle)
    val threadDeleteState = _threadDeleteState.asStateFlow()

    // Search state
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    // Pagination state
    private var currentOffset = 0
    private val pageSize = 20 // Muat 20 items per page
    private var isLoadingMore = false
    private var hasMoreData = true

    // Track apakah ini first load
    private var isFirstLoad = true

    // All threads (for search filtering)
    private var allThreads: List<ThreadWithPermissions> = emptyList()

    init {
        // Auto load saat ViewModel dibuat
        loadThreads()
    }

    /**
     * Load threads pertama kali atau refresh
     */
    fun loadThreads() {
        if (isFirstLoad) {
            // First load: tampilkan loading penuh
            _homeState.value = HomeState.Loading
            isFirstLoad = false
        } else {
            // Pull-to-refresh: tampilkan refreshing state
            _homeState.value = HomeState.Refreshing
        }

        viewModelScope.launch {
            try {
                // Reset pagination
                currentOffset = 0
                hasMoreData = true

                val threads = repository.getThreadsPaginated(
                    offset = currentOffset,
                    limit = pageSize
                )

                // Load comment counts untuk threads
                val threadIds = threads.map { it.id }
                val commentCounts = repository.getCommentCounts(threadIds)

                // Get current user info
                val currentUserId = authRepository.getCurrentUserId() ?: ""
                val isAdmin = authRepository.isAdmin()

                // Update threads dengan comment count dan permissions
                val threadsWithPermissions = threads.map { thread ->
                    thread.copy(commentCount = commentCounts[thread.id] ?: 0)
                        .toThreadWithPermissions(currentUserId, isAdmin)
                }

                // Store all threads for search
                allThreads = threadsWithPermissions

                // Apply search filter if active
                val filteredThreads = if (_searchQuery.value.isNotEmpty()) {
                    filterThreads(threadsWithPermissions, _searchQuery.value)
                } else {
                    threadsWithPermissions
                }

                // Cek apakah masih ada data berikutnya
                hasMoreData = threads.size >= pageSize

                _homeState.value = HomeState.Success(
                    threads = filteredThreads,
                    hasMore = hasMoreData
                )

                // Update offset untuk pagination berikutnya
                currentOffset = threads.size

            } catch (e: Exception) {
                _homeState.value = HomeState.Error(e.message ?: "Gagal memuat feed")
            }
        }
    }

    /**
     * Load more threads untuk infinite scroll
     */
    fun loadMoreThreads() {
        // Prevent multiple simultaneous loads
        if (isLoadingMore || !hasMoreData) return

        // Cek apakah state saat ini Success
        val currentState = _homeState.value
        if (currentState !is HomeState.Success) return

        isLoadingMore = true
        _homeState.value = HomeState.LoadingMore

        viewModelScope.launch {
            try {
                val newThreads = repository.getThreadsPaginated(
                    offset = currentOffset,
                    limit = pageSize
                )

                // Load comment counts untuk new threads
                val threadIds = newThreads.map { it.id }
                val commentCounts = repository.getCommentCounts(threadIds)

                // Get current user info
                val currentUserId = authRepository.getCurrentUserId() ?: ""
                val isAdmin = authRepository.isAdmin()

                // Update new threads dengan comment count dan permissions
                val threadsWithPermissions = newThreads.map { thread ->
                    thread.copy(commentCount = commentCounts[thread.id] ?: 0)
                        .toThreadWithPermissions(currentUserId, isAdmin)
                }

                // Update all threads list
                allThreads = allThreads + threadsWithPermissions

                // Apply search filter if active
                val filteredNewThreads = if (_searchQuery.value.isNotEmpty()) {
                    filterThreads(threadsWithPermissions, _searchQuery.value)
                } else {
                    threadsWithPermissions
                }

                // Cek apakah masih ada data berikutnya
                hasMoreData = newThreads.size >= pageSize

                // Gabungkan dengan threads yang sudah ada
                val allThreads = currentState.threads + filteredNewThreads

                _homeState.value = HomeState.Success(
                    threads = allThreads,
                    hasMore = hasMoreData
                )

                // Update offset
                currentOffset = allThreads.size

            } catch (e: Exception) {
                // Jika error saat load more, kembali ke state Success dengan data lama
                _homeState.value = currentState.copy(hasMore = false)
            } finally {
                isLoadingMore = false
            }
        }
    }

    /**
     * Refresh data (pull-to-refresh)
     */
    fun refresh() {
        loadThreads()
    }

    /**
     * Delete thread
     * Rules (enforced by RLS):
     * - User bisa hapus own thread
     * - Admin bisa hapus any thread
     */
    fun deleteThread(threadId: String) {
        viewModelScope.launch {
            _threadDeleteState.value = ThreadDeleteState.Loading
            try {
                repository.deleteThread(threadId)
                _threadDeleteState.value = ThreadDeleteState.Success

                // Reload threads untuk update list
                loadThreads()

            } catch (e: Exception) {
                _threadDeleteState.value = ThreadDeleteState.Error(
                    e.message ?: "Gagal menghapus thread"
                )
            }
        }
    }

    /**
     * Reset thread delete state
     */
    fun resetThreadDeleteState() {
        _threadDeleteState.value = ThreadDeleteState.Idle
    }

    /**
     * Update search query and filter threads
     */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query

        // Filter current threads
        val currentState = _homeState.value
        if (currentState is HomeState.Success) {
            val filteredThreads = if (query.isEmpty()) {
                allThreads // Show all threads if search is cleared
            } else {
                filterThreads(allThreads, query)
            }

            _homeState.value = HomeState.Success(
                threads = filteredThreads,
                hasMore = currentState.hasMore
            )
        }
    }

    /**
     * Clear search query
     */
    fun clearSearch() {
        updateSearchQuery("")
    }

    /**
     * Filter threads based on search query
     * Search in: title and caption (content)
     */
    private fun filterThreads(
        threads: List<ThreadWithPermissions>,
        query: String
    ): List<ThreadWithPermissions> {
        if (query.isEmpty()) return threads

        val lowerQuery = query.lowercase()
        return threads.filter { thread ->
            thread.title.lowercase().contains(lowerQuery) ||
            thread.content.lowercase().contains(lowerQuery) ||
            thread.userName.lowercase().contains(lowerQuery)
        }
    }

    /**
     * Reset state (untuk cleanup)
     */
    fun resetState() {
        currentOffset = 0
        hasMoreData = true
        isLoadingMore = false
        isFirstLoad = true
        allThreads = emptyList()
        _searchQuery.value = ""
    }
}

