package com.cj.tapblok.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedWebsiteDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(blockedWebsite: BlockedWebsite)

    @Delete
    suspend fun delete(blockedWebsite: BlockedWebsite)

    @Query("SELECT * FROM blocked_websites ORDER BY domain COLLATE NOCASE ASC")
    fun getAllBlockedWebsites(): Flow<List<BlockedWebsite>>

    @Query("SELECT * FROM blocked_websites ORDER BY domain COLLATE NOCASE ASC")
    suspend fun getAllBlockedWebsitesList(): List<BlockedWebsite>
}
