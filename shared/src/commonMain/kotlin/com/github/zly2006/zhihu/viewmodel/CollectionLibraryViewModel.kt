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

package com.github.zly2006.zhihu.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.zly2006.zhihu.shared.data.Feed
import com.github.zly2006.zhihu.shared.data.FeedDisplayItem
import com.github.zly2006.zhihu.shared.data.ZhihuJson
import com.github.zly2006.zhihu.shared.data.ZhihuPaging
import com.github.zly2006.zhihu.shared.data.navDestination
import com.github.zly2006.zhihu.shared.data.toFeedDisplayItemNavDestinationJson
import com.github.zly2006.zhihu.shared.util.Log
import com.github.zly2006.zhihu.ui.Collection
import com.github.zly2006.zhihu.viewmodel.filter.CollectionIndexDao
import com.github.zly2006.zhihu.viewmodel.filter.CollectionIndexItem
import com.github.zly2006.zhihu.viewmodel.filter.CollectionSyncState
import com.github.zly2006.zhihu.viewmodel.filter.getContentFilterDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlin.time.Clock
import kotlin.time.Instant

data class CollectionLibraryItem(
    val contentKey: String,
    val collectedAt: Long,
    val collectionIds: Set<String>,
    val collectionTitles: List<String>,
    val displayItem: FeedDisplayItem,
) {
    val searchableText: String = buildString {
        append(displayItem.title)
        append(' ')
        append(displayItem.summary.orEmpty())
        append(' ')
        append(displayItem.authorName.orEmpty())
        append(' ')
        append(collectionTitles.joinToString(" "))
    }.lowercase()
}

class CollectionLibraryViewModel(
    private val dao: CollectionIndexDao = getContentFilterDatabase().collectionIndexDao(),
) : ViewModel() {
    val items = mutableStateListOf<CollectionLibraryItem>()
    val syncStates = mutableStateListOf<CollectionSyncState>()

    var hasLoaded by mutableStateOf(false)
        private set
    var isSyncing by mutableStateOf(false)
        private set
    var syncProgress by mutableStateOf("")
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun load() {
        viewModelScope.launch {
            reloadFromDatabase()
            hasLoaded = true
        }
    }

    fun synchronize(
        environment: ZhihuApiEnvironment,
        urlToken: String,
        force: Boolean,
    ) {
        if (isSyncing) return
        if (urlToken.isBlank()) {
            errorMessage = "请先使用知乎加加现有的账号入口登录知乎"
            return
        }

        viewModelScope.launch {
            isSyncing = true
            errorMessage = null
            syncProgress = "正在读取收藏夹"
            val syncStart = Clock.System.now().toEpochMilliseconds()
            try {
                val collections = fetchAllCollections(environment, urlToken)
                Log.i("ZhicangPerf", "fetchCollections: ${collections.size} in ${Clock.System.now().toEpochMilliseconds() - syncStart}ms", null)
                val collectionIds = collections.map { it.id }
                dao.retainCollections(collectionIds)

                var failedCount = 0
                collections.forEachIndexed { index, collection ->
                    val previous = dao.getSyncState(collection.id)
                    val unchanged = !force &&
                        previous?.completed == true &&
                        previous.itemCount == collection.itemCount &&
                        previous.remoteUpdatedAt == collection.updatedTime &&
                        previous.collectionTitle == collection.title
                    if (unchanged) return@forEachIndexed

                    syncProgress = "正在同步 ${index + 1}/${collections.size}：${collection.title}"
                    dao.upsertSyncState(
                        CollectionSyncState(
                            collectionId = collection.id,
                            collectionTitle = collection.title,
                            itemCount = collection.itemCount,
                            remoteUpdatedAt = collection.updatedTime,
                            lastSyncedAt = previous?.lastSyncedAt ?: 0L,
                            completed = false,
                        ),
                    )
                    val colStart = Clock.System.now().toEpochMilliseconds()
                    try {
                        val remoteItems = fetchAllCollectionItems(environment, collection.id)
                        val indexedItems = remoteItems.mapNotNull { item ->
                            item.toIndexItem(collection)
                        }
                        Log.i("ZhicangPerf", "fetchItems[${collection.title}]: raw=${remoteItems.size} indexed=${indexedItems.size} in ${Clock.System.now().toEpochMilliseconds() - colStart}ms", null)
                        dao.replaceCollection(
                            collectionId = collection.id,
                            items = indexedItems,
                            state = CollectionSyncState(
                                collectionId = collection.id,
                                collectionTitle = collection.title,
                                itemCount = collection.itemCount,
                                remoteUpdatedAt = collection.updatedTime,
                                lastSyncedAt = Clock.System.now().toEpochMilliseconds(),
                                completed = true,
                            ),
                        )
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        failedCount++
                    }
                }

                reloadFromDatabase()
                Log.i("ZhicangPerf", "syncTotal: collections=${collections.size} failed=$failedCount items=${items.size} total=${Clock.System.now().toEpochMilliseconds() - syncStart}ms", null)
                syncProgress = if (failedCount == 0) {
                    "收藏已同步"
                } else {
                    "同步完成，$failedCount 个收藏夹稍后重试"
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorMessage = error.message ?: "同步收藏失败"
            } finally {
                isSyncing = false
            }
        }
    }

    private suspend fun reloadFromDatabase() {
        val start = Clock.System.now().toEpochMilliseconds()
        val allItems = dao.getAllItems()
        val t1 = Clock.System.now().toEpochMilliseconds()
        val merged = mergeCollectionIndexItems(allItems)
        val t2 = Clock.System.now().toEpochMilliseconds()
        items.clear()
        items.addAll(merged)
        syncStates.clear()
        syncStates.addAll(dao.getAllSyncStates())
        val t3 = Clock.System.now().toEpochMilliseconds()
        Log.i("ZhicangPerf", "reload: rows=${allItems.size} query=${t1 - start}ms merge=${t2 - t1}ms states=${t3 - t2}ms total=${t3 - start}ms", null)
    }
}

private suspend fun fetchAllCollections(
    environment: ZhihuApiEnvironment,
    urlToken: String,
): List<Collection> = fetchAllPages(
    environment = environment,
    initialUrl = "https://www.zhihu.com/api/v4/people/$urlToken/collections",
    include = "",
)

private suspend fun fetchAllCollectionItems(
    environment: ZhihuApiEnvironment,
    collectionId: String,
): List<CollectionItem> = fetchAllPages(
    environment = environment,
    initialUrl = "https://www.zhihu.com/api/v4/collections/$collectionId/items",
    include = "data[*].content,excerpt,headline,target.author.badge_v2",
)

private suspend inline fun <reified T> fetchAllPages(
    environment: ZhihuApiEnvironment,
    initialUrl: String,
    include: String,
): List<T> {
    val result = mutableListOf<T>()
    val visitedUrls = mutableSetOf<String>()
    var nextUrl = initialUrl

    while (nextUrl.isNotBlank() && visitedUrls.add(nextUrl)) {
        val response = environment.fetchJson(nextUrl.replace("http://", "https://"), include)
            ?: error("知乎返回了空响应，请确认当前账号仍处于登录状态")
        val data = response["data"] as? JsonArray
            ?: error("知乎收藏接口未返回内容列表")
        data.forEach { element ->
            runCatching { ZhihuJson.decodeJson<T>(element) }
                .onSuccess(result::add)
                .onFailure { environment.logDecodeFailure("CollectionLibrary", element, it as Exception) }
        }
        val paging = response["paging"]
            ?.let { ZhihuJson.decodeJson<ZhihuPaging>(it) }
            ?: break
        if (paging.isEnd) break
        nextUrl = paging.next
    }
    return result
}

private fun CollectionItem.toIndexItem(collection: Collection): CollectionIndexItem? {
    val destination = content.navDestination ?: return null
    val (contentType, contentId) = content.identityParts() ?: return null
    val navJson = destination.toFeedDisplayItemNavDestinationJson()
    val collectedAt = runCatching { Instant.parse(created).toEpochMilliseconds() }
        .getOrElse {
            content.createdTime
                .takeIf { it > 0 }
                ?.times(1_000L)
                ?: collection.updatedTime.times(1_000L)
        }
    return CollectionIndexItem(
        contentKey = "$contentType:$contentId",
        collectionId = collection.id,
        collectionTitle = collection.title,
        contentType = contentType,
        contentId = contentId,
        collectedAt = collectedAt,
        title = content.title,
        excerpt = content.excerpt,
        details = content.detailsText,
        authorName = content.author?.name,
        avatarUrl = content.author?.avatarUrl,
        navDestinationJson = navJson,
    )
}

private fun Feed.Target.identityParts(): Pair<String, String>? = when (this) {
    is Feed.AnswerTarget -> "answer" to id.toString()
    is Feed.ArticleTarget -> "article" to id.toString()
    is Feed.PinTarget -> "pin" to id.toString()
    is Feed.QuestionTarget -> "question" to id.toString()
    is Feed.VideoTarget -> null
}

fun mergeCollectionIndexItems(rows: List<CollectionIndexItem>): List<CollectionLibraryItem> = rows
    .groupBy(CollectionIndexItem::contentKey)
    .map { (contentKey, memberships) ->
        val latest = memberships.maxBy(CollectionIndexItem::collectedAt)
        val folderTitles = memberships
            .sortedByDescending(CollectionIndexItem::collectedAt)
            .map(CollectionIndexItem::collectionTitle)
            .distinct()
        CollectionLibraryItem(
            contentKey = contentKey,
            collectedAt = latest.collectedAt,
            collectionIds = memberships.mapTo(linkedSetOf(), CollectionIndexItem::collectionId),
            collectionTitles = folderTitles,
            displayItem = FeedDisplayItem(
                title = latest.title,
                summary = latest.excerpt,
                details = buildString {
                    append(latest.details)
                    if (folderTitles.isNotEmpty()) {
                        append(" · ")
                        append(folderTitles.joinToString("、"))
                    }
                },
                feed = null,
                navDestinationJson = latest.navDestinationJson,
                avatarSrc = latest.avatarUrl,
                authorName = latest.authorName,
            ),
        )
    }.sortedByDescending(CollectionLibraryItem::collectedAt)
