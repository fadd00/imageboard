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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.sample.image_board.data.model.ThreadWithPermissions
import com.sample.image_board.viewmodel.AuthViewModel
import com.sample.image_board.viewmodel.HomeState
import com.sample.image_board.viewmodel.HomeViewModel
import com.sample.image_board.viewmodel.ThreadDeleteState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun HomeScreen(
        onFabClick: () -> Unit,
        onThreadClick: (String) -> Unit,
        onLogout: () -> Unit,
        viewModel: HomeViewModel = viewModel(),
        authViewModel: AuthViewModel = viewModel(),
        showFab: Boolean = true
) {
    val context = LocalContext.current
    val state by viewModel.homeState.collectAsState()
    val showLogoutDialog by authViewModel.showLogoutDialog.collectAsState()
    val showSettingsDialog by authViewModel.showSettingsDialog.collectAsState()
    val stayLoggedIn by authViewModel.stayLoggedIn.collectAsState()
    val threadDeleteState by viewModel.threadDeleteState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val currentUsername by authViewModel.currentUsername.collectAsState()

    // State untuk delete dialog
    var showDeleteDialog by remember { mutableStateOf(false) }
    var threadToDelete by remember { mutableStateOf<String?>(null) }

    // State untuk search bar visibility
    var showSearchBar by remember { mutableStateOf(false) }

    // State untuk LazyColumn (deteksi scroll untuk infinite scroll)
    val listState = rememberLazyListState()

    // State untuk pull-to-refresh
    val isRefreshing = state is HomeState.Refreshing
    val pullRefreshState =
            rememberPullRefreshState(refreshing = isRefreshing, onRefresh = { viewModel.refresh() })

    // Load username saat HomeScreen muncul
    LaunchedEffect(Unit) { authViewModel.loadUsername() }

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
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }.collect {
                lastVisibleIndex ->
            if (state is HomeState.Success) {
                val currentState = state as HomeState.Success
                val totalItems = currentState.threads.size

                // Trigger load more saat user scroll 5 item sebelum akhir
                if (lastVisibleIndex != null &&
                                lastVisibleIndex >= totalItems - 5 &&
                                currentState.hasMore
                ) {
                    viewModel.loadMoreThreads()
                }
            }
        }
    }

    // Dialog Settings
    if (showSettingsDialog) {
        AlertDialog(
                onDismissRequest = { authViewModel.closeSettings() },
                title = { Text("Pengaturan") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        // Stay Logged In Toggle
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Tetap Masuk", style = MaterialTheme.typography.bodyLarge)
                            Switch(
                                    checked = stayLoggedIn,
                                    onCheckedChange = {
                                        authViewModel.toggleStayLoggedIn()
                                        authViewModel.saveStayLoggedInPreference()
                                    }
                            )
                        }

                        HorizontalDivider()

                        // Logout Button
                        Button(
                                onClick = { authViewModel.requestLogout() },
                                modifier = Modifier.fillMaxWidth(),
                                colors =
                                        ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.error
                                        )
                        ) { Text("Logout", color = MaterialTheme.colorScheme.onError) }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { authViewModel.closeSettings() }) { Text("Tutup") }
                }
        )
    }

    // Dialog Konfirmasi Logout
    if (showLogoutDialog) {
        AlertDialog(
                onDismissRequest = { authViewModel.cancelLogout() },
                title = { Text("Konfirmasi Logout") },
                text = { Text("Logout dari akun ${currentUsername ?: "user"}?") },
                confirmButton = {
                    TextButton(
                            onClick = {
                                authViewModel.confirmLogout()
                                onLogout()
                            }
                    ) { Text("Keluar", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { authViewModel.cancelLogout() }) { Text("Batal") }
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
                text = {
                    Text(
                            "Apakah Anda yakin ingin menghapus thread ini? Semua komentar juga akan terhapus."
                    )
                },
                confirmButton = {
                    TextButton(
                            onClick = {
                                viewModel.deleteThread(threadToDelete!!)
                                showDeleteDialog = false
                                threadToDelete = null
                            }
                    ) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(
                            onClick = {
                                showDeleteDialog = false
                                threadToDelete = null
                            }
                    ) { Text("Batal") }
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
                                                    Icon(
                                                            Icons.Filled.Clear,
                                                            contentDescription = "Clear search"
                                                    )
                                                }
                                            }
                                        },
                                        colors =
                                                OutlinedTextFieldDefaults.colors(
                                                        focusedContainerColor =
                                                                MaterialTheme.colorScheme.surface,
                                                        unfocusedContainerColor =
                                                                MaterialTheme.colorScheme.surface,
                                                        disabledContainerColor =
                                                                MaterialTheme.colorScheme.surface,
                                                )
                                )
                            },
                            navigationIcon = {
                                IconButton(
                                        onClick = {
                                            showSearchBar = false
                                            viewModel.clearSearch()
                                        }
                                ) { Icon(Icons.Default.Clear, contentDescription = "Close search") }
                            }
                    )
                } else {
                    // Custom Compact Header
                    Surface(
                            shadowElevation = 2.dp,
                            tonalElevation = 2.dp,
                            modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                        "Imgr",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                )
                                // Show username or 'Guest' if null to ensure layout consistency
                                Text(
                                        text = currentUsername ?: "Loading...",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            // Settings icon
                            IconButton(onClick = { authViewModel.requestSettings() }) {
                                Icon(Icons.Filled.Settings, contentDescription = "Settings")
                            }
                        }
                    }
                }
            },
            floatingActionButton = {
                if (showFab) {
                    FloatingActionButton(onClick = onFabClick) {
                        Icon(Icons.Default.Add, contentDescription = "Create Thread")
                    }
                }
            }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).pullRefresh(pullRefreshState)) {
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
                    val threads =
                            when (currentState) {
                                is HomeState.Success -> currentState.threads
                                is HomeState.LoadingMore -> {
                                    // Ambil data dari state sebelumnya
                                    (viewModel.homeState.replayCache.lastOrNull() as?
                                                    HomeState.Success)
                                            ?.threads
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
                                ThreadCard(thread = thread, onClick = { onThreadClick(thread.id) })
                            }

                            // Loading indicator di bawah untuk infinite scroll
                            if (currentState is HomeState.LoadingMore ||
                                            (currentState is HomeState.Success &&
                                                    currentState.hasMore)
                            ) {
                                item {
                                    Box(
                                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                                            contentAlignment = Alignment.Center
                                    ) { CircularProgressIndicator(modifier = Modifier.size(32.dp)) }
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
                        Button(onClick = { viewModel.loadThreads() }) { Text("Coba Lagi") }
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
fun ThreadCard(thread: ThreadWithPermissions, onClick: () -> Unit) {
    Card(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            shape = RoundedCornerShape(16.dp)
    ) {
        Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Thumbnail (100x100dp for better visibility)
            if (thread.imageUrl.isNotEmpty()) {
                AsyncImage(
                        model = thread.imageUrl,
                        contentDescription = "Thread Image",
                        modifier = Modifier.size(100.dp).clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                )
            }

            // Content on the right
            Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Username
                Text(
                        text = thread.userName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                )

                // Title
                if (thread.title.isNotEmpty()) {
                    Text(
                            text = thread.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                    )
                }

                // Caption (main content per SRS - no title)
                if (thread.content.isNotEmpty()) {
                    Text(
                            text = thread.content,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Comment count
                Text(
                        text = "${thread.commentCount} comments",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/** Skeleton Loading untuk ThreadCard Ditampilkan saat first load atau refresh */
@Composable
fun ThreadCardSkeleton() {
    Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Title skeleton
            Box(
                    modifier =
                            Modifier.fillMaxWidth(0.7f)
                                    .height(24.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .shimmerEffect()
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Username skeleton
            Box(
                    modifier =
                            Modifier.fillMaxWidth(0.4f)
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .shimmerEffect()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Content skeleton (3 lines)
            repeat(3) {
                Box(
                        modifier =
                                Modifier.fillMaxWidth(if (it == 2) 0.6f else 1f)
                                        .height(16.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .shimmerEffect()
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Image skeleton
            Box(
                    modifier =
                            Modifier.fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .shimmerEffect()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Comment count skeleton
            Box(
                    modifier =
                            Modifier.width(80.dp)
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
