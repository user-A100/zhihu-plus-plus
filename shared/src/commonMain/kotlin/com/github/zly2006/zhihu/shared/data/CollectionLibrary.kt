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

package com.github.zly2006.zhihu.shared.data

const val HOME_CONTENT_SOURCE_PREFERENCE_KEY = "home_content_source"
const val COLLECTION_HOME_SELECTED_IDS_PREFERENCE_KEY = "collection_home_selected_ids"

enum class HomeContentSource(
    val key: String,
    val displayName: String,
    val description: String,
) {
    RECOMMENDATION(
        key = "recommendation",
        displayName = "知乎推荐",
        description = "使用当前选择的推荐算法",
    ),
    COLLECTION_REVISIT(
        key = "collection_revisit",
        displayName = "收藏重温",
        description = "从本地收藏索引中随机重温，未读内容优先",
    ),
    ;

    companion object {
        fun fromKey(key: String?): HomeContentSource = entries.firstOrNull { it.key == key } ?: RECOMMENDATION
    }
}

enum class CollectionTimelineSort(
    val displayName: String,
) {
    NEWEST("最新收藏"),
    OLDEST("最早收藏"),
}

enum class CollectionTimelineGrouping(
    val displayName: String,
) {
    NONE("不分组"),
    WEEK("按周"),
    MONTH("按月"),
    YEAR("按年"),
}
