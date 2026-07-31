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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.zly2006.zhihu.navigation.LocalNavigator
import com.github.zly2006.zhihu.shared.data.FeedDisplayItem
import com.github.zly2006.zhihu.shared.ui.TopLevelReselectAction
import com.github.zly2006.zhihu.shared.ui.topLevelReselectAction
import com.github.zly2006.zhihu.viewmodel.CollectionContentEnvironment
import com.github.zly2006.zhihu.viewmodel.CollectionContentViewModel
import com.github.zly2006.zhihu.viewmodel.CollectionsViewModel
import com.github.zly2006.zhihu.viewmodel.rememberPaginationEnvironment

/**
 * 收藏直达浏览页：进入后直接展示某个收藏夹的内容瀑布流，
 * 右上角文件夹图标在页内切换当前展示的收藏夹，不跳转到新页面。
 *
 * 默认选中策略见 [pickDefaultCollectionId]（优先默认收藏夹，否则第一个）。
 *
 * @param testCollections 测试注入的收藏夹列表；非空时跳过网络拉取，便于仪器测试。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionBrowseScreen(
    urlToken: String?,
    showBackButton: Boolean = true,
    scrollToTopTrigger: Int = 0,
    testCollections: List<Collection>? = null,
) {
    val navigator = LocalNavigator.current
    val environment = rememberPaginationEnvironment(allowGuestAccess = false)
    val contentEnv = environment as CollectionContentEnvironment
    val useTestCollections = testCollections != null
    val collectionsViewModel: CollectionsViewModel = viewModel(key = urlToken) {
        CollectionsViewModel(urlToken.orEmpty())
    }
    val collections = testCollections ?: collectionsViewModel.allData
    val listState = rememberLazyListState()
    var cachedScrollToTopTrigger by remember { mutableIntStateOf(scrollToTopTrigger) }

    var selectedCollectionId by remember { mutableStateOf<String?>(null) }
    var folderMenuExpanded by remember { mutableStateOf(false) }
    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(useTestCollections) {
        if (!useTestCollections && collectionsViewModel.allData.isEmpty()) {
            collectionsViewModel.refresh(environment)
        }
    }
    // 夹列表到位后，若尚未选中任何夹，按策略挑一个默认展示。
    LaunchedEffect(collections.toList(), selectedCollectionId) {
        if (selectedCollectionId == null) {
            selectedCollectionId = pickDefaultCollectionId(collections)
        }
    }

    // 选中夹的内容 ViewModel：id 变化时通过 key 切换实例，旧内容自动清空、新夹自动加载。
    val contentViewModel: CollectionContentViewModel? = selectedCollectionId?.let { id ->
        viewModel(key = id) { CollectionContentViewModel(id) }
    }
    LaunchedEffect(contentViewModel) {
        if (contentViewModel != null && contentViewModel.allData.isEmpty()) {
            contentViewModel.refresh(contentEnv)
        }
    }
    // 二次点击「收藏」tab：已在顶部则刷新夹列表与当前夹内容，否则滚回顶部（与其他 tab 一致）。
    LaunchedEffect(scrollToTopTrigger) {
        when (
            topLevelReselectAction(
                triggerDelta = scrollToTopTrigger - cachedScrollToTopTrigger,
                isAtTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0,
            )
        ) {
            TopLevelReselectAction.Refresh -> {
                if (!useTestCollections) {
                    collectionsViewModel.refresh(environment)
                    contentViewModel?.refresh(contentEnv)
                }
            }
            TopLevelReselectAction.ScrollToTop -> listState.animateScrollToItem(0)
            null -> {}
        }
        cachedScrollToTopTrigger = scrollToTopTrigger
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    if (searchVisible) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            placeholder = { Text("搜索当前收藏夹") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("collection_browse_search_field"),
                        )
                    } else {
                        Text(
                            text = contentViewModel?.title ?: "收藏",
                            modifier = Modifier.testTag("collection_browse_title"),
                        )
                    }
                },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(
                            onClick = navigator.onNavigateBack,
                            modifier = Modifier.testTag("collection_browse_back_button"),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            searchVisible = !searchVisible
                            if (!searchVisible) searchQuery = ""
                        },
                        modifier = Modifier.testTag("collection_browse_search_action"),
                    ) {
                        Icon(
                            if (searchVisible) Icons.Filled.Close else Icons.Filled.Search,
                            contentDescription = "搜索",
                        )
                    }
                    Box {
                        IconButton(
                            onClick = { folderMenuExpanded = true },
                            enabled = collections.isNotEmpty(),
                            modifier = Modifier.testTag("collection_browse_folder_switch_button"),
                        ) {
                            Icon(Icons.Filled.Folder, contentDescription = "切换收藏夹")
                        }
                        DropdownMenu(
                            expanded = folderMenuExpanded,
                            onDismissRequest = { folderMenuExpanded = false },
                            modifier = Modifier.testTag("collection_browse_folder_menu"),
                        ) {
                            collections.forEach { collection ->
                                DropdownMenuItem(
                                    text = { Text(collection.title) },
                                    trailingIcon = {
                                        if (collection.id == selectedCollectionId) {
                                            Icon(Icons.Filled.Check, contentDescription = null)
                                        }
                                    },
                                    onClick = {
                                        selectedCollectionId = collection.id
                                        folderMenuExpanded = false
                                    },
                                    modifier = Modifier.testTag("collection_browse_folder_menu_item_${collection.id}"),
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            // 收藏夹列表为空（测试注入空，或真实加载完仍空）→ 空态
            collections.isEmpty() && (useTestCollections || collectionsViewModel.isEnd) -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("还没有收藏夹", modifier = Modifier.testTag("collection_browse_empty_collections"))
                }
            }
            // 还没选中夹（通常夹列表仍在加载）→ 加载中
            contentViewModel == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.testTag("collection_browse_loading_collections"))
                }
            }
            // 选中夹但内容为空 → 空态
            contentViewModel.allData.isEmpty() && contentViewModel.isEnd -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("这个收藏夹是空的", modifier = Modifier.testTag("collection_browse_empty_content"))
                }
            }
            else -> {
                CollectionContentBody(
                    viewModel = contentViewModel,
                    environment = contentEnv,
                    collectionId = selectedCollectionId.orEmpty(),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .padding(innerPadding),
                    listState = listState,
                    tagPrefix = "collection_browse",
                    filter = searchQuery.takeIf { it.isNotBlank() }?.lowercase()?.let { needle ->
                        { item: FeedDisplayItem ->
                            item.title.lowercase().contains(needle) ||
                                item.summary?.lowercase()?.contains(needle) == true ||
                                item.authorName?.lowercase()?.contains(needle) == true
                        }
                    },
                )
            }
        }
    }
}

/**
 * 挑选默认展示的收藏夹 id：优先默认收藏夹，否则取第一个；列表为空返回 null。
 */
fun pickDefaultCollectionId(collections: List<Collection>): String? =
    collections.firstOrNull { it.isDefault }?.id ?: collections.firstOrNull()?.id
