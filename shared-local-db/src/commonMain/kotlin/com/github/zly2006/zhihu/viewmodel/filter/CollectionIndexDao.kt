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

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
interface CollectionIndexDao {
    @Query("SELECT * FROM ${CollectionIndexItem.TABLE_NAME}")
    suspend fun getAllItems(): List<CollectionIndexItem>

    @Query("SELECT * FROM ${CollectionSyncState.TABLE_NAME} ORDER BY collectionTitle COLLATE NOCASE")
    suspend fun getAllSyncStates(): List<CollectionSyncState>

    @Query("SELECT * FROM ${CollectionExposure.TABLE_NAME}")
    suspend fun getAllExposures(): List<CollectionExposure>

    @Query("SELECT * FROM ${CollectionSyncState.TABLE_NAME} WHERE collectionId = :collectionId LIMIT 1")
    suspend fun getSyncState(collectionId: String): CollectionSyncState?

    @Query("SELECT * FROM ${CollectionExposure.TABLE_NAME} WHERE contentKey = :contentKey LIMIT 1")
    suspend fun getExposure(contentKey: String): CollectionExposure?

    @Upsert
    suspend fun upsertItems(items: List<CollectionIndexItem>)

    @Upsert
    suspend fun upsertSyncState(state: CollectionSyncState)

    @Upsert
    suspend fun upsertExposure(exposure: CollectionExposure)

    @Query("DELETE FROM ${CollectionIndexItem.TABLE_NAME} WHERE collectionId = :collectionId")
    suspend fun deleteItemsForCollection(collectionId: String)

    @Query("DELETE FROM ${CollectionIndexItem.TABLE_NAME} WHERE collectionId NOT IN (:collectionIds)")
    suspend fun deleteItemsOutsideCollections(collectionIds: List<String>)

    @Query("DELETE FROM ${CollectionSyncState.TABLE_NAME} WHERE collectionId NOT IN (:collectionIds)")
    suspend fun deleteStatesOutsideCollections(collectionIds: List<String>)

    @Query("DELETE FROM ${CollectionIndexItem.TABLE_NAME}")
    suspend fun deleteAllItems()

    @Query("DELETE FROM ${CollectionSyncState.TABLE_NAME}")
    suspend fun deleteAllSyncStates()

    @Transaction
    suspend fun replaceCollection(
        collectionId: String,
        items: List<CollectionIndexItem>,
        state: CollectionSyncState,
    ) {
        deleteItemsForCollection(collectionId)
        if (items.isNotEmpty()) {
            upsertItems(items)
        }
        upsertSyncState(state)
    }

    @Transaction
    suspend fun retainCollections(collectionIds: List<String>) {
        if (collectionIds.isEmpty()) {
            deleteAllItems()
            deleteAllSyncStates()
        } else {
            deleteItemsOutsideCollections(collectionIds)
            deleteStatesOutsideCollections(collectionIds)
        }
    }

    @Transaction
    suspend fun recordExposure(
        contentKey: String,
        exposedAt: Long,
    ) {
        val previous = getExposure(contentKey)
        upsertExposure(
            CollectionExposure(
                contentKey = contentKey,
                impressionCount = (previous?.impressionCount ?: 0) + 1,
                lastExposedAt = exposedAt,
            ),
        )
    }
}
