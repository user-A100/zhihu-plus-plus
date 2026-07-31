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

import com.github.zly2006.zhihu.viewmodel.filter.CollectionIndexItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CollectionMergeTest {
    private fun item(
        contentKey: String,
        collectionId: String,
        collectionTitle: String,
        collectedAt: Long,
        title: String = "标题",
    ) = CollectionIndexItem(
        contentKey = contentKey,
        collectionId = collectionId,
        collectionTitle = collectionTitle,
        contentType = contentKey.substringBefore(':'),
        contentId = contentKey.substringAfter(':'),
        collectedAt = collectedAt,
        title = title,
        excerpt = null,
        details = "",
        authorName = null,
        avatarUrl = null,
        navDestinationJson = "{}",
    )

    @Test
    fun mergesSameContentAcrossFoldersIntoOneRow() {
        val rows = listOf(
            item("answer:1", "c1", "夹一", collectedAt = 1_000L),
            item("answer:1", "c2", "夹二", collectedAt = 2_000L),
        )

        val merged = mergeCollectionIndexItems(rows)

        assertEquals(1, merged.size)
        val row = merged.single()
        assertEquals("answer:1", row.contentKey)
        // 使用最近一次收藏时间
        assertEquals(2_000L, row.collectedAt)
        // 保留所有收藏夹关系
        assertEquals(setOf("c1", "c2"), row.collectionIds)
        // 收藏夹标题按收藏时间倒序
        assertEquals(listOf("夹二", "夹一"), row.collectionTitles)
        // details 携带全部收藏夹名
        assertTrue(row.displayItem.details.contains("夹二"))
        assertTrue(row.displayItem.details.contains("夹一"))
        // 收藏时间线走自有路径，不携带推荐 feed
        assertNull(row.displayItem.feed)
    }

    @Test
    fun keepsSeparateContentSeparateAndSortsByCollectedAtDesc() {
        val rows = listOf(
            item("answer:1", "c1", "夹一", collectedAt = 2_000L, title = "A"),
            item("answer:2", "c1", "夹一", collectedAt = 1_000L, title = "B"),
            item("article:9", "c1", "夹一", collectedAt = 3_000L, title = "C"),
        )

        val merged = mergeCollectionIndexItems(rows)

        assertEquals(3, merged.size)
        assertEquals(listOf("C", "A", "B"), merged.map { it.displayItem.title })
    }
}
