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

import com.github.zly2006.zhihu.navigation.Account
import com.github.zly2006.zhihu.navigation.Daily
import com.github.zly2006.zhihu.navigation.Follow
import com.github.zly2006.zhihu.navigation.Home
import com.github.zly2006.zhihu.navigation.HotList
import com.github.zly2006.zhihu.navigation.MyCollections
import com.github.zly2006.zhihu.navigation.OnlineHistory
import com.github.zly2006.zhihu.ui.subscreens.bottomBarItemOrderFromPreference
import com.github.zly2006.zhihu.ui.subscreens.defaultBottomBarSelectionKeys
import com.github.zly2006.zhihu.ui.subscreens.normalizeBottomBarItemOrder
import com.github.zly2006.zhihu.ui.subscreens.normalizeBottomBarSelection
import com.github.zly2006.zhihu.ui.subscreens.resolveValidStartDestinationKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ZhihuMainPreferencesTest {
    @Test
    fun defaultBottomBarSelectionIsUnifiedAndIgnoresDuo3Flag() {
        // 产品决策：底栏统一默认（主页、关注、收藏、历史、账号），不再随 duo3 变化
        val expected = linkedSetOf(
            Home.name,
            Follow.name,
            MyCollections.name,
            OnlineHistory.name,
            Account.name,
        )
        assertEquals(expected, defaultBottomBarSelectionKeys(duo3HomeAccount = false))
        assertEquals(expected, defaultBottomBarSelectionKeys(duo3HomeAccount = true))
    }

    @Test
    fun normalizeKeepsAFiveItemSelectionIntactWithoutForcingAccount() {
        // 新规则：1-5 项，账号不再被强制加入，原选择被原样保留
        val normalized = normalizeBottomBarSelection(
            selectedKeys = linkedSetOf(Home.name, Follow.name, HotList.name, Daily.name, OnlineHistory.name),
            duo3HomeAccount = false,
        )

        assertEquals(5, normalized.size)
        assertTrue(Home.name in normalized)
        assertTrue(HotList.name in normalized)
        assertFalse(Account.name in normalized)
    }

    @Test
    fun normalizeDoesNotForceFillSelectionUpToThreeItems() {
        // 新规则：允许 1-5 项，两个有效项保持为两项，不会回填到 3
        val normalized = normalizeBottomBarSelection(
            selectedKeys = linkedSetOf(Home.name, Account.name),
            duo3HomeAccount = true,
            enforceMinimumSelection = true,
        )

        assertEquals(setOf(Home.name, Account.name), normalized)
    }

    @Test
    fun resolveValidStartDestinationFallsBackToFirstAvailableDestination() {
        assertEquals(
            Follow.name,
            resolveValidStartDestinationKey(
                preferredKey = HotList.name,
                availableKeysInOrder = listOf(Follow.name, Daily.name),
            ),
        )
    }

    @Test
    fun normalizeBottomBarSelectionAllowsCollectionsEntry() {
        val normalized = normalizeBottomBarSelection(
            selectedKeys = linkedSetOf(Home.name, HotList.name, MyCollections.name),
            duo3HomeAccount = true,
            enforceMinimumSelection = true,
        )

        assertTrue(MyCollections.name in normalized)
        assertEquals(3, normalized.size)
    }

    @Test
    fun normalizeBottomBarItemOrderKeepsPreferredOrderAndAppendsMissingSelectedItems() {
        val normalized = normalizeBottomBarItemOrder(
            preferredOrderKeys = listOf(HotList.name, MyCollections.name, HotList.name, "Unknown"),
            selectedKeys = linkedSetOf(Home.name, HotList.name, MyCollections.name, Daily.name),
        )

        assertEquals(
            listOf(HotList.name, MyCollections.name, Home.name, Daily.name),
            normalized,
        )
    }

    @Test
    fun bottomBarItemOrderFromPreferenceIgnoresUnselectedAndUnknownKeys() {
        val normalized = bottomBarItemOrderFromPreference(
            preferenceValue = "${MyCollections.name}, Unknown, ${HotList.name}, ${OnlineHistory.name}",
            selectedKeys = linkedSetOf(Home.name, HotList.name, MyCollections.name),
        )

        assertEquals(
            listOf(MyCollections.name, HotList.name, Home.name),
            normalized,
        )
    }
}
