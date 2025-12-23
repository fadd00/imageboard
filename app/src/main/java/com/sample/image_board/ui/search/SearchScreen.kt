package com.sample.image_board.ui.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sample.image_board.ui.home.ThreadCard
import com.sample.image_board.viewmodel.HomeState
import com.sample.image_board.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(onThreadClick: (String) -> Unit, homeViewModel: HomeViewModel) {
    var searchQuery by remember { mutableStateOf("") }
    val homeState by homeViewModel.homeState.collectAsState()

    // Get all threads for filtering
    val allThreads =
            remember(homeState) {
                when (homeState) {
                    is HomeState.Success -> (homeState as HomeState.Success).threads
                    else -> emptyList()
                }
            }

    // Filter threads based on search query
    val filteredThreads =
            remember(searchQuery, allThreads) {
                if (searchQuery.isBlank()) {
                    allThreads
                } else {
                    allThreads.filter { thread ->
                        thread.title.contains(searchQuery, ignoreCase = true) ||
                                thread.content.contains(searchQuery, ignoreCase = true) ||
                                thread.userName.contains(searchQuery, ignoreCase = true)
                    }
                }
            }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(16.dp))

        // Search Bar
        OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search posts...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Results
        when {
            homeState is HomeState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            filteredThreads.isEmpty() && searchQuery.isNotBlank() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                                "No results for \"$searchQuery\"",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                                "Try different keywords",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            filteredThreads.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                            "Start typing to search posts",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    item {
                        Text(
                                "${filteredThreads.size} results",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    items(filteredThreads, key = { it.id }) { thread ->
                        ThreadCard(thread = thread, onClick = { onThreadClick(thread.id) })
                    }
                }
            }
        }
    }
}
