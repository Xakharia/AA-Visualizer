package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.AppRouting
import com.example.model.DeviceRouting
import com.example.model.EqProfile
import com.example.model.LyricLine
import com.example.model.OutputDeviceType
import com.example.model.ProfileCategory
import com.example.model.SyncedLyrics

@Entity(tableName = "eq_profiles")
data class EqProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String,
    val isBuiltIn: Boolean,
    val sortOrder: Int,
    val bandsCsv: String,
    val bassBoost: Int,
    val trebleBoostDb: Float,
    val masterGainDb: Float,
    val balance: Float,
    val agcEnabled: Boolean,
    val smartBassEnabled: Boolean,
    val limiterEnabled: Boolean
) {
    fun toDomain(): EqProfile {
        val parsed = bandsCsv.split(",")
            .mapNotNull { it.trim().toFloatOrNull() }
            .let { list ->
                if (list.size == 10) list else List(10) { idx -> list.getOrElse(idx) { 0f } }
            }
        val cat = runCatching { ProfileCategory.valueOf(category) }.getOrDefault(ProfileCategory.CUSTOM)
        return EqProfile(
            id = id,
            name = name,
            category = cat,
            isBuiltIn = isBuiltIn,
            sortOrder = sortOrder,
            bandsDb = parsed,
            bassBoost = bassBoost,
            trebleBoostDb = trebleBoostDb,
            masterGainDb = masterGainDb,
            balance = balance,
            agcEnabled = agcEnabled,
            smartBassEnabled = smartBassEnabled,
            limiterEnabled = limiterEnabled
        )
    }

    companion object {
        fun fromDomain(profile: EqProfile): EqProfileEntity = EqProfileEntity(
            id = profile.id,
            name = profile.name,
            category = profile.category.name,
            isBuiltIn = profile.isBuiltIn,
            sortOrder = profile.sortOrder,
            bandsCsv = profile.bandsDb.joinToString(",") { "%.2f".format(java.util.Locale.US, it) },
            bassBoost = profile.bassBoost,
            trebleBoostDb = profile.trebleBoostDb,
            masterGainDb = profile.masterGainDb,
            balance = profile.balance,
            agcEnabled = profile.agcEnabled,
            smartBassEnabled = profile.smartBassEnabled,
            limiterEnabled = profile.limiterEnabled
        )
    }
}

@Entity(tableName = "device_routing")
data class DeviceRoutingEntity(
    @PrimaryKey val deviceType: String,
    val profileId: Long,
    val autoSwitchEnabled: Boolean
) {
    fun toDomain(): DeviceRouting = DeviceRouting(
        deviceType = runCatching { OutputDeviceType.valueOf(deviceType) }
            .getOrDefault(OutputDeviceType.PHONE_SPEAKER),
        profileId = profileId,
        autoSwitchEnabled = autoSwitchEnabled
    )
}

@Entity(tableName = "app_routing")
data class AppRoutingEntity(
    @PrimaryKey val packageName: String,
    val appLabel: String,
    val profileId: Long,
    val autoSwitchEnabled: Boolean
) {
    fun toDomain(): AppRouting = AppRouting(
        packageName = packageName,
        appLabel = appLabel,
        profileId = profileId,
        autoSwitchEnabled = autoSwitchEnabled
    )
}

@Entity(tableName = "cached_lyrics")
data class CachedLyricsEntity(
    @PrimaryKey val trackKey: String,
    val trackTitle: String,
    val artistName: String,
    val durationSec: Int,
    val rawLrc: String?,
    val plainLyrics: String?,
    val fetchedAtMs: Long
) {
    fun toDomain(parser: (String?, String?, Int) -> List<LyricLine>): SyncedLyrics {
        val parsed = parser(rawLrc, plainLyrics, durationSec)
        return SyncedLyrics(
            trackTitle = trackTitle,
            artistName = artistName,
            durationSec = durationSec,
            lines = parsed,
            isSynced = !rawLrc.isNullOrBlank(),
            source = "LRCLIB (Cached)"
        )
    }
}
