package com.example.data.repository

import com.example.data.local.AppDatabase
import com.example.data.local.AppRoutingEntity
import com.example.data.local.CachedLyricsEntity
import com.example.data.local.DeviceRoutingEntity
import com.example.data.local.EqProfileEntity
import com.example.data.local.UserPreferencesRepository
import com.example.data.remote.LrcParser
import com.example.data.remote.LrclibClient
import com.example.data.remote.LrclibService
import com.example.model.AppRouting
import com.example.model.AudioInputSource
import com.example.model.DeviceRouting
import com.example.model.EqProfile
import com.example.model.OutputDeviceType
import com.example.model.PredefinedProfiles
import com.example.model.ProfileCategory
import com.example.model.SyncedLyrics
import com.example.model.VisualizerConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AudioRepository(
    private val database: AppDatabase,
    private val prefs: UserPreferencesRepository,
    private val lrclib: LrclibService = LrclibClient.api
) {
    private val profileDao = database.eqProfileDao()
    private val deviceDao = database.deviceRoutingDao()
    private val appDao = database.appRoutingDao()
    private val lyricsDao = database.cachedLyricsDao()
    private val seedMutex = Mutex()

    val profiles: Flow<List<EqProfile>> = profileDao.observeAll().map { list ->
        list.map { it.toDomain() }
    }

    val deviceRoutings: Flow<List<DeviceRouting>> = deviceDao.observeAll().map { list ->
        list.map { it.toDomain() }
    }

    val appRoutings: Flow<List<AppRouting>> = appDao.observeAll().map { list ->
        list.map { it.toDomain() }
    }

    val visualizerConfig: Flow<VisualizerConfig> = prefs.visualizerConfig
    val activeProfileId: Flow<Long> = prefs.activeProfileId
    val audioInputSource: Flow<AudioInputSource> = prefs.audioInputSource
    val autoEqSwitchEnabled: Flow<Boolean> = prefs.autoEqSwitchEnabled
    val autoLyricsEnabled: Flow<Boolean> = prefs.autoLyricsEnabled
    val eqEnabled: Flow<Boolean> = prefs.eqEnabled

    suspend fun ensureSeeded() = seedMutex.withLock {
        if (profileDao.count() == 0) {
            profileDao.insertAll(PredefinedProfiles.all.map { EqProfileEntity.fromDomain(it) })
        }
        if (deviceDao.getAllOnce().isEmpty()) {
            val allProfiles = profileDao.getAllOnce()
            val defaultId = allProfiles.firstOrNull { it.name == "Default" }?.id ?: 1L
            val bassId = allProfiles.firstOrNull { it.name == "Bass" }?.id ?: defaultId
            val highwayId = allProfiles.firstOrNull { it.name == "Highway" }?.id ?: defaultId
            val loudnessId = allProfiles.firstOrNull { it.name == "Loudness" }?.id ?: defaultId

            deviceDao.upsertAll(
                listOf(
                    DeviceRoutingEntity(OutputDeviceType.PHONE_SPEAKER.name, loudnessId, true),
                    DeviceRoutingEntity(OutputDeviceType.HEADPHONES_BLUETOOTH.name, bassId, true),
                    DeviceRoutingEntity(OutputDeviceType.EXTERNAL_SPEAKER.name, defaultId, true),
                    DeviceRoutingEntity(OutputDeviceType.USB_DAC.name, defaultId, true),
                    DeviceRoutingEntity(OutputDeviceType.CAR_AUDIO.name, highwayId, true)
                )
            )
        }
        val existingApps = appDao.getForPackage("com.spotify.music")
        if (existingApps == null) {
            val allProfiles = profileDao.getAllOnce()
            val bassId = allProfiles.firstOrNull { it.name == "Bass" }?.id ?: 1L
            val vocalId = allProfiles.firstOrNull { it.name == "Vocal" }?.id ?: 1L
            val clearVoiceId = allProfiles.firstOrNull { it.name == "Clear Voice" }?.id ?: 1L
            val defaultId = allProfiles.firstOrNull { it.name == "Default" }?.id ?: 1L
            appDao.upsertAll(
                listOf(
                    AppRoutingEntity("com.maxmpz.audioplayer", "Poweramp", defaultId, true),
                    AppRoutingEntity("com.spotify.music", "Spotify", bassId, true),
                    AppRoutingEntity("com.google.android.apps.youtube.music", "YouTube Music", bassId, true),
                    AppRoutingEntity("com.google.android.youtube", "YouTube", vocalId, true),
                    AppRoutingEntity("com.apple.android.music", "Apple Music", defaultId, true),
                    AppRoutingEntity("au.com.shiftyjelly.pocketcasts", "Pocket Casts", clearVoiceId, true)
                )
            )
        }
    }

    suspend fun getProfileById(id: Long): EqProfile? = profileDao.getById(id)?.toDomain()

    suspend fun saveProfile(profile: EqProfile): Long {
        val entity = EqProfileEntity.fromDomain(profile)
        return profileDao.insert(entity)
    }

    suspend fun updateProfile(profile: EqProfile) {
        profileDao.update(EqProfileEntity.fromDomain(profile))
    }

    suspend fun createCustomProfile(name: String, basedOn: EqProfile? = null): Long {
        val current = profileDao.getAllOnce()
        val maxSort = (current.maxOfOrNull { it.sortOrder } ?: 0) + 1
        val newProfile = EqProfile(
            id = 0,
            name = name.trim().ifEmpty { "Custom EQ" },
            category = ProfileCategory.CUSTOM,
            isBuiltIn = false,
            sortOrder = maxSort,
            bandsDb = basedOn?.bandsDb ?: List(10) { 0f },
            bassBoost = basedOn?.bassBoost ?: 0,
            trebleBoostDb = basedOn?.trebleBoostDb ?: 0f,
            masterGainDb = basedOn?.masterGainDb ?: 0f,
            balance = basedOn?.balance ?: 0f,
            agcEnabled = basedOn?.agcEnabled ?: false,
            smartBassEnabled = basedOn?.smartBassEnabled ?: false,
            limiterEnabled = basedOn?.limiterEnabled ?: true
        )
        return profileDao.insert(EqProfileEntity.fromDomain(newProfile))
    }

    suspend fun duplicateProfile(source: EqProfile): Long {
        val current = profileDao.getAllOnce()
        val maxSort = (current.maxOfOrNull { it.sortOrder } ?: 0) + 1
        return profileDao.insert(
            EqProfileEntity.fromDomain(
                source.copy(
                    id = 0,
                    name = "${source.name} Copy",
                    category = ProfileCategory.CUSTOM,
                    isBuiltIn = false,
                    sortOrder = maxSort
                )
            )
        )
    }

    suspend fun renameCustomProfile(profile: EqProfile, newName: String) {
        if (!profile.isBuiltIn && newName.isNotBlank()) {
            profileDao.update(EqProfileEntity.fromDomain(profile.copy(name = newName.trim())))
        }
    }

    suspend fun moveProfileOrder(profileId: Long, delta: Int) {
        val all = profileDao.getAllOnce().sortedBy { it.sortOrder }.toMutableList()
        val index = all.indexOfFirst { it.id == profileId }
        if (index == -1) return
        val target = (index + delta).coerceIn(0, all.lastIndex)
        if (target == index) return
        val moved = all.removeAt(index)
        all.add(target, moved)
        val reindexed = all.mapIndexed { idx, item -> item.copy(sortOrder = idx) }
        profileDao.updateAll(reindexed)
    }

    suspend fun deleteCustomProfile(id: Long) {
        profileDao.deleteCustomById(id)
    }

    suspend fun setActiveProfileId(id: Long) = prefs.setActiveProfileId(id)
    suspend fun setAudioInputSource(source: AudioInputSource) = prefs.setAudioInputSource(source)
    suspend fun setAutoEqSwitch(enabled: Boolean) = prefs.setAutoEqSwitch(enabled)
    suspend fun setAutoLyrics(enabled: Boolean) = prefs.setAutoLyrics(enabled)
    suspend fun setEqEnabled(enabled: Boolean) = prefs.setEqEnabled(enabled)
    suspend fun updateVisualizerConfig(transform: (VisualizerConfig) -> VisualizerConfig) =
        prefs.updateVisualizerConfig(transform)

    suspend fun setDeviceRouting(deviceType: OutputDeviceType, profileId: Long, autoEnabled: Boolean) {
        deviceDao.upsert(DeviceRoutingEntity(deviceType.name, profileId, autoEnabled))
    }

    suspend fun setAppRouting(packageName: String, appLabel: String, profileId: Long, autoEnabled: Boolean) {
        appDao.upsert(AppRoutingEntity(packageName, appLabel, profileId, autoEnabled))
    }

    suspend fun deleteAppRouting(packageName: String) {
        appDao.delete(packageName)
    }

    suspend fun resolveProfileForContext(packageName: String?, deviceType: OutputDeviceType): Long? {
        if (!packageName.isNullOrBlank()) {
            val appRule = appDao.getForPackage(packageName)
            if (appRule != null && appRule.autoSwitchEnabled) {
                return appRule.profileId
            }
        }
        val devRule = deviceDao.getForDevice(deviceType.name)
        if (devRule != null && devRule.autoSwitchEnabled) {
            return devRule.profileId
        }
        return null
    }

    suspend fun fetchLyrics(title: String, artist: String, durationSec: Int): Result<SyncedLyrics> {
        val cleanTitle = title.trim()
        val cleanArtist = artist.trim()
        if (cleanTitle.isEmpty()) {
            return Result.failure(IllegalArgumentException("Track title is required"))
        }
        val cacheKey = "${cleanArtist.lowercase()}|${cleanTitle.lowercase()}|$durationSec"
        lyricsDao.getByKey(cacheKey)?.let { cached ->
            val parsed = cached.toDomain(LrcParser::parse)
            if (parsed.lines.isNotEmpty()) return Result.success(parsed)
        }

        return runCatching {
            val exactResp = if (cleanArtist.isNotEmpty() && durationSec > 0) {
                lrclib.getExactLyrics(cleanTitle, cleanArtist, durationSec)
            } else null

            val dto = if (exactResp != null && exactResp.isSuccessful && exactResp.body() != null) {
                exactResp.body()
            } else {
                val searchResp = lrclib.searchLyrics(cleanTitle, cleanArtist)
                if (searchResp.isSuccessful) {
                    val list = searchResp.body().orEmpty()
                    list.firstOrNull { !it.syncedLyrics.isNullOrBlank() } ?: list.firstOrNull()
                } else null
            }

            if (dto == null || (dto.syncedLyrics.isNullOrBlank() && dto.plainLyrics.isNullOrBlank())) {
                error("No synchronized lyrics found on LRCLIB for \"$cleanTitle\"")
            }

            val resolvedDuration = dto.duration?.toInt() ?: durationSec.coerceAtLeast(180)
            val lines = LrcParser.parse(dto.syncedLyrics, dto.plainLyrics, resolvedDuration)
            lyricsDao.upsert(
                CachedLyricsEntity(
                    trackKey = cacheKey,
                    trackTitle = dto.trackName ?: cleanTitle,
                    artistName = dto.artistName ?: cleanArtist,
                    durationSec = resolvedDuration,
                    rawLrc = dto.syncedLyrics,
                    plainLyrics = dto.plainLyrics,
                    fetchedAtMs = System.currentTimeMillis()
                )
            )
            SyncedLyrics(
                trackTitle = dto.trackName ?: cleanTitle,
                artistName = dto.artistName ?: cleanArtist,
                durationSec = resolvedDuration,
                lines = lines,
                isSynced = !dto.syncedLyrics.isNullOrBlank(),
                source = "LRCLIB"
            )
        }
    }
}
