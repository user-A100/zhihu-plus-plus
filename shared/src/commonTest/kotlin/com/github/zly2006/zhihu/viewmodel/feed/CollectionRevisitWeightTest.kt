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

import com.github.zly2006.zhihu.shared.data.FeedDisplayItem
import com.github.zly2006.zhihu.viewmodel.CollectionLibraryItem
import com.github.zly2006.zhihu.viewmodel.filter.CollectionExposure
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CollectionRevisitWeightTest {
    private val now = 1_700_000_000_000L
    private val day = 24L * 60L * 60L * 1_000L

    private fun libraryItem(key: String): CollectionLibraryItem = CollectionLibraryItem(
        contentKey = key,
        collectedAt = now,
        collectionIds = setOf("c"),
        collectionTitles = listOf("夹"),
        displayItem = FeedDisplayItem(title = key, summary = null, details = "", feed = null),
    )

    private fun weightOf(
        target: CollectionLibraryItem,
        lastOpenedAt: Long? = null,
        exposure: CollectionExposure? = null,
    ): Double {
        val opened = if (lastOpenedAt != null) mapOf(target.contentKey to lastOpenedAt) else emptyMap()
        val exposures = if (exposure != null) mapOf(target.contentKey to exposure) else emptyMap()
        return computeRevisitWeights(listOf(target), opened, exposures, now).single().weight
    }

    @Test
    fun unreadContentWeighsMoreThanRecentlyRead() {
        val unreadWeight = weightOf(libraryItem("a")) // 未读
        val readWeight = weightOf(libraryItem("b"), lastOpenedAt = now) // 今天读过
        assertTrue(unreadWeight > readWeight, "unread=$unreadWeight read=$readWeight")
    }

    @Test
    fun longAgoReadWeighsMoreThanRecentlyRead() {
        val longAgoWeight = weightOf(libraryItem("a"), lastOpenedAt = now - 60L * day)
        val recentWeight = weightOf(libraryItem("b"), lastOpenedAt = now)
        assertTrue(longAgoWeight > recentWeight, "longAgo=$longAgoWeight recent=$recentWeight")
    }

    @Test
    fun frequentExposureReducesWeight() {
        val exposedWeight = weightOf(
            libraryItem("a"),
            exposure = CollectionExposure(
                contentKey = "a",
                impressionCount = 10,
                lastExposedAt = now - 7L * day,
            ),
        )
        val freshWeight = weightOf(libraryItem("b")) // 未曝光
        assertTrue(freshWeight > exposedWeight, "fresh=$freshWeight exposed=$exposedWeight")
    }

    @Test
    fun recentExposureAppliesShortTermPenalty() {
        val recentWeight = weightOf(
            libraryItem("a"),
            exposure = CollectionExposure(contentKey = "a", impressionCount = 1, lastExposedAt = now - 1L),
        )
        val oldWeight = weightOf(
            libraryItem("b"),
            exposure = CollectionExposure(contentKey = "b", impressionCount = 1, lastExposedAt = now - 7L * day),
        )
        assertTrue(oldWeight > recentWeight, "old=$oldWeight recent=$recentWeight")
    }

    @Test
    fun weightsNeverDropBelowFloor() {
        // 极端：今天读过 + 高频曝光 + 最近曝光
        val extreme = weightOf(
            libraryItem("a"),
            lastOpenedAt = now,
            exposure = CollectionExposure(contentKey = "a", impressionCount = 1_000, lastExposedAt = now),
        )
        assertTrue(extreme >= 0.01, "extreme=$extreme")
    }

    @Test
    fun samplerReturnsNoDuplicatesAndRespectsCount() {
        val source = (1..5).map { WeightedCollectionItem(libraryItem("k$it"), 1.0) }
        val sampled = weightedSampleWithoutReplacement(source, count = 3, random = Random(42))
        assertEquals(3, sampled.size)
        val keys = sampled.map { it.item.contentKey }
        assertEquals(keys.size, keys.toSet().size) // 单页无重复
        assertTrue(sampled.all { row -> row.item.contentKey in source.map { it.item.contentKey } })
    }

    @Test
    fun samplerNeverExceedsSourceSize() {
        val source = (1..2).map { WeightedCollectionItem(libraryItem("k$it"), 1.0) }
        val sampled = weightedSampleWithoutReplacement(source, count = 10, random = Random(7))
        assertEquals(2, sampled.size)
    }

    @Test
    fun samplerFavoursHeavilyWeightedItem() {
        val source = listOf(
            WeightedCollectionItem(libraryItem("light"), 0.001),
            WeightedCollectionItem(libraryItem("heavy"), 1_000.0),
        )
        var heavyHits = 0
        repeat(50) { seed ->
            val picked = weightedSampleWithoutReplacement(source, count = 1, random = Random(seed.toLong()))
                .single()
                .item
                .contentKey
            if (picked == "heavy") heavyHits++
        }
        // 高权项被选中的概率应接近 100%
        assertTrue(heavyHits >= 49, "heavy picked $heavyHits/50 times")
    }

    @Test
    fun samplerReturnsEmptyForEmptySource() {
        val sampled = weightedSampleWithoutReplacement(emptyList(), count = 5, random = Random(0))
        assertTrue(sampled.isEmpty())
    }
}
