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

package com.github.zly2006.zhihu.ui.subscreens

import com.github.zly2006.zhihu.navigation.Account
import com.github.zly2006.zhihu.navigation.Daily
import com.github.zly2006.zhihu.navigation.Follow
import com.github.zly2006.zhihu.navigation.Home
import com.github.zly2006.zhihu.navigation.HotList
import com.github.zly2006.zhihu.navigation.MyCollections
import com.github.zly2006.zhihu.navigation.OnlineHistory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BottomBarSelectionTest {
    @Test
    fun emptySelectionFallsBackToSafeDefault() {
        val result = normalizeBottomBarSelection(emptyList(), duo3HomeAccount = false)
        assertEquals(
            setOf(Home.name, Follow.name, MyCollections.name, OnlineHistory.name, Account.name),
            result,
        )
    }

    @Test
    fun allowsASingleSelectedItem() {
        val result = normalizeBottomBarSelection(listOf(Follow.name), duo3HomeAccount = false)
        assertEquals(setOf(Follow.name), result)
    }

    @Test
    fun capsAtFiveItemsByDroppingTheLastOne() {
        val six = listOf(
            Home.name,
            Follow.name,
            HotList.name,
            Daily.name,
            OnlineHistory.name,
            MyCollections.name,
        )
        val result = normalizeBottomBarSelection(six, duo3HomeAccount = false)
        assertEquals(5, result.size)
        // 按既定顺序，最后一项被裁掉
        assertFalse(MyCollections.name in result)
    }

    @Test
    fun filtersUnknownKeys() {
        val result = normalizeBottomBarSelection(
            listOf(Home.name, "bogus"),
            duo3HomeAccount = false,
        )
        assertEquals(setOf(Home.name), result)
    }

    @Test
    fun defaultKeysNoLongerVaryByDuo3Flag() {
        // 产品决策：底栏统一默认，duo3 标志不再改变默认集合
        assertEquals(
            defaultBottomBarSelectionKeys(duo3HomeAccount = false),
            defaultBottomBarSelectionKeys(duo3HomeAccount = true),
        )
        val defaults = defaultBottomBarSelectionKeys(duo3HomeAccount = false)
        assertEquals(5, defaults.size)
        assertTrue(Account.name in defaults)
        assertTrue(MyCollections.name in defaults)
    }
}
