package com.sample.image_board.ui.home

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sample.image_board.data.model.ThreadWithPermissions
import com.sample.image_board.viewmodel.HomeViewModel
import com.sample.image_board.viewmodel.AuthViewModel
import com.sample.image_board.viewmodel.ThreadDeleteState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sample.image_board.viewmodel.HomeState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun HomeScreen(
    onFabClick: () -> Unit,
    onThreadClick: (String) -> Unit,
    onLogout: () -> Unit,
    viewModel: HomeViewModel = viewModel(),
    authViewModel: AuthViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.homeState.collectAsState()
    val showLogoutDialog by authViewModel.showLogoutDialog.collectAsState()
    val threadDeleteState by viewModel.threadDeleteState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    // State untuk delete dialog
    var showDeleteDialog by remember { mutableStateOf(false) }
    var threadToDelete by remember { mutableStateOf<String?>(null) }

    // State untuk search bar visibility
    var showSearchBar by remember { mutableStateOf(false) }

    // State untuk LazyColumn (deteksi scroll untuk infinite scroll)
    val listState = rememberLazyListState()

    // State untuk pull-to-refresh
    val isRefreshing = state is HomeState.Refreshing
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = { viewModel.refresh() }
    )

    // Handle thread delete state
    LaunchedEffect(threadDeleteState) {
        when (val deleteState = threadDeleteState) {
            is ThreadDeleteState.Success -> {
                Toast.makeText(context, "Thread berhasil dihapus!", Toast.LENGTH_SHORT).show()
                viewModel.resetThreadDeleteState()
            }
            is ThreadDeleteState.Error -> {
                Toast.makeText(context, deleteState.message, Toast.LENGTH_LONG).show()
                viewModel.resetThreadDeleteState()
            }
            else -> {}
        }
    }

    // Deteksi scroll ke bawah untuk infinite scroll
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisibleIndex ->
                if (state is HomeState.Success) {
                    val currentState = state as HomeState.Success
                    val totalItems = currentState.threads.size

                    // Trigger load more saat user scroll 5 item sebelum akhir
                    if (lastVisibleIndex != null &&
                        lastVisibleIndex >= totalItems - 5 &&
                        currentState.hasMore) {
                        viewModel.loadMoreThreads()
                    }
                }
            }
    }

    // Dialog Konfirmasi Logout
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { authViewModel.cancelLogout() },
            title = { Text("Konfirmasi Logout") },
            text = { Text("Apakah Anda yakin ingin keluar dari akun ini?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        authViewModel.confirmLogout()
                        onLogout()
                    }
                ) {
                    Text("Keluar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { authViewModel.cancelLogout() }) {
                    Text("Batal")
                }
            }
        )
    }

    // Dialog Konfirmasi Delete Thread
    if (showDeleteDialog && threadToDelete != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                threadToDelete = null
            },
            title = { Text("Hapus Thread") },
            text = { Text("Apakah Anda yakin ingin menghapus thread ini? Semua komentar juga akan terhapus.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteThread(threadToDelete!!)
                        showDeleteDialog = false
                        threadToDelete = null
                    }
                ) {
                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    threadToDelete = null
                }) {
                    Text("Batal")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            if (showSearchBar) {
                // Search Bar
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.updateSearchQuery(it) },
                            placeholder = { Text("Cari thread...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.clearSearch() }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                disabledContainerColor = MaterialTheme.colorScheme.surface,
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            showSearchBar = false
                            viewModel.clearSearch()
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "Close search")
                        }
                    }
                )
            } else {
                // Normal Top Bar
                TopAppBar(
                    title = { Text("ImageBoard Feed") },
                    actions = {
                        IconButton(onClick = { showSearchBar = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                        IconButton(onClick = { authViewModel.requestLogout() }) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout")
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onFabClick) {
                Icon(Icons.Default.Add, contentDescription = "Create Thread")
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .pullRefresh(pullRefreshState)
        ) {
            when (val currentState = state) {
                is HomeState.Loading -> {
                    // Skeleton Loading untuk first load
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(5) { // Tampilkan 5 skeleton
                            ThreadCardSkeleton()
                        }
                    }
                }

                is HomeState.Success, is HomeState.LoadingMore -> {
                    val threads = when (currentState) {
                        is HomeState.Success -> currentState.threads
                        is HomeState.LoadingMore -> {
                            // Ambil data dari state sebelumnya
                            (viewModel.homeState.replayCache.lastOrNull() as? HomeState.Success)?.threads
                                ?: emptyList()
                        }
                        else -> emptyList()
                    }

                    if (threads.isEmpty() && currentState !is HomeState.LoadingMore) {
                        // Empty state
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Belum ada thread. Bikin yang pertama!",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(threads, key = { it.id }) { thread ->
                                ThreadCard(
                                    thread = thread,
                                    onClick = { onThreadClick(thread.id) },
                                    onDeleteClick = if (thread.canDelete) {
                                        {
                                            threadToDelete = thread.id
                                            showDeleteDialog = true
                                        }
                                    } else null
                                )
                            }

                            // Loading indicator di bawah untuk infinite scroll
                            if (currentState is HomeState.LoadingMore ||
                                (currentState is HomeState.Success && currentState.hasMore)) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }
                            }

                            // End of list indicator
                            if (currentState is HomeState.Success && !currentState.hasMore && threads.isNotEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "Sudah semua! 🎉",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                is HomeState.Refreshing -> {
                    // Tampilkan data lama sambil refresh
                    // (akan di-handle oleh PullRefreshIndicator)
                }

                is HomeState.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(currentState.message)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { viewModel.loadThreads() }) {
                            Text("Coba Lagi")
                        }
                    }
                }
            }

            // Pull-to-Refresh Indicator (muncul saat tarik ke bawah)
            PullRefreshIndicator(
                refreshing = isRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

@Composable
fun ThreadCard(
    thread: ThreadWithPermissions,
    onClick: () -> Unit,
    onDeleteClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header with delete button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = thread.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Posted by ${thread.userName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Delete button (only if user can delete)
                if (onDeleteClick != null) {
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Hapus thread",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Content
            Text(
                text = thread.content,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3
            )

            // Image (if exists)
            if (thread.imageUrl.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                AsyncImage(
                    model = thread.imageUrl,
                    contentDescription = "Thread Image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Footer
            Text(
                text = "${thread.commentCount} comments",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Skeleton Loading untuk ThreadCard
 * Ditampilkan saat first load atau refresh
 */
@Composable
fun ThreadCardSkeleton() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Title skeleton
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmerEffect()
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Username skeleton
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmerEffect()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Content skeleton (3 lines)
            repeat(3) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (it == 2) 0.6f else 1f)
                        .height(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .shimmerEffect()
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Image skeleton
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .shimmerEffect()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Comment count skeleton
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmerEffect()
            )
        }
    }
}

@Composable
fun Modifier.shimmerEffect(): Modifier {
    val shimmerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    return this.then(Modifier.background(shimmerColor))
}
