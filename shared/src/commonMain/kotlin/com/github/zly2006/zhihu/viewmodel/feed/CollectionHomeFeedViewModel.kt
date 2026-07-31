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

package com.github.zly2006.zhihu.viewmodel.feed

import androidx.lifecycle.viewModelScope
import com.github.zly2006.zhihu.shared.data.FeedDisplayItem
import com.github.zly2006.zhihu.viewmodel.CollectionLibraryItem
import com.github.zly2006.zhihu.viewmodel.PaginationEnvironment
import com.github.zly2006.zhihu.viewmodel.filter.CollectionExposure
import com.github.zly2006.zhihu.viewmodel.filter.CollectionIndexDao
import com.github.zly2006.zhihu.viewmodel.filter.getContentFilterDatabase
import com.github.zly2006.zhihu.viewmodel.mergeCollectionIndexItems
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.random.Random
import kotlin.time.Clock

class CollectionHomeFeedViewModel(
    private val selectedCollectionIds: Set<String>,
    private val dao: CollectionIndexDao = getContentFilterDatabase().collectionIndexDao(),
    private val random: Random = Random.Default,
) : BaseFeedViewModel() {
    override val initialUrl: String = "local://collection-revisit"

    private var candidates: List<WeightedCollectionItem> = emptyList()
    private val emittedKeys = mutableSetOf<String>()
    private val exposedThisSession = mutableSetOf<String>()

    override val isEnd: Boolean
        get() = candidates.isNotEmpty() && emittedKeys.size >= candidates.size

    override fun refresh(environment: PaginationEnvironment) {
        candidates = emptyList()
        emittedKeys.clear()
        super.refresh(environment)
    }

    override suspend fun fetchFeeds(environment: PaginationEnvironment) {
        try {
            if (candidates.isEmpty()) {
                candidates = loadCandidates()
            }
            val remaining = candidates.filterNot { it.item.contentKey in emittedKeys }
            val page = weightedSampleWithoutReplacement(remaining, PAGE_SIZE, random)
            val displayPage = page.map { it.item.displayItem }
            emittedKeys.addAll(page.map { it.item.contentKey })
            addDisplayItems(displayPage)
            latestLoadedDisplayItems.value = displayPage
        } finally {
            isLoading = false
        }
    }

    fun markExposed(item: FeedDisplayItem) {
        val contentKey = candidates
            .firstOrNull { it.item.displayItem.stableKey == item.stableKey }
            ?.item
            ?.contentKey
            ?: return
        if (!exposedThisSession.add(contentKey)) return
        viewModelScope.launch {
            dao.recordExposure(contentKey, Clock.System.now().toEpochMilliseconds())
        }
    }

    private suspend fun loadCandidates(): List<WeightedCollectionItem> {
        val merged = mergeCollectionIndexItems(dao.getAllItems())
            .filter { selectedCollectionIds.isEmpty() || it.collectionIds.any(selectedCollectionIds::contains) }
        if (merged.isEmpty()) return emptyList()

        val keys = merged.map(CollectionLibraryItem::contentKey)
        val openedAt = keys
            .chunked(500)
            .flatMap { getContentFilterDatabase().contentOpenEventDao().getLastOpenedByKeys(it) }
            .associate { it.contentKey to it.lastOpenedAt }
        val exposures = dao.getAllExposures().associateBy(CollectionExposure::contentKey)
        val now = Clock.System.now().toEpochMilliseconds()

        return computeRevisitWeights(merged, openedAt, exposures, now)
    }

    private companion object {
        const val PAGE_SIZE = 20
    }
}

// 一天毫秒数：收藏重温权重使用，抽出为顶层常量以便 computeRevisitWeights 复用。
private const val DAY_MILLIS = 24L * 60L * 60L * 1_000L

/**
 * 收藏重温加权算法（纯函数，便于单元测试）。
 *
 * 权重规则：未读内容大幅加权；距上次阅读越久权重越高（封顶 180 天）；
 * 首页曝光次数越多权重越低；最近一天内曝光过的内容短期强降权。
 */
internal fun computeRevisitWeights(
    items: List<CollectionLibraryItem>,
    lastOpenedByKey: Map<String, Long>,
    exposuresByKey: Map<String, CollectionExposure>,
    now: Long,
): List<WeightedCollectionItem> = items.map { item ->
    val lastOpened = lastOpenedByKey[item.contentKey]
    val exposure = exposuresByKey[item.contentKey]
    val unreadBoost = if (lastOpened == null) 12.0 else 1.0
    val daysSinceRead = lastOpened
        ?.let { ((now - it).coerceAtLeast(0L) / DAY_MILLIS).toDouble() }
        ?: 180.0
    val unseenDurationBoost = 1.0 + daysSinceRead.coerceAtMost(180.0) / 30.0
    val exposurePenalty = 1.0 / (1.0 + (exposure?.impressionCount ?: 0) * 0.5)
    val recentExposurePenalty = if (
        exposure != null &&
        now - exposure.lastExposedAt in 0 until DAY_MILLIS
    ) {
        0.15
    } else {
        1.0
    }
    WeightedCollectionItem(
        item = item,
        weight = max(0.01, unreadBoost * unseenDurationBoost * exposurePenalty * recentExposurePenalty),
    )
}

internal data class WeightedCollectionItem(
    val item: CollectionLibraryItem,
    val weight: Double,
)

internal fun weightedSampleWithoutReplacement(
    source: List<WeightedCollectionItem>,
    count: Int,
    random: Random,
): List<WeightedCollectionItem> {
    val pool = source.toMutableList()
    val result = ArrayList<WeightedCollectionItem>(minOf(count, source.size))
    repeat(minOf(count, source.size)) {
        val totalWeight = pool.sumOf(WeightedCollectionItem::weight)
        var cursor = random.nextDouble(totalWeight)
        var selectedIndex = pool.lastIndex
        for (index in pool.indices) {
            cursor -= pool[index].weight
            if (cursor <= 0.0) {
                selectedIndex = index
                break
            }
        }
        result += pool.removeAt(selectedIndex)
    }
    return result
}
