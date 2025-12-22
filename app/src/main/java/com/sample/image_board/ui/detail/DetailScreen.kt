package com.sample.image_board.ui.detail

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
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
import com.sample.image_board.data.model.CommentWithPermissions
import com.sample.image_board.data.model.ThreadWithUser
import com.sample.image_board.viewmodel.CommentDeleteState
import com.sample.image_board.viewmodel.CommentPostState
import com.sample.image_board.viewmodel.DetailState
import com.sample.image_board.viewmodel.DetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    threadId: String,
    onBack: () -> Unit,
    viewModel: DetailViewModel = viewModel()
) {
    val context = LocalContext.current
    val detailState by viewModel.detailState.collectAsState()
    val commentPostState by viewModel.commentPostState.collectAsState()
    val commentDeleteState by viewModel.commentDeleteState.collectAsState()

    var commentText by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var commentToDelete by remember { mutableStateOf<String?>(null) }

    // Load thread detail saat pertama kali dibuka
    LaunchedEffect(threadId) {
        viewModel.loadThreadDetail(threadId)
    }

    // Handle comment post state
    LaunchedEffect(commentPostState) {
        when (val state = commentPostState) {
            is CommentPostState.Success -> {
                Toast.makeText(context, "Komentar berhasil dikirim!", Toast.LENGTH_SHORT).show()
                commentText = "" // Clear input
                viewModel.resetCommentPostState()
            }
            is CommentPostState.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                viewModel.resetCommentPostState()
            }
            else -> {}
        }
    }

    // Handle comment delete state
    LaunchedEffect(commentDeleteState) {
        when (val state = commentDeleteState) {
            is CommentDeleteState.Success -> {
                Toast.makeText(context, "Komentar berhasil dihapus!", Toast.LENGTH_SHORT).show()
                viewModel.resetCommentDeleteState()
            }
            is CommentDeleteState.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                viewModel.resetCommentDeleteState()
            }
            else -> {}
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog && commentToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Hapus Komentar") },
            text = { Text("Apakah Anda yakin ingin menghapus komentar ini?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteComment(threadId, commentToDelete!!)
                        showDeleteDialog = false
                        commentToDelete = null
                    }
                ) {
                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Thread Detail") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        when (val state = detailState) {
            is DetailState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is DetailState.Success -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    // Content (Thread + Comments) - Scrollable
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Thread Detail
                        item {
                            ThreadDetailCard(thread = state.thread)
                        }

                        // Comments Header
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Komentar",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "${state.comments.size} komentar",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Comments List (Kronologis - Terlama di atas)
                        if (state.comments.isEmpty()) {
                            item {
                                Text(
                                    "Belum ada komentar. Jadilah yang pertama berkomentar!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 16.dp)
                                )
                            }
                        } else {
                            items(state.comments, key = { it.id }) { comment ->
                                CommentItem(
                                    comment = comment,
                                    onDeleteClick = if (comment.canDelete) {
                                        {
                                            commentToDelete = comment.id
                                            showDeleteDialog = true
                                        }
                                    } else null
                                )
                            }
                        }
                    }

                    // Comment Input - Fixed di bawah
                    CommentInputBar(
                        commentText = commentText,
                        onCommentTextChange = {
                            if (it.length <= DetailViewModel.MAX_COMMENT_LENGTH) {
                                commentText = it
                            }
                        },
                        onSendClick = {
                            viewModel.postComment(threadId, commentText)
                        },
                        isLoading = commentPostState is CommentPostState.Loading,
                        enabled = commentText.isNotBlank()
                    )
                }
            }

            is DetailState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(state.message)
                        Button(onClick = { viewModel.loadThreadDetail(threadId) }) {
                            Text("Coba Lagi")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ThreadDetailCard(thread: ThreadWithUser) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Title
            Text(
                text = thread.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Author & Date
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar placeholder
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = thread.userName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Image
            AsyncImage(
                model = thread.imageUrl,
                contentDescription = "Thread Image",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 400.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )

            // Caption
            if (thread.content.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = thread.content,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
fun CommentItem(
    comment: CommentWithPermissions,
    onDeleteClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer)
        )

        // Comment Content
        Column(
            modifier = Modifier.weight(1f)
        ) {
            // Username
            Text(
                text = comment.userName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Comment text
            Text(
                text = comment.content,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        // Delete button (only if user can delete)
        if (onDeleteClick != null) {
            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Hapus komentar",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun CommentInputBar(
    commentText: String,
    onCommentTextChange: (String) -> Unit,
    onSendClick: () -> Unit,
    isLoading: Boolean,
    enabled: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 8.dp,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // Text Field
            OutlinedTextField(
                value = commentText,
                onValueChange = onCommentTextChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Tulis komentar...") },
                maxLines = 4,
                enabled = !isLoading,
                supportingText = {
                    Text(
                        "${commentText.length}/${DetailViewModel.MAX_COMMENT_LENGTH}",
                        color = if (commentText.length >= DetailViewModel.MAX_COMMENT_LENGTH)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )

            // Send Button
            IconButton(
                onClick = onSendClick,
                enabled = enabled && !isLoading,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (enabled && !isLoading)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (enabled)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

