package com.nm.story2mv.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface StoryDao {

    @Transaction
    @Query("SELECT * FROM stories ORDER BY createdAt DESC")
    fun observeStories(): Flow<List<StoryWithShots>>

    @Transaction
    @Query("SELECT * FROM stories WHERE id = :storyId")
    fun observeStory(storyId: Long): Flow<StoryWithShots?>

    @Query("SELECT * FROM assets ORDER BY createdAt DESC")
    fun observeAssets(): Flow<List<AssetEntity>>

    @Query(
        "SELECT * FROM assets WHERE (:query IS NULL OR :query = '' OR title LIKE '%' || :query || '%') " +
            "ORDER BY createdAt DESC"
    )
    fun observeAssets(query: String?): Flow<List<AssetEntity>>

    @Query("SELECT * FROM shots WHERE id = :shotId LIMIT 1")
    suspend fun getShot(shotId: String): ShotEntity?

    @Query("SELECT * FROM stories WHERE id = :storyId LIMIT 1")
    suspend fun getStory(storyId: Long): StoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStory(story: StoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStories(stories: List<StoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertShots(shots: List<ShotEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertShot(shot: ShotEntity)

    @Query("DELETE FROM shots WHERE storyId = :storyId")
    suspend fun deleteShotsForStory(storyId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAsset(asset: AssetEntity)

    @Query("SELECT COUNT(*) FROM stories")
    suspend fun countStories(): Int

    @Query("DELETE FROM assets WHERE id = :assetId")
    suspend fun deleteAsset(assetId: Long)
}

