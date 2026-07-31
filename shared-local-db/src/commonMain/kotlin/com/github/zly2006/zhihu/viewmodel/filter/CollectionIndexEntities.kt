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

package com.github.zly2006.zhihu.viewmodel.filter

import androidx.room.Entity
import androidx.room.Index

/**
 * 收藏内容与收藏夹的本地索引关系。
 *
 * 同一内容可属于多个收藏夹，因此使用内容身份键与收藏夹 ID 组成联合主键。这里只保存时间线、
 * 搜索和现有阅读器导航所需的轻量字段，不缓存正文。
 */
@Entity(
    tableName = CollectionIndexItem.TABLE_NAME,
    primaryKeys = ["contentKey", "collectionId"],
    indices = [
        Index(value = ["collectionId"]),
        Index(value = ["contentKey"]),
        Index(value = ["collectedAt"]),
    ],
)
data class CollectionIndexItem(
    val contentKey: String,
    val collectionId: String,
    val collectionTitle: String,
    val contentType: String,
    val contentId: String,
    val collectedAt: Long,
    val title: String,
    val excerpt: String?,
    val details: String,
    val authorName: String?,
    val avatarUrl: String?,
    val navDestinationJson: String,
) {
    companion object {
        const val TABLE_NAME = "collection_index_items"
    }
}

/** 每个收藏夹独立保存同步游标，以便首轮索引可以中断后继续。 */
@Entity(tableName = CollectionSyncState.TABLE_NAME, primaryKeys = ["collectionId"])
data class CollectionSyncState(
    val collectionId: String,
    val collectionTitle: String,
    val itemCount: Int,
    val remoteUpdatedAt: Long,
    val lastSyncedAt: Long,
    val completed: Boolean,
) {
    companion object {
        const val TABLE_NAME = "collection_sync_states"
    }
}

/** 首页收藏重温的轻量曝光信号；阅读信号仍复用 content_open_events。 */
@Entity(tableName = CollectionExposure.TABLE_NAME, primaryKeys = ["contentKey"])
data class CollectionExposure(
    val contentKey: String,
    val impressionCount: Int,
    val lastExposedAt: Long,
) {
    companion object {
        const val TABLE_NAME = "collection_exposures"
    }
}
