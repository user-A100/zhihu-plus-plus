/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation (version 3 only).
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.github.zly2006.zhihu.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.zly2006.zhihu.navigation.Collections
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.shared.data.navDestination
import com.github.zly2006.zhihu.shared.platform.rememberUserMessageSink
import com.github.zly2006.zhihu.shared.ui.TopLevelReselectAction
import com.github.zly2006.zhihu.shared.ui.topLevelReselectAction
import com.github.zly2006.zhihu.ui.components.FeedCard
import com.github.zly2006.zhihu.viewmodel.CollectionLibraryItem
import com.github.zly2006.zhihu.viewmodel.CollectionLibraryViewModel
import com.github.zly2006.zhihu.viewmodel.rememberPaginationEnvironment

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionLibraryScreen(
    urlToken: String?,
    showBackButton: Boolean = true,
    scrollToTopTrigger: Int = 0,
    innerPadding: PaddingValues = PaddingValues(0.dp),
) {
    val navigator = LocalNavigator.current
    val environment = rememberPaginationEnvironment(allowGuestAccess = false)
    val userMessages = rememberUserMessageSink()
    val screenViewModel: CollectionLibraryViewModel = viewModel()
    val listState = rememberLazyListState()

    var searchVisible by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var cachedScrollToTopTrigger by remember { mutableStateOf(scrollToTopTrigger) }

    val filteredItems by remember {
        derivedStateOf {
            screenViewModel.items
                .filter { query.isBlank() || query.lowercase() in it.searchableText }
        }
    }

    LaunchedEffect(screenViewModel) {
        screenViewModel.load()
    }
    LaunchedEffect(screenViewModel.hasLoaded, urlToken) {
        if (screenViewModel.hasLoaded && screenViewModel.items.isNotEmpty()) {
            screenViewModel.synchronize(environment, urlToken.orEmpty(), force = false)
        }
    }
    LaunchedEffect(scrollToTopTrigger) {
        when (
            topLevelReselectAction(
                triggerDelta = scrollToTopTrigger - cachedScrollToTopTrigger,
                isAtTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0,
            )
        ) {
            TopLevelReselectAction.Refresh -> {
                screenViewModel.synchronize(environment, urlToken.orEmpty(), force = false)
            }
            TopLevelReselectAction.ScrollToTop -> listState.animateScrollToItem(0)
            null -> Unit
        }
        cachedScrollToTopTrigger = scrollToTopTrigger
    }
    LaunchedEffect(screenViewModel.errorMessage) {
        screenViewModel.errorMessage?.let { userMessages.showShortMessage(it) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("收藏")
                        if (screenViewModel.items.isNotEmpty()) {
                            Text(
                                "${screenViewModel.items.size} 条内容",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = navigator.onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { searchVisible = !searchVisible },
                        modifier = Modifier.testTag(COLLECTION_LIBRARY_SEARCH_ACTION_TAG),
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = "搜索收藏")
                    }
                    IconButton(
                        onClick = { navigator.onNavigate(Collections(urlToken.orEmpty())) },
                        modifier = Modifier.testTag(COLLECTION_LIBRARY_FOLDERS_ACTION_TAG),
                    ) {
                        Icon(Icons.Filled.Folder, contentDescription = "收藏夹目录")
                    }
                    IconButton(
                        onClick = {
                            screenViewModel.synchronize(environment, urlToken.orEmpty(), force = false)
                        },
                        enabled = !screenViewModel.isSyncing,
                        modifier = Modifier.testTag(COLLECTION_LIBRARY_SYNC_ACTION_TAG),
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "同步收藏")
                    }
                },
            )
        },
    ) { scaffoldPadding ->
        PullToRefreshBox(
            isRefreshing = screenViewModel.isSyncing,
            onRefresh = {
                screenViewModel.synchronize(environment, urlToken.orEmpty(), force = false)
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(top = scaffoldPadding.calculateTopPadding()),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (searchVisible) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        label = { Text("搜索标题、作者、摘要或收藏夹") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag(COLLECTION_LIBRARY_SEARCH_FIELD_TAG),
                    )
                }

                if (screenViewModel.isSyncing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        screenViewModel.syncProgress,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                when {
                    !screenViewModel.hasLoaded -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    screenViewModel.items.isEmpty() && !screenViewModel.isSyncing -> {
                        CollectionLibraryEmptyState(
                            onStartSync = {
                                screenViewModel.synchronize(environment, urlToken.orEmpty(), force = true)
                            },
                        )
                    }
                    filteredItems.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("没有找到匹配的收藏")
                        }
                    }
                    else -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag(COLLECTION_LIBRARY_LIST_TAG),
                            contentPadding = PaddingValues(
                                bottom = innerPadding.calculateBottomPadding() + 16.dp,
                            ),
                        ) {
                            items(
                                items = filteredItems,
                                key = CollectionLibraryItem::contentKey,
                            ) { item ->
                                FeedCard(
                                    item = item.displayItem,
                                    modifier = Modifier.testTag("collection_library_item_${item.contentKey}"),
                                ) {
                                    navDestination?.let { navigator.onNavigate(it) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionLibraryEmptyState(
    onStartSync: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("建立收藏索引", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "使用知乎加加当前登录账号读取收藏夹。只保存检索和排序需要的轻量信息，可中断后继续。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onStartSync,
                modifier = Modifier.testTag(COLLECTION_LIBRARY_START_SYNC_TAG),
            ) {
                Text("开始同步")
            }
        }
    }
}

private const val COLLECTION_LIBRARY_SEARCH_ACTION_TAG = "collection_library_search_action"
private const val COLLECTION_LIBRARY_FOLDERS_ACTION_TAG = "collection_library_folders_action"
private const val COLLECTION_LIBRARY_SYNC_ACTION_TAG = "collection_library_sync_action"
private const val COLLECTION_LIBRARY_SEARCH_FIELD_TAG = "collection_library_search_field"
private const val COLLECTION_LIBRARY_LIST_TAG = "collection_library_list"
private const val COLLECTION_LIBRARY_START_SYNC_TAG = "collection_library_start_sync"
