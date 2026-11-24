package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EqProfileDao {
    @Query("SELECT * FROM eq_profiles ORDER BY sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<EqProfileEntity>>

    @Query("SELECT * FROM eq_profiles ORDER BY sortOrder ASC, id ASC")
    suspend fun getAllOnce(): List<EqProfileEntity>

    @Query("SELECT * FROM eq_profiles WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): EqProfileEntity?

    @Query("SELECT COUNT(*) FROM eq_profiles")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: EqProfileEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(profiles: List<EqProfileEntity>)

    @Update
    suspend fun update(profile: EqProfileEntity)

    @Update
    suspend fun updateAll(profiles: List<EqProfileEntity>)

    @Query("DELETE FROM eq_profiles WHERE id = :id AND isBuiltIn = 0")
    suspend fun deleteCustomById(id: Long)
}

@Dao
interface DeviceRoutingDao {
    @Query("SELECT * FROM device_routing")
    fun observeAll(): Flow<List<DeviceRoutingEntity>>

    @Query("SELECT * FROM device_routing")
    suspend fun getAllOnce(): List<DeviceRoutingEntity>

    @Query("SELECT * FROM device_routing WHERE deviceType = :deviceType LIMIT 1")
    suspend fun getForDevice(deviceType: String): DeviceRoutingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DeviceRoutingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<DeviceRoutingEntity>)
}

@Dao
interface AppRoutingDao {
    @Query("SELECT * FROM app_routing ORDER BY appLabel ASC")
    fun observeAll(): Flow<List<AppRoutingEntity>>

    @Query("SELECT * FROM app_routing WHERE packageName = :packageName LIMIT 1")
    suspend fun getForPackage(packageName: String): AppRoutingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AppRoutingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<AppRoutingEntity>)

    @Query("DELETE FROM app_routing WHERE packageName = :packageName")
    suspend fun delete(packageName: String)
}

@Dao
interface CachedLyricsDao {
    @Query("SELECT * FROM cached_lyrics WHERE trackKey = :trackKey LIMIT 1")
    suspend fun getByKey(trackKey: String): CachedLyricsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CachedLyricsEntity)
}
