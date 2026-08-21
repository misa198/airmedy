package me.misa198.airmedy.sync

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.Transaction
import androidx.room.withTransaction
import java.io.File
import java.util.UUID
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.misa198.airmedy.sync.LibrarySyncAsset
import me.misa198.airmedy.sync.LibrarySyncManifest
import me.misa198.airmedy.sync.LibrarySyncRequest
import me.misa198.airmedy.sync.LibrarySyncStore
import me.misa198.airmedy.sync.LibrarySyncProtocol
import me.misa198.airmedy.sync.PulledAsset
import me.misa198.airmedy.sync.PlaylistMutationStore
import me.misa198.airmedy.sync.PlaylistArtworkStagingStore
import me.misa198.airmedy.sync.StagedPlaylistArtwork
import me.misa198.airmedy.player.TrackAnalysis
import me.misa198.airmedy.player.ListeningSession
import me.misa198.airmedy.player.PlaybackAttempt
import me.misa198.airmedy.player.ListeningWrite
import me.misa198.airmedy.player.PlaybackEndReason
import me.misa198.airmedy.player.DailyTrackListeningStat
import me.misa198.airmedy.player.DailyPlaybackAttemptStat
import me.misa198.airmedy.sync.ListeningSyncSnapshot
import me.misa198.airmedy.sync.ListeningSyncStore

@Entity(tableName = "sync_plans", primaryKeys = ["planId"])
internal data class SyncPlanEntity(
    val planId: String,
    val desktopId: String,
    val manifestJson: String,
    val state: String,
    val active: Boolean,
)

@Entity(tableName = "sync_assets", primaryKeys = ["planId", "assetId"])
internal data class SyncAssetEntity(
    val planId: String,
    val assetId: String,
    val kind: String,
    val sha256: String,
    val size: Long,
    val relativePath: String?,
)

@Entity(tableName = "sync_tracks", primaryKeys = ["planId", "trackId"])
internal data class SyncTrackEntity(
    val planId: String,
    val trackId: String,
    val title: String,
    val artists: String,
    val album: String = "",
    val albumId: String = "",
    val artworkKey: String?,
    val playCount: Int = 0,
    val createdAt: String = "",
    val discNumber: Int = 0,
    val trackNumber: Int = 0,
    val syncOrder: Int = 0,
    val rawJson: String,
)

@Entity(tableName = "sync_playlists", primaryKeys = ["planId", "playlistId"])
internal data class SyncPlaylistEntity(
    val planId: String,
    val playlistId: String,
    val name: String,
    val trackIdsJson: String,
    val rawJson: String,
)

@Entity(tableName = "playlist_mutations", primaryKeys = ["mutationId"])
internal data class PlaylistMutationEntity(
    val mutationId: String,
    val playlistId: String,
    val operation: String,
    val updatedAt: Long,
    val payloadJson: String,
    val state: String = "pending",
)

/** A playlist created on this device before the desktop returns an authoritative snapshot. */
@Entity(tableName = "local_playlists", primaryKeys = ["playlistId"])
internal data class LocalPlaylistEntity(
    val playlistId: String,
    val name: String,
    val mutationId: String,
    val syncState: String = "pending",
    val artworkSha256: String? = null,
)

@Entity(tableName = "playlist_artwork_staging", primaryKeys = ["sha256"])
internal data class PlaylistArtworkStagingEntity(val sha256: String, val mime: String, val size: Long, val relativePath: String)

@Entity(tableName = "sync_documents", primaryKeys = ["planId", "kind", "documentKey"])
internal data class SyncDocumentEntity(
    val planId: String,
    val kind: String,
    val documentKey: String,
    val rawJson: String,
)

@Entity(tableName = "listening_sessions")
internal data class ListeningSessionEntity(
    @androidx.room.PrimaryKey val id: String,
    val sourceDeviceId: String,
    val trackId: String,
    val startedAt: Long,
    val endedAt: Long,
    val listenedSeconds: Int,
    val qualifiedPlay: Boolean,
)

@Entity(tableName = "playback_attempts")
internal data class PlaybackAttemptEntity(
    @androidx.room.PrimaryKey val id: String,
    val sourceDeviceId: String,
    val trackId: String,
    val startedAt: Long,
    val endedAt: Long,
    val startPositionMs: Long,
    val listenedSeconds: Int,
    val endReason: String?,
)

@Entity(tableName = "daily_track_listening_stats", primaryKeys = ["sourceDeviceId", "localDate", "trackId"])
internal data class DailyTrackListeningStatEntity(val sourceDeviceId: String, val localDate: String, val trackId: String, val listenedSeconds: Int, val playCount: Int)

@Entity(tableName = "daily_playback_attempt_stats", primaryKeys = ["sourceDeviceId", "localDate"])
internal data class DailyPlaybackAttemptStatEntity(val sourceDeviceId: String, val localDate: String, val attempts: Int, val completed: Int, val skipped: Int, val stopped: Int, val listenedSeconds: Int)

internal data class AnalysisDocumentRow(val documentKey: String, val rawJson: String)

internal data class LibraryTrackRow(
    val id: String,
    val title: String,
    val artists: String,
    val album: String,
    val albumId: String = "",
    val artworkKey: String?,
    val playCount: Int,
    val createdAt: String,
    val discNumber: Int = 0,
    val trackNumber: Int = 0,
    val syncOrder: Int = 0,
    val artworkPath: String?,
    val audioPath: String?,
    val rawJson: String,
)

internal data class ArtworkAssetRow(
    val assetId: String,
    val relativePath: String,
)

@Dao
internal interface SyncDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertPlan(value: SyncPlanEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAssets(values: List<SyncAssetEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTracks(values: List<SyncTrackEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertPlaylists(values: List<SyncPlaylistEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertPlaylistMutation(value: PlaylistMutationEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertLocalPlaylist(value: LocalPlaylistEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertPlaylistArtwork(value: PlaylistArtworkStagingEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertDocuments(values: List<SyncDocumentEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertListeningSession(value: ListeningSessionEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertPlaybackAttempt(value: PlaybackAttemptEntity): Long

    @Query("UPDATE playback_attempts SET endedAt=:endedAt, listenedSeconds=:seconds, endReason=:reason WHERE id=:id AND endReason IS NULL")
    suspend fun finishPlaybackAttempt(id: String, endedAt: Long, seconds: Int, reason: String): Int

    @Query("SELECT * FROM playback_attempts WHERE endReason IS NULL")
    suspend fun openPlaybackAttempts(): List<PlaybackAttemptEntity>

    @Query("INSERT INTO daily_track_listening_stats(sourceDeviceId,localDate,trackId,listenedSeconds,playCount) VALUES(:source,:date,:trackId,:seconds,:plays) ON CONFLICT(sourceDeviceId,localDate,trackId) DO UPDATE SET listenedSeconds=listenedSeconds+:seconds, playCount=playCount+:plays")
    suspend fun addDailyTrackStat(source: String, date: String, trackId: String, seconds: Int, plays: Int)

    @Query("INSERT INTO daily_playback_attempt_stats(sourceDeviceId,localDate,attempts,completed,skipped,stopped,listenedSeconds) VALUES(:source,:date,:attempts,:completed,:skipped,:stopped,:seconds) ON CONFLICT(sourceDeviceId,localDate) DO UPDATE SET attempts=attempts+:attempts, completed=completed+:completed, skipped=skipped+:skipped, stopped=stopped+:stopped, listenedSeconds=listenedSeconds+:seconds")
    suspend fun addDailyAttemptStat(source: String, date: String, attempts: Int, completed: Int, skipped: Int, stopped: Int, seconds: Int)

    @Query("UPDATE sync_tracks SET playCount=playCount+1 WHERE trackId=:trackId AND planId IN (SELECT planId FROM sync_plans WHERE active=1)")
    suspend fun incrementActiveTrackPlayCount(trackId: String)

    @Query("SELECT * FROM listening_sessions WHERE endedAt>=:since") suspend fun listeningSessionsSince(since: Long): List<ListeningSessionEntity>
    @Query("SELECT * FROM playback_attempts WHERE endedAt>=:since AND endReason IS NOT NULL") suspend fun playbackAttemptsSince(since: Long): List<PlaybackAttemptEntity>
    @Query("SELECT * FROM daily_track_listening_stats") suspend fun dailyTrackStats(): List<DailyTrackListeningStatEntity>
    @Query("SELECT * FROM daily_playback_attempt_stats") suspend fun dailyAttemptStats(): List<DailyPlaybackAttemptStatEntity>
    @Query("SELECT * FROM daily_track_listening_stats") fun observeDailyTrackStats(): Flow<List<DailyTrackListeningStatEntity>>
    @Query("SELECT * FROM daily_playback_attempt_stats") fun observeDailyAttemptStats(): Flow<List<DailyPlaybackAttemptStatEntity>>

    @Query("DELETE FROM listening_sessions WHERE endedAt<:before") suspend fun deleteOldListeningSessions(before: Long)
    @Query("DELETE FROM playback_attempts WHERE endedAt>0 AND endedAt<:before") suspend fun deleteOldPlaybackAttempts(before: Long)

    @Query("INSERT INTO daily_track_listening_stats(sourceDeviceId,localDate,trackId,listenedSeconds,playCount) VALUES(:source,:date,:trackId,:seconds,:plays) ON CONFLICT(sourceDeviceId,localDate,trackId) DO UPDATE SET listenedSeconds=max(listenedSeconds,:seconds), playCount=max(playCount,:plays)")
    suspend fun mergeDailyTrackStat(source: String, date: String, trackId: String, seconds: Int, plays: Int)

    @Query("INSERT INTO daily_playback_attempt_stats(sourceDeviceId,localDate,attempts,completed,skipped,stopped,listenedSeconds) VALUES(:source,:date,:attempts,:completed,:skipped,:stopped,:seconds) ON CONFLICT(sourceDeviceId,localDate) DO UPDATE SET attempts=max(attempts,:attempts), completed=max(completed,:completed), skipped=max(skipped,:skipped), stopped=max(stopped,:stopped), listenedSeconds=max(listenedSeconds,:seconds)")
    suspend fun mergeDailyAttemptStat(source: String, date: String, attempts: Int, completed: Int, skipped: Int, stopped: Int, seconds: Int)

    @Query("SELECT * FROM sync_assets WHERE planId = :planId AND assetId = :assetId LIMIT 1")
    suspend fun asset(planId: String, assetId: String): SyncAssetEntity?

    @Query("SELECT * FROM sync_assets WHERE planId = :planId")
    suspend fun assets(planId: String): List<SyncAssetEntity>

    @Query("SELECT * FROM sync_assets WHERE relativePath IS NOT NULL")
    suspend fun committedAssets(): List<SyncAssetEntity>

    @Query("UPDATE sync_assets SET relativePath = :relativePath WHERE planId = :planId AND sha256 = :sha256 AND size = :size")
    suspend fun setAssetPathsByHash(planId: String, sha256: String, size: Long, relativePath: String)

    @Query("SELECT COUNT(*) FROM sync_assets WHERE planId = :planId AND relativePath IS NULL")
    suspend fun missingAssetCount(planId: String): Int

    @Query("SELECT assetId FROM sync_assets WHERE planId = :planId ORDER BY assetId")
    suspend fun assetIds(planId: String): List<String>

    @Query("SELECT relativePath FROM sync_assets WHERE planId = :planId AND relativePath IS NOT NULL")
    suspend fun assetPaths(planId: String): List<String>

    @Query("SELECT relativePath FROM sync_assets WHERE planId != :planId AND relativePath IS NOT NULL")
    suspend fun assetPathsExcept(planId: String): List<String>

    @Query("UPDATE sync_plans SET active = 0 WHERE active = 1") suspend fun deactivatePlans()
    @Query("UPDATE sync_plans SET active = 1, state = 'active' WHERE planId = :planId") suspend fun activatePlan(planId: String)
    @Query("SELECT * FROM sync_assets WHERE planId != :planId") suspend fun staleAssets(planId: String): List<SyncAssetEntity>
    @Query("DELETE FROM sync_assets WHERE planId != :planId") suspend fun deleteStaleAssets(planId: String)
    @Query("DELETE FROM sync_tracks WHERE planId != :planId") suspend fun deleteStaleTracks(planId: String)
    @Query("DELETE FROM sync_playlists WHERE planId != :planId") suspend fun deleteStalePlaylists(planId: String)
    @Query("DELETE FROM sync_documents WHERE planId != :planId") suspend fun deleteStaleDocuments(planId: String)
    @Query("DELETE FROM sync_plans WHERE planId != :planId") suspend fun deleteStalePlans(planId: String)
    @Query("DELETE FROM sync_assets WHERE planId = :planId") suspend fun deleteAssets(planId: String)
    @Query("DELETE FROM sync_tracks WHERE planId = :planId") suspend fun deleteTracks(planId: String)
    @Query("DELETE FROM sync_playlists WHERE planId = :planId") suspend fun deletePlaylists(planId: String)
    @Query("DELETE FROM sync_documents WHERE planId = :planId") suspend fun deleteDocuments(planId: String)
    @Query("DELETE FROM sync_plans WHERE planId = :planId AND active = 0") suspend fun deleteInactivePlan(planId: String)
    @Query("SELECT * FROM playlist_mutations WHERE state = 'pending' ORDER BY updatedAt, mutationId") suspend fun pendingPlaylistMutations(): List<PlaylistMutationEntity>
    @Query("SELECT * FROM playlist_mutations WHERE state = 'pending' ORDER BY updatedAt, mutationId") fun observePendingPlaylistMutations(): Flow<List<PlaylistMutationEntity>>
    @Query("UPDATE playlist_mutations SET state = 'acknowledged' WHERE mutationId IN (:mutationIds)") suspend fun acknowledgePlaylistMutations(mutationIds: List<String>)
    @Query("SELECT * FROM local_playlists ORDER BY name COLLATE NOCASE") fun observeLocalPlaylists(): Flow<List<LocalPlaylistEntity>>
    @Query("UPDATE local_playlists SET syncState = :state WHERE mutationId IN (:mutationIds)") suspend fun setLocalPlaylistSyncState(mutationIds: List<String>, state: String)
    @Query("DELETE FROM local_playlists WHERE playlistId IN (:playlistIds)") suspend fun deleteLocalPlaylists(playlistIds: List<String>)
    @Query("SELECT * FROM playlist_artwork_staging WHERE sha256 = :sha256 LIMIT 1") suspend fun playlistArtwork(sha256: String): PlaylistArtworkStagingEntity?
    @Query("SELECT * FROM playlist_artwork_staging") fun observePlaylistArtwork(): Flow<List<PlaylistArtworkStagingEntity>>
    @Query("DELETE FROM playlist_artwork_staging WHERE sha256 IN (:hashes)") suspend fun deletePlaylistArtwork(hashes: List<String>)
    @Query("""
        SELECT t.trackId AS id,
               t.title AS title,
               t.artists AS artists,
               t.album AS album,
               t.albumId AS albumId,
               t.artworkKey AS artworkKey,
               t.playCount AS playCount,
               t.createdAt AS createdAt,
               t.discNumber AS discNumber,
               t.trackNumber AS trackNumber,
               t.syncOrder AS syncOrder,
               a.relativePath AS artworkPath,
               audio.relativePath AS audioPath,
               t.rawJson AS rawJson
        FROM sync_tracks t
        INNER JOIN sync_plans p ON p.planId = t.planId
        LEFT JOIN sync_assets a ON a.planId = t.planId AND (a.assetId = t.artworkKey OR a.assetId = ('artwork:' || t.artworkKey))
        LEFT JOIN sync_assets audio ON audio.planId = t.planId AND audio.assetId = ('audio:' || t.trackId)
        WHERE p.active = 1
        ORDER BY t.syncOrder
    """)
    fun observeTracks(): Flow<List<LibraryTrackRow>>

    @Query("SELECT s.playlistId AS id, s.name AS name, s.trackIdsJson AS trackIdsJson, s.rawJson AS rawJson FROM sync_playlists s INNER JOIN sync_plans p ON p.planId = s.planId WHERE p.active = 1 ORDER BY s.name COLLATE NOCASE")
    fun observePlaylists(): Flow<List<LibraryPlaylistRow>>

    @Query("""
        SELECT a.assetId AS assetId, a.relativePath AS relativePath
        FROM sync_assets a
        INNER JOIN sync_plans p ON p.planId = a.planId
        WHERE p.active = 1 AND a.relativePath IS NOT NULL AND a.assetId LIKE 'artwork:%'
    """)
    fun observeArtworkAssets(): Flow<List<ArtworkAssetRow>>

    @Query("""
        SELECT d.rawJson
        FROM sync_documents d
        INNER JOIN sync_plans p ON p.planId = d.planId
        WHERE p.active = 1 AND d.kind = 'lyric' AND d.documentKey = :trackId
        LIMIT 1
    """)
    fun observeLyrics(trackId: String): Flow<String?>

    @Query("SELECT d.documentKey, d.rawJson FROM sync_documents d INNER JOIN sync_plans p ON p.planId = d.planId WHERE p.active = 1 AND d.kind = 'analysis'")
    suspend fun activeAnalysisDocuments(): List<AnalysisDocumentRow>
    @Query("SELECT COUNT(*) > 0 FROM sync_documents d INNER JOIN sync_plans p ON p.planId = d.planId WHERE p.active = 1 AND d.kind = 'analysis'")
    fun observeAnalysisAvailable(): Flow<Boolean>
}

@Database(
    entities = [SyncPlanEntity::class, SyncAssetEntity::class, SyncTrackEntity::class, SyncPlaylistEntity::class, PlaylistMutationEntity::class, LocalPlaylistEntity::class, PlaylistArtworkStagingEntity::class, SyncDocumentEntity::class, ListeningSessionEntity::class, PlaybackAttemptEntity::class, DailyTrackListeningStatEntity::class, DailyPlaybackAttemptStatEntity::class],
    version = 10,
    exportSchema = false,
)
internal abstract class SyncDatabase : RoomDatabase() {
    abstract fun syncDao(): SyncDao

    companion object {
        fun create(context: Context): SyncDatabase = Room.databaseBuilder(context, SyncDatabase::class.java, "library-sync.db")
            .addMigrations(Migration2To3, Migration3To4, Migration4To5, Migration5To6, Migration6To7, Migration7To8, Migration8To9, Migration9To10)
            .fallbackToDestructiveMigration()
            .build()

        private val Migration2To3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE sync_tracks ADD COLUMN discNumber INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE sync_tracks ADD COLUMN trackNumber INTEGER NOT NULL DEFAULT 0")
            }
        }
        private val Migration3To4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE sync_tracks ADD COLUMN albumId TEXT NOT NULL DEFAULT ''")
            }
        }
        private val Migration4To5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE sync_tracks ADD COLUMN syncOrder INTEGER NOT NULL DEFAULT 0")
            }
        }
        private val Migration5To6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS playlist_mutations (mutationId TEXT NOT NULL, playlistId TEXT NOT NULL, operation TEXT NOT NULL, updatedAt INTEGER NOT NULL, payloadJson TEXT NOT NULL, state TEXT NOT NULL, PRIMARY KEY(mutationId))")
            }
        }
        private val Migration6To7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS playlist_artwork_staging (sha256 TEXT NOT NULL, mime TEXT NOT NULL, size INTEGER NOT NULL, relativePath TEXT NOT NULL, PRIMARY KEY(sha256))")
            }
        }
        private val Migration7To8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS local_playlists (playlistId TEXT NOT NULL, name TEXT NOT NULL, mutationId TEXT NOT NULL, syncState TEXT NOT NULL, PRIMARY KEY(playlistId))")
            }
        }
        private val Migration8To9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE local_playlists ADD COLUMN artworkSha256 TEXT")
            }
        }
        private val Migration9To10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS listening_sessions (id TEXT NOT NULL PRIMARY KEY, sourceDeviceId TEXT NOT NULL, trackId TEXT NOT NULL, startedAt INTEGER NOT NULL, endedAt INTEGER NOT NULL, listenedSeconds INTEGER NOT NULL, qualifiedPlay INTEGER NOT NULL)")
                database.execSQL("CREATE TABLE IF NOT EXISTS playback_attempts (id TEXT NOT NULL PRIMARY KEY, sourceDeviceId TEXT NOT NULL, trackId TEXT NOT NULL, startedAt INTEGER NOT NULL, endedAt INTEGER NOT NULL, startPositionMs INTEGER NOT NULL, listenedSeconds INTEGER NOT NULL, endReason TEXT)")
                database.execSQL("CREATE TABLE IF NOT EXISTS daily_track_listening_stats (sourceDeviceId TEXT NOT NULL, localDate TEXT NOT NULL, trackId TEXT NOT NULL, listenedSeconds INTEGER NOT NULL, playCount INTEGER NOT NULL, PRIMARY KEY(sourceDeviceId,localDate,trackId))")
                database.execSQL("CREATE TABLE IF NOT EXISTS daily_playback_attempt_stats (sourceDeviceId TEXT NOT NULL, localDate TEXT NOT NULL, attempts INTEGER NOT NULL, completed INTEGER NOT NULL, skipped INTEGER NOT NULL, stopped INTEGER NOT NULL, listenedSeconds INTEGER NOT NULL, PRIMARY KEY(sourceDeviceId,localDate))")
            }
        }
    }
}

data class LibraryTrack(
    val id: String = "",
    val title: String,
    val artists: String,
    val album: String = "",
    val albumId: String = "",
    val artworkKey: String? = null,
    val playCount: Int = 0,
    val createdAt: String = "",
    val updatedAt: String = "",
    val discNumber: Int = 0,
    val trackNumber: Int = 0,
    val syncOrder: Int = 0,
    /** Canonical lossless TrackDTO JSON from the desktop manifest (desktop path excluded). */
    val metadataJson: String = "{}",
    val artworkPath: String? = null,
    val audioPath: String? = null,
    val sortTitle: String = "",
    val sortArtists: String = "",
)

internal data class LibraryPlaylistRow(val id: String, val name: String, val trackIdsJson: String, val rawJson: String)
data class LibraryPlaylist(val id: String, val name: String, val trackIds: List<String>, val metadataJson: String, val syncFailed: Boolean = false)

/** Reads non-indexed desktop metadata without requiring a Room schema change. */
fun LibraryTrack.metadataObject(): JsonObject? = runCatching {
    LibrarySyncProtocol.json.parseToJsonElement(metadataJson) as? JsonObject
}.getOrNull()

data class LibraryArtist(
    val id: String,
    val name: String,
    val createdAt: String = "",
    val artworkPath: String? = null,
    val sortName: String = "",
)

data class LibraryAlbum(
    val id: String,
    val title: String,
    val artist: String = "",
    val copyright: String = "",
    val createdAt: String = "",
    val artworkPath: String? = null,
    val year: Int = 0,
    val sortTitle: String = "",
    val sortArtist: String = "",
)

data class LibraryGenre(
    val id: String,
    val name: String,
    val createdAt: String = "",
    val sortName: String = "",
)

data class LibraryComposer(
    val id: String,
    val name: String,
    val createdAt: String = "",
    val artworkPath: String? = null,
    val sortName: String = "",
)

internal class AndroidLibrarySyncStore(
    private val database: SyncDatabase,
    private val filesDir: File,
) : LibrarySyncStore, PlaylistMutationStore, PlaylistArtworkStagingStore, ListeningSyncStore {
    private val dao = database.syncDao()
    // This store has process lifetime through AndroidSyncRuntime. Sharing avoids one
    // Room query and JSON projection per visible ViewModel.
    private val snapshotScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val activeTrackRows = dao.observeTracks()
        .shareIn(snapshotScope, SharingStarted.WhileSubscribed(5_000), replay = 1)
    private val pendingPlaylistMutations = dao.observePendingPlaylistMutations()
        .shareIn(snapshotScope, SharingStarted.WhileSubscribed(5_000), replay = 1)
    private val artworkAssets = dao.observeArtworkAssets()
        .shareIn(snapshotScope, SharingStarted.WhileSubscribed(5_000), replay = 1)
    val analysisAvailable: Flow<Boolean> = dao.observeAnalysisAvailable()
    val dailyTrackListeningStats: Flow<List<DailyTrackListeningStat>> = dao.observeDailyTrackStats().map { rows ->
        rows.map { DailyTrackListeningStat(it.sourceDeviceId, it.localDate, it.trackId, it.listenedSeconds, it.playCount) }
    }
    val dailyPlaybackAttemptStats: Flow<List<DailyPlaybackAttemptStat>> = dao.observeDailyAttemptStats().map { rows ->
        rows.map { DailyPlaybackAttemptStat(it.sourceDeviceId, it.localDate, it.attempts, it.completed, it.skipped, it.stopped, it.listenedSeconds) }
    }

    suspend fun analysis(trackId: String): TrackAnalysis? = activeAnalyses()[trackId]

    suspend fun activeAnalyses(): Map<String, TrackAnalysis> = dao.activeAnalysisDocuments().mapNotNull { document ->
        val value = runCatching { LibrarySyncProtocol.json.parseToJsonElement(document.rawJson).jsonObject }.getOrNull() ?: return@mapNotNull null
        val lufs = value["loudness_lufs"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull() ?: return@mapNotNull null
        val peak = value["true_peak"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull() ?: return@mapNotNull null
        document.documentKey to TrackAnalysis(lufs, peak)
    }.toMap()
    val tracks: Flow<List<LibraryTrack>> = combine(activeTrackRows, pendingPlaylistMutations) { rows, pending ->
        val overrides = pending.mapNotNull { row ->
            runCatching { LibrarySyncProtocol.json.decodeFromString(PlaylistMutationPayload.serializer(), row.payloadJson) }
                .getOrNull()?.takeIf { row.operation == PlaylistMutationOperation.SET_FAVORITE.name }
                ?.let { it.trackId to it.isFavorite }
        }.toMap()
        rows.map { row ->
            val metadata = row.metadataObject()
            LibraryTrack(
                id = row.id,
                title = row.title,
                sortTitle = metadata?.string("sort_title").orEmpty(),
                artists = row.artists,
                sortArtists = metadata?.arraySortNames("artists").orEmpty(),
                album = row.album,
                albumId = row.albumId,
                artworkKey = row.artworkKey,
                playCount = row.playCount,
                createdAt = row.createdAt,
                updatedAt = metadata?.string("updated_at").orEmpty(),
                discNumber = row.discNumber,
                trackNumber = row.trackNumber,
                syncOrder = row.syncOrder,
                metadataJson = overrides[row.id]?.let { favorite ->
                    val root = runCatching { LibrarySyncProtocol.json.parseToJsonElement(row.rawJson).jsonObject }.getOrDefault(JsonObject(emptyMap()))
                    JsonObject(root + ("is_favorite" to JsonPrimitive(favorite))).toString()
                } ?: row.rawJson,
                artworkPath = row.artworkPath,
                audioPath = row.audioPath,
            )
        }
    }.shareIn(snapshotScope, SharingStarted.WhileSubscribed(5_000), replay = 1)
    val artists: Flow<List<LibraryArtist>> = combine(
        activeTrackRows,
        artworkAssets,
    ) { rows, artworkAssets ->
        libraryArtistsFrom(rows, artworkAssets.associate { asset ->
            asset.assetId.removePrefix("artwork:") to asset.relativePath
        })
    }.shareIn(snapshotScope, SharingStarted.WhileSubscribed(5_000), replay = 1)
    val albums: Flow<List<LibraryAlbum>> = combine(
        activeTrackRows,
        artworkAssets,
    ) { rows, artworkAssets ->
        libraryAlbumsFrom(rows, artworkAssets.associate { asset ->
            asset.assetId.removePrefix("artwork:") to asset.relativePath
        })
    }.shareIn(snapshotScope, SharingStarted.WhileSubscribed(5_000), replay = 1)
    val genres: Flow<List<LibraryGenre>> = activeTrackRows.map { rows ->
        libraryGenresFrom(rows)
    }.shareIn(snapshotScope, SharingStarted.WhileSubscribed(5_000), replay = 1)
    val composers: Flow<List<LibraryComposer>> = combine(
        activeTrackRows,
        artworkAssets,
    ) { rows, artworkAssets ->
        libraryComposersFrom(rows, artworkAssets.associate { asset ->
            asset.assetId.removePrefix("artwork:") to asset.relativePath
        })
    }.shareIn(snapshotScope, SharingStarted.WhileSubscribed(5_000), replay = 1)
    val playlists: Flow<List<LibraryPlaylist>> = combine(
        dao.observePlaylists(),
        dao.observeLocalPlaylists(),
        pendingPlaylistMutations,
    ) { rows, local, pending ->
        val synced = rows.map { row ->
            LibraryPlaylist(
                row.id,
                row.name,
                runCatching {
                    (LibrarySyncProtocol.json.parseToJsonElement(row.trackIdsJson) as? JsonArray)
                        .orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                }.getOrDefault(emptyList()),
                row.rawJson,
            )
        }
        val syncedIds = synced.mapTo(mutableSetOf(), LibraryPlaylist::id)
        val projected = synced + local.filter { it.playlistId !in syncedIds }.map { row ->
            val metadata = row.artworkSha256?.let { hash -> "{\"playlist\":{\"artwork_key\":\"$hash\"}}" } ?: "{}"
            LibraryPlaylist(row.playlistId, row.name, emptyList(), metadata, syncFailed = row.syncState == "failed")
        }
        applyPendingPlaylistMutations(projected, pending.mapNotNull(PlaylistMutationEntity::toPlaylistMutation))
            .sortedBy { it.name.lowercase() }
    }.shareIn(snapshotScope, SharingStarted.WhileSubscribed(5_000), replay = 1)
    val artworkPaths: Flow<Map<String, String>> = combine(artworkAssets, dao.observePlaylistArtwork()) { assets, staged ->
        (assets.map { asset -> asset.assetId.removePrefix("artwork:") to asset.relativePath } + staged.map { artwork -> artwork.sha256 to artwork.relativePath }).toMap()
    }.shareIn(snapshotScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    /** Durable boundary for playlist mutations; list browsing remains read-only for now. */
    suspend fun queuePlaylistMutation(mutation: PlaylistMutation) {
        require(mutation.validationError() == null) { mutation.validationError() ?: "Invalid playlist mutation" }
        dao.insertPlaylistMutation(
            PlaylistMutationEntity(
                mutation.mutationId,
                mutation.playlistId,
                mutation.operation.name,
                mutation.updatedAt,
                LibrarySyncProtocol.json.encodeToString(PlaylistMutationPayload.serializer(), mutation.payload),
            ),
        )
    }

    /** Applies the desired favorite state optimistically; the durable delta is reconciled on the next desktop Sync. */
    suspend fun setFavorite(trackId: String, favorite: Boolean) {
        val mutation = PlaylistMutation(
            mutationId = UUID.randomUUID().toString(),
            playlistId = "favorites",
            operation = PlaylistMutationOperation.SET_FAVORITE,
            updatedAt = System.currentTimeMillis(),
            payload = PlaylistMutationPayload(trackId = trackId, isFavorite = favorite),
        )
        queuePlaylistMutation(mutation)
    }

    suspend fun createLocalPlaylist(
        mutation: PlaylistMutation,
        artwork: StagedPlaylistArtwork? = null,
        artworkMutationId: String? = null,
        initialTrackIds: List<String> = emptyList(),
    ) {
        require(mutation.operation == PlaylistMutationOperation.CREATE) { "Expected playlist create mutation" }
        require(mutation.validationError() == null) { mutation.validationError() ?: "Invalid playlist mutation" }
        database.withTransaction {
            dao.insertPlaylistMutation(PlaylistMutationEntity(mutation.mutationId, mutation.playlistId, mutation.operation.name, mutation.updatedAt, LibrarySyncProtocol.json.encodeToString(PlaylistMutationPayload.serializer(), mutation.payload)))
            if (artwork != null) {
                require(!artworkMutationId.isNullOrBlank()) { "Artwork mutation ID is required" }
                dao.insertPlaylistArtwork(PlaylistArtworkStagingEntity(artwork.sha256, artwork.mime, artwork.size, artwork.relativePath))
                val artworkMutation = PlaylistMutation(
                    mutationId = artworkMutationId,
                    playlistId = mutation.playlistId,
                    operation = me.misa198.airmedy.sync.PlaylistMutationOperation.SET_ARTWORK,
                    // Desktop applies mutations with a per-playlist LWW watermark.
                    // CREATE must precede SET_ARTWORK even when UUID order differs.
                    updatedAt = mutation.updatedAt + 1,
                    payload = PlaylistMutationPayload(artworkSha256 = artwork.sha256),
                )
                dao.insertPlaylistMutation(PlaylistMutationEntity(artworkMutation.mutationId, artworkMutation.playlistId, artworkMutation.operation.name, artworkMutation.updatedAt, LibrarySyncProtocol.json.encodeToString(PlaylistMutationPayload.serializer(), artworkMutation.payload)))
            }
            dao.insertLocalPlaylist(LocalPlaylistEntity(mutation.playlistId, mutation.payload.name!!.trim(), mutation.mutationId, artworkSha256 = artwork?.sha256))
            initialTrackIds.distinct().filter(String::isNotBlank).forEachIndexed { index, trackId ->
                val addMutation = PlaylistMutation(
                    mutationId = UUID.randomUUID().toString(),
                    playlistId = mutation.playlistId,
                    operation = PlaylistMutationOperation.ADD_TRACK,
                    updatedAt = mutation.updatedAt + 2 + index,
                    payload = PlaylistMutationPayload(trackId = trackId),
                )
                dao.insertPlaylistMutation(PlaylistMutationEntity(addMutation.mutationId, addMutation.playlistId, addMutation.operation.name, addMutation.updatedAt, LibrarySyncProtocol.json.encodeToString(PlaylistMutationPayload.serializer(), addMutation.payload)))
            }
        }
    }

    suspend fun markLocalPlaylistMutationsFailed(ids: List<String>) {
        if (ids.isNotEmpty()) dao.setLocalPlaylistSyncState(ids, "failed")
    }

    suspend fun stagePlaylistArtwork(value: StagedPlaylistArtwork) {
        require(value.sha256.matches(Regex("^[0-9a-f]{64}$")) && value.mime in setOf("image/jpeg", "image/png", "image/webp"))
        require(!value.relativePath.startsWith('/') && ".." !in value.relativePath.split('/'))
        dao.insertPlaylistArtwork(PlaylistArtworkStagingEntity(value.sha256, value.mime, value.size, value.relativePath))
    }

    override suspend fun stagedPlaylistArtwork(sha256: String): StagedPlaylistArtwork? = dao.playlistArtwork(sha256)?.let { StagedPlaylistArtwork(it.sha256, it.mime, it.size, it.relativePath) }

    override suspend fun pendingPlaylistMutations(): List<PlaylistMutation> = dao.pendingPlaylistMutations().mapNotNull(PlaylistMutationEntity::toPlaylistMutation)

    override suspend fun acknowledgePlaylistMutations(ids: List<String>) {
        if (ids.isEmpty()) return
        // Keep staged artwork visible until the replacement manifest is active.
        dao.acknowledgePlaylistMutations(ids)
    }

    fun lyrics(trackId: String): Flow<String?> = dao.observeLyrics(trackId).map { rawJson ->
        rawJson?.let { value ->
            runCatching {
                LibrarySyncProtocol.json.parseToJsonElement(value).jsonObject["content"]?.jsonPrimitive?.contentOrNull
            }.getOrNull()
        }
    }

    override suspend fun prepare(request: LibrarySyncRequest, manifest: LibrarySyncManifest) {
        database.withTransaction {
            val cachedPaths = buildMap {
                dao.committedAssets().forEach { asset ->
                    cachedAssetPath(filesDir, asset)?.let { put(asset.sha256 to asset.size, it) }
                }
            }
            dao.insertPlan(SyncPlanEntity(request.planId, request.desktopId, LibrarySyncProtocol.json.encodeToString(LibrarySyncManifest.serializer(), manifest), "staging", false))
            dao.insertAssets(manifest.assets.orEmpty().map { asset ->
                val cachedPath = cachedPaths[asset.sha256 to asset.size]
                SyncAssetEntity(request.planId, asset.id, asset.kind, asset.sha256, asset.size, cachedPath)
            })
            dao.insertTracks(manifest.tracks.orEmpty().mapIndexedNotNull { index, track -> track.toTrack(request.planId, index) })
            val pending = dao.pendingPlaylistMutations().mapNotNull { row -> runCatching {
                PlaylistMutation(row.mutationId, row.playlistId, PlaylistMutationOperation.valueOf(row.operation), row.updatedAt,
                    LibrarySyncProtocol.json.decodeFromString(PlaylistMutationPayload.serializer(), row.payloadJson))
            }.getOrNull() }
            val authoritativeIds = manifest.playlists.orEmpty().mapNotNull { item ->
                (item["playlist"] as? JsonObject)?.string("id")
            }
            if (authoritativeIds.isNotEmpty()) dao.deleteLocalPlaylists(authoritativeIds)
            dao.insertPlaylists(mergePlaylistSnapshot(manifest.playlists.orEmpty(), manifest.scope, pending).mapNotNull { it.toPlaylist(request.planId) })
            dao.insertDocuments(manifest.lyrics.entries.map { SyncDocumentEntity(request.planId, "lyric", it.key, it.value.toString()) })
            dao.insertDocuments(manifest.analysis.entries.map { SyncDocumentEntity(request.planId, "analysis", it.key, it.value.toString()) })
        }
    }

    override suspend fun isAssetCommitted(planId: String, asset: LibrarySyncAsset): Boolean =
        dao.asset(planId, asset.id)?.let {
            it.sha256 == asset.sha256 && it.size == asset.size && cachedAssetPath(filesDir, it) != null
        } == true

    override suspend fun cachedAssetContents(): Set<LibrarySyncAssetContent> = dao.committedAssets().mapNotNull { asset ->
        cachedAssetPath(filesDir, asset)?.let { LibrarySyncAssetContent(asset.sha256, asset.size) }
    }.toSet()

    override suspend fun stageAsset(planId: String, asset: LibrarySyncAsset, pulled: PulledAsset) {
        require(pulled.sha256.equals(asset.sha256, ignoreCase = true) && pulled.size == asset.size) { "Invalid downloaded asset" }
        require(!pulled.relativePath.startsWith('/') && ".." !in pulled.relativePath.split('/')) { "Invalid asset path" }
        require(File(filesDir, pulled.relativePath).isFile) { "Downloaded asset is missing" }
        dao.setAssetPathsByHash(planId, asset.sha256, asset.size, pulled.relativePath)
    }

    override suspend fun activate(planId: String): List<String> = database.withTransaction {
        check(dao.missingAssetCount(planId) == 0) { "Plan has missing assets" }
        dao.deactivatePlans()
        dao.activatePlan(planId)
        dao.assetIds(planId)
    }

    override suspend fun finalize(planId: String) {
        val (stale, activePaths) = database.withTransaction {
            val active = dao.assetPaths(planId).toSet()
            dao.staleAssets(planId).also {
                dao.deleteStaleAssets(planId)
                dao.deleteStaleTracks(planId)
                dao.deleteStalePlaylists(planId)
                dao.deleteStaleDocuments(planId)
                dao.deleteStalePlans(planId)
            } to active
        }
        stale.forEach { asset ->
            asset.relativePath
                ?.takeUnless { it in activePaths }
                ?.let { File(filesDir, it).delete() }
        }
        cleanupAcknowledgedPlaylistArtwork()
    }

    override suspend fun discard(planId: String) {
        val (assets, referencedPaths) = database.withTransaction {
            val planAssets = dao.assets(planId)
            val otherPaths = dao.assetPathsExcept(planId).toSet()
            dao.deleteAssets(planId)
            dao.deleteTracks(planId)
            dao.deletePlaylists(planId)
            dao.deleteDocuments(planId)
            dao.deleteInactivePlan(planId)
            planAssets to otherPaths
        }
        assets.forEach { asset ->
            asset.relativePath
                ?.takeUnless { it in referencedPaths }
                ?.let { File(filesDir, it).delete() }
        }
    }

    suspend fun recordListening(write: ListeningWrite) = database.withTransaction {
        when (write) {
            is ListeningWrite.Session -> {
                val value = write.value
                if (dao.insertListeningSession(value.toEntity()) != -1L) {
                    splitListeningByDate(value.startedAt, value.endedAt, value.listenedSeconds).forEach { (date, seconds) ->
                        dao.addDailyTrackStat(value.sourceDeviceId, date, value.trackId, seconds, 0)
                    }
                }
            }
            is ListeningWrite.AttemptStarted -> {
                val value = write.value
                if (dao.insertPlaybackAttempt(value.toEntity()) != -1L) {
                    dao.addDailyAttemptStat(value.sourceDeviceId, localDate(value.startedAt), 1, 0, 0, 0, 0)
                }
            }
            is ListeningWrite.AttemptFinished -> {
                val value = write.value
                if (dao.finishPlaybackAttempt(value.id, value.endedAt, value.listenedSeconds, value.endReason!!.name.lowercase()) > 0) {
                    val completed = if (value.endReason == PlaybackEndReason.COMPLETED) 1 else 0
                    val skipped = if (value.endReason == PlaybackEndReason.SKIPPED) 1 else 0
                    val stopped = if (value.endReason == PlaybackEndReason.STOPPED) 1 else 0
                    dao.addDailyAttemptStat(value.sourceDeviceId, localDate(value.startedAt), 0, completed, skipped, stopped, value.listenedSeconds)
                }
            }
            is ListeningWrite.QualifiedPlay -> {
                dao.addDailyTrackStat(write.sourceDeviceId, localDate(write.occurredAt), write.trackId, 0, 1)
                dao.incrementActiveTrackPlayCount(write.trackId)
            }
        }
    }

    suspend fun recoverOpenPlaybackAttempts(nowMs: Long) {
        dao.openPlaybackAttempts().forEach { row ->
            recordListening(ListeningWrite.AttemptFinished(row.toModel().copy(endedAt = nowMs, endReason = PlaybackEndReason.STOPPED)))
        }
    }

    suspend fun cleanupListening(beforeMs: Long) {
        dao.deleteOldListeningSessions(beforeMs)
        dao.deleteOldPlaybackAttempts(beforeMs)
    }

    override suspend fun listeningSnapshot(reconciliationId: String, sinceMs: Long) = ListeningSyncSnapshot(
        reconciliationId = reconciliationId,
        sessions = dao.listeningSessionsSince(sinceMs).map { ListeningSession(it.id, it.sourceDeviceId, it.trackId, it.startedAt, it.endedAt, it.listenedSeconds, it.qualifiedPlay) },
        attempts = dao.playbackAttemptsSince(sinceMs).map(PlaybackAttemptEntity::toModel),
        dailyTracks = dao.dailyTrackStats().map { DailyTrackListeningStat(it.sourceDeviceId, it.localDate, it.trackId, it.listenedSeconds, it.playCount) },
        dailyAttempts = dao.dailyAttemptStats().map { DailyPlaybackAttemptStat(it.sourceDeviceId, it.localDate, it.attempts, it.completed, it.skipped, it.stopped, it.listenedSeconds) },
    )

    override suspend fun mergeListeningSnapshot(snapshot: ListeningSyncSnapshot) = database.withTransaction {
        snapshot.sessions.forEach { dao.insertListeningSession(it.toEntity()) }
        snapshot.attempts.filter { it.endReason != null }.forEach { dao.insertPlaybackAttempt(it.toEntity()) }
        snapshot.dailyTracks.forEach { dao.mergeDailyTrackStat(it.sourceDeviceId, it.localDate, it.trackId, it.listenedSeconds, it.playCount) }
        snapshot.dailyAttempts.forEach { dao.mergeDailyAttemptStat(it.sourceDeviceId, it.localDate, it.attempts, it.completed, it.skipped, it.stopped, it.listenedSeconds) }
    }

    suspend fun clearAll() {
        val assets = database.withTransaction {
            dao.staleAssets("__never_matches__").also {
                dao.deleteStaleAssets("__never_matches__"); dao.deleteStaleTracks("__never_matches__"); dao.deleteStalePlaylists("__never_matches__"); dao.deleteStaleDocuments("__never_matches__"); dao.deleteStalePlans("__never_matches__")
            }
        }
        assets.forEach { it.relativePath?.let { path -> File(filesDir, path).delete() } }
    }

    private suspend fun cleanupAcknowledgedPlaylistArtwork() {
        val staged = dao.observePlaylistArtwork().first()
        val unused = unusedPlaylistArtworkHashes(
            staged.map(PlaylistArtworkStagingEntity::sha256),
            pendingPlaylistMutations(),
            dao.observeLocalPlaylists().first().mapNotNull(LocalPlaylistEntity::artworkSha256),
        )
        if (unused.isEmpty()) return
        val paths = staged.filter { it.sha256 in unused }.map(PlaylistArtworkStagingEntity::relativePath)
        dao.deletePlaylistArtwork(unused)
        paths.forEach { File(filesDir, it).delete() }
    }

    private fun JsonObject.toTrack(planId: String, syncOrder: Int): SyncTrackEntity? {
        val id = string("id") ?: return null
        val playCount = (this["play_count"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0
        val createdAt = (this["created_at"] as? JsonPrimitive)?.contentOrNull
            ?: (this["mtime"] as? JsonPrimitive)?.contentOrNull
            ?: ""
        return SyncTrackEntity(
            planId = planId,
            trackId = id,
            title = string("title") ?: "",
            artists = arrayNames("artists"),
            album = (this["album"] as? JsonObject)?.string("title") ?: "",
            albumId = (this["album"] as? JsonObject)?.string("id") ?: "",
            artworkKey = string("artwork_key"),
            playCount = playCount,
            createdAt = createdAt,
            discNumber = int("disc_number"),
            trackNumber = int("track_number"),
            syncOrder = syncOrder,
            rawJson = toString(),
        )
    }

    private fun JsonObject.toPlaylist(planId: String): SyncPlaylistEntity? {
        val playlist = this["playlist"] as? JsonObject ?: return null
        val id = playlist.string("id") ?: return null
        return SyncPlaylistEntity(planId, id, playlist.string("name") ?: "", (this["track_ids"] as? JsonArray)?.toString() ?: "[]", toString())
    }

    private fun JsonObject.arrayNames(name: String): String = ((this[name] as? JsonArray).orEmpty()).mapNotNull { (it as? JsonObject)?.string("name") }.joinToString(", ")

    private fun JsonObject.int(name: String): Int = (this[name] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0
}

private fun ListeningSession.toEntity() = ListeningSessionEntity(id, sourceDeviceId, trackId, startedAt, endedAt, listenedSeconds, qualifiedPlay)
private fun PlaybackAttempt.toEntity() = PlaybackAttemptEntity(id, sourceDeviceId, trackId, startedAt, endedAt, startPositionMs, listenedSeconds, endReason?.name?.lowercase())
private fun PlaybackAttemptEntity.toModel() = PlaybackAttempt(id, sourceDeviceId, trackId, startedAt, endedAt, startPositionMs, listenedSeconds, endReason?.let { PlaybackEndReason.valueOf(it.uppercase()) })

private fun localDate(epochMs: Long): String = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate().toString()

internal fun splitListeningByDate(startedAt: Long, endedAt: Long, seconds: Int): Map<String, Int> {
    if (seconds <= 0 || endedAt <= startedAt) return mapOf(localDate(startedAt) to seconds.coerceAtLeast(0))
    val zone = ZoneId.systemDefault()
    val result = linkedMapOf<String, Int>()
    var cursor = Instant.ofEpochMilli(startedAt).atZone(zone)
    val end = Instant.ofEpochMilli(endedAt).atZone(zone)
    var remaining = seconds
    val wallMs = endedAt - startedAt
    while (cursor.isBefore(end)) {
        val next = minOf(cursor.toLocalDate().plusDays(1).atStartOfDay(zone), end)
        val part = if (next == end) remaining else (((next.toInstant().toEpochMilli() - cursor.toInstant().toEpochMilli()).toDouble() / wallMs) * seconds).toInt()
        if (part > 0) result[cursor.toLocalDate().toString()] = (result[cursor.toLocalDate().toString()] ?: 0) + part
        remaining -= part
        cursor = next
    }
    return result.ifEmpty { mapOf(localDate(startedAt) to seconds) }
}

/** Pending deltas overlay the incoming snapshot until desktop sends a terminal acknowledgement. */
internal fun mergePlaylistSnapshot(snapshot: List<JsonObject>, scope: JsonObject, pending: List<PlaylistMutation>): List<JsonObject> {
    val allowed: (String) -> Boolean = when (scope.string("kind")) {
        "all" -> { _: String -> true }
        "playlists" -> {
            val ids = (scope["selected_ids"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.toSet()
            ({ id: String -> id in ids })
        }
        else -> { _: String -> false }
    }
    val playlists = linkedMapOf<String, JsonObject>()
    snapshot.forEach { item -> (item["playlist"] as? JsonObject)?.string("id")?.let { playlists[it] = item } }
    pending.filter { allowed(it.playlistId) }.forEach { mutation ->
        val current = playlists[mutation.playlistId]
        when (mutation.operation) {
            PlaylistMutationOperation.DELETE -> playlists.remove(mutation.playlistId)
            PlaylistMutationOperation.CREATE -> if (current == null) playlists[mutation.playlistId] = playlistSnapshot(mutation, emptyList())
            PlaylistMutationOperation.UPDATE -> if (current != null) playlists[mutation.playlistId] = playlistSnapshot(mutation, trackIds(current), current)
            PlaylistMutationOperation.ADD_TRACK, PlaylistMutationOperation.REMOVE_TRACK, PlaylistMutationOperation.MOVE_TRACK -> if (current != null) {
                val ids = trackIds(current).toMutableList(); val track = mutation.payload.trackId ?: return@forEach
                when (mutation.operation) {
                    PlaylistMutationOperation.ADD_TRACK -> if (track !in ids) ids.add(track)
                    PlaylistMutationOperation.REMOVE_TRACK -> ids.remove(track)
                    PlaylistMutationOperation.MOVE_TRACK -> {
                        ids.remove(track)
                        val previous = mutation.payload.previousTrackId
                        val next = mutation.payload.nextTrackId
                        val index = next?.let(ids::indexOf)?.takeIf { it >= 0 }
                            ?: previous?.let(ids::indexOf)?.takeIf { it >= 0 }?.plus(1)
                            ?: ids.size
                        ids.add(index.coerceIn(0, ids.size), track)
                    }
                    else -> Unit
                }
                playlists[mutation.playlistId] = playlistSnapshot(mutation, ids, current)
            }
            else -> Unit
        }
    }
    return playlists.values.toList()
}

private fun trackIds(value: JsonObject): List<String> = ((value["track_ids"] as? JsonArray).orEmpty()).mapNotNull { (it as? JsonPrimitive)?.contentOrNull }

private fun PlaylistMutationEntity.toPlaylistMutation(): PlaylistMutation? = runCatching {
    PlaylistMutation(
        mutationId,
        playlistId,
        PlaylistMutationOperation.valueOf(operation),
        updatedAt,
        LibrarySyncProtocol.json.decodeFromString(PlaylistMutationPayload.serializer(), payloadJson),
    )
}.getOrNull()

/** Reflects pending playlist deltas locally until desktop acknowledges them. */
internal fun applyPendingPlaylistMutations(
    playlists: List<LibraryPlaylist>,
    pending: List<PlaylistMutation>,
): List<LibraryPlaylist> {
    val projected = playlists.associateByTo(linkedMapOf(), LibraryPlaylist::id)
    pending.forEach { mutation ->
        val current = projected[mutation.playlistId] ?: return@forEach
        when (mutation.operation) {
            PlaylistMutationOperation.DELETE -> projected.remove(mutation.playlistId)
            PlaylistMutationOperation.UPDATE -> projected[mutation.playlistId] = current.copy(
                name = mutation.payload.name?.trim()?.takeIf(String::isNotBlank) ?: current.name,
            )
            PlaylistMutationOperation.SET_ARTWORK -> mutation.payload.artworkSha256?.let { key ->
                projected[mutation.playlistId] = current.copy(metadataJson = current.withPlaylistArtworkKey(key))
            }
            PlaylistMutationOperation.REMOVE_ARTWORK -> projected[mutation.playlistId] = current.copy(
                metadataJson = current.withPlaylistArtworkKey(null),
            )
            PlaylistMutationOperation.ADD_TRACK -> projected[mutation.playlistId] = current.copy(
                trackIds = (current.trackIds + (mutation.payload.trackId ?: return@forEach)).distinct(),
            )
            PlaylistMutationOperation.REMOVE_TRACK -> projected[mutation.playlistId] = current.copy(
                trackIds = current.trackIds.filterNot { it == mutation.payload.trackId ?: return@forEach },
            )
            PlaylistMutationOperation.MOVE_TRACK -> {
                val trackId = mutation.payload.trackId ?: return@forEach
                val ids = current.trackIds.filterNot { it == trackId }.toMutableList()
                val index = mutation.payload.nextTrackId?.let(ids::indexOf)?.takeIf { it >= 0 }
                    ?: mutation.payload.previousTrackId?.let(ids::indexOf)?.takeIf { it >= 0 }?.plus(1)
                    ?: ids.size
                ids.add(index.coerceIn(0, ids.size), trackId)
                projected[mutation.playlistId] = current.copy(trackIds = ids)
            }
            else -> Unit
        }
    }
    return projected.values.toList()
}

internal fun unusedPlaylistArtworkHashes(staged: List<String>, pending: List<PlaylistMutation>, localArtwork: List<String>): List<String> {
    val referenced = pending.mapNotNull { it.payload.artworkSha256 }.toSet() + localArtwork
    return staged.filter { it !in referenced }
}

private fun LibraryPlaylist.withPlaylistArtworkKey(key: String?): String {
    val root = runCatching { LibrarySyncProtocol.json.parseToJsonElement(metadataJson) as? JsonObject }.getOrNull()
        ?: JsonObject(emptyMap())
    val container = (root["playlist"] as? JsonObject)?.toMutableMap() ?: linkedMapOf()
    if (key == null) container.remove("artwork_key") else container["artwork_key"] = JsonPrimitive(key)
    return LibrarySyncProtocol.json.encodeToString(JsonObject(root.toMutableMap().apply { put("playlist", JsonObject(container)) }))
}

private fun playlistSnapshot(mutation: PlaylistMutation, tracks: List<String>, current: JsonObject? = null): JsonObject {
    val playlist = ((current?.get("playlist") as? JsonObject)?.toMutableMap() ?: linkedMapOf()).apply {
        put("id", JsonPrimitive(mutation.playlistId))
        mutation.payload.name?.let { put("name", JsonPrimitive(it)) }
        mutation.payload.description?.let { put("description", JsonPrimitive(it)) }
    }
    return JsonObject(linkedMapOf("playlist" to JsonObject(playlist), "track_ids" to JsonArray(tracks.map(::JsonPrimitive))))
}

internal fun libraryArtistsFrom(
    tracks: List<LibraryTrackRow>,
    artworkPaths: Map<String, String>,
): List<LibraryArtist> {
    val artists = linkedMapOf<String, LibraryArtist>()
    tracks.forEach { track ->
        val root = runCatching { LibrarySyncProtocol.json.parseToJsonElement(track.rawJson) as? JsonObject }.getOrNull()
            ?: return@forEach
        ((root["artists"] as? JsonArray).orEmpty()).forEach { value ->
            val artist = value as? JsonObject ?: return@forEach
            val id = artist.string("id")?.takeIf(String::isNotBlank) ?: return@forEach
            val name = artist.string("name")?.trim().orEmpty().takeIf(String::isNotEmpty) ?: return@forEach
            val artworkKey = artist.string("artwork_key")?.takeIf(String::isNotBlank)
                ?: listOf("artwork_key_manual", "artwork_key_local", "artwork_key_online")
                    .firstNotNullOfOrNull { key -> artist.string(key)?.takeIf(String::isNotBlank) }
            val candidate = LibraryArtist(
                id = id,
                name = name,
                sortName = artist.string("sort_name").orEmpty(),
                createdAt = artist.string("created_at").orEmpty(),
                artworkPath = artworkKey?.let(artworkPaths::get),
            )
            artists[id] = artists[id]?.let { existing ->
                existing.copy(
                    sortName = existing.sortName.ifBlank { candidate.sortName },
                    artworkPath = existing.artworkPath ?: candidate.artworkPath,
                    createdAt = listOf(existing.createdAt, candidate.createdAt)
                        .filter(String::isNotBlank)
                        .minOrNull().orEmpty(),
                )
            } ?: candidate
        }
    }
    return artists.values.toList()
}

internal fun libraryAlbumsFrom(
    tracks: List<LibraryTrackRow>,
    artworkPaths: Map<String, String>,
): List<LibraryAlbum> {
    val albums = linkedMapOf<String, LibraryAlbum>()
    tracks.forEach { track ->
        val root = runCatching { LibrarySyncProtocol.json.parseToJsonElement(track.rawJson) as? JsonObject }.getOrNull()
            ?: return@forEach
        val album = root["album"] as? JsonObject ?: return@forEach
        val id = album.string("id")?.takeIf(String::isNotBlank) ?: return@forEach
        val title = album.string("title")?.trim().orEmpty().takeIf(String::isNotEmpty) ?: return@forEach
        val candidate = LibraryAlbum(
            id = id,
            title = title,
            sortTitle = album.string("sort_title").orEmpty(),
            artist = root.arrayNames("album_artists").ifBlank { root.arrayNames("artists") },
            copyright = album.string("copyright").orEmpty(),
            sortArtist = root.arraySortNames("album_artists").ifBlank { root.arraySortNames("artists") },
            createdAt = album.string("created_at").orEmpty().ifBlank { track.createdAt },
            artworkPath = album.string("artwork_key")?.takeIf(String::isNotBlank)?.let(artworkPaths::get),
            year = album.int("year"),
        )
        albums[id] = albums[id]?.let { existing ->
            existing.copy(
                sortTitle = existing.sortTitle.ifBlank { candidate.sortTitle },
                artist = existing.artist.ifBlank { candidate.artist },
                copyright = existing.copyright.ifBlank { candidate.copyright },
                sortArtist = existing.sortArtist.ifBlank { candidate.sortArtist },
                artworkPath = existing.artworkPath ?: candidate.artworkPath,
                createdAt = listOf(existing.createdAt, candidate.createdAt)
                    .filter(String::isNotBlank)
                    .minOrNull()
                    .orEmpty(),
                year = existing.year.takeIf { it > 0 } ?: candidate.year,
            )
        } ?: candidate
    }
    return albums.values.toList()
}

internal fun libraryGenresFrom(
    tracks: List<LibraryTrackRow>,
): List<LibraryGenre> {
    val genres = linkedMapOf<String, LibraryGenre>()
    tracks.forEach { track ->
        val root = runCatching { LibrarySyncProtocol.json.parseToJsonElement(track.rawJson) as? JsonObject }.getOrNull()
            ?: return@forEach

        fun addGenre(id: String, rawName: String, sortName: String, createdAt: String) {
            val name = rawName.trim()
            if (id.isBlank() || name.isBlank()) return
            val candidate = LibraryGenre(
                id = id,
                name = name,
                sortName = sortName,
                createdAt = createdAt,
            )
            genres[id] = genres[id]?.let { existing ->
                existing.copy(
                    sortName = existing.sortName.ifBlank { candidate.sortName },
                    createdAt = listOf(existing.createdAt, candidate.createdAt)
                        .filter(String::isNotBlank)
                        .minOrNull()
                        .orEmpty(),
                )
            } ?: candidate
        }

        (root["genres"] as? JsonArray).orEmpty().forEach { value ->
            val genre = value as? JsonObject ?: return@forEach
            addGenre(
                genre.string("id").orEmpty(),
                genre.string("name") ?: genre.string("title") ?: "",
                genre.string("normalization_key").orEmpty(),
                genre.string("created_at").orEmpty().ifBlank { track.createdAt },
            )
        }
    }
    return genres.values.toList()
}

internal fun libraryComposersFrom(
    tracks: List<LibraryTrackRow>,
    artworkPaths: Map<String, String>,
): List<LibraryComposer> {
    val composers = linkedMapOf<String, LibraryComposer>()
    val composerIdsByName = mutableMapOf<String, String>()
    tracks.forEach { track ->
        val root = runCatching { LibrarySyncProtocol.json.parseToJsonElement(track.rawJson) as? JsonObject }.getOrNull()
            ?: return@forEach

        fun addComposer(rawId: String, rawName: String, sortName: String, artworkKey: String?, createdAt: String) {
            val name = rawName.trim()
            if (rawId.isBlank() || name.isBlank()) return
            val canonicalName = name.lowercase(Locale.ROOT)
            val previousId = composerIdsByName[canonicalName]
            val id = rawId
            val candidate = LibraryComposer(
                id = id,
                name = name,
                sortName = sortName,
                createdAt = createdAt,
                artworkPath = artworkKey?.let(artworkPaths::get),
            )
            val existing = when {
                previousId == id -> composers[id]
                previousId != null -> composers.remove(previousId)
                else -> composers.remove(id)
            }
            composers[id] = existing?.let { existing ->
                existing.copy(
                    sortName = existing.sortName.ifBlank { candidate.sortName },
                    artworkPath = existing.artworkPath ?: candidate.artworkPath,
                    createdAt = listOf(existing.createdAt, candidate.createdAt)
                        .filter(String::isNotBlank)
                        .minOrNull()
                        .orEmpty(),
                )
            } ?: candidate
            composerIdsByName[canonicalName] = id
        }

        (root["composers"] as? JsonArray).orEmpty().forEach { value ->
            val composer = value as? JsonObject ?: return@forEach
            val artworkKey = composer.string("artwork_key")?.takeIf(String::isNotBlank)
                ?: listOf("artwork_key_manual", "artwork_key_local", "artwork_key_online")
                    .firstNotNullOfOrNull { key -> composer.string(key)?.takeIf(String::isNotBlank) }
            addComposer(
                composer.string("id").orEmpty(),
                composer.string("name") ?: composer.string("title") ?: "",
                composer.string("normalization_key").orEmpty(),
                artworkKey,
                composer.string("created_at").orEmpty().ifBlank { track.createdAt },
            )
        }
    }
    return composers.values.toList()
}

internal fun cachedAssetPath(filesDir: File, asset: SyncAssetEntity?): String? = asset
    ?.relativePath
    ?.takeIf { !it.startsWith('/') && ".." !in it.split('/') }
    ?.takeIf { File(filesDir, it).isFile }

private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.int(name: String): Int = (this[name] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0

private fun JsonObject.arrayNames(name: String): String = ((this[name] as? JsonArray).orEmpty())
    .mapNotNull { (it as? JsonObject)?.string("name")?.trim()?.takeIf(String::isNotEmpty) }
    .joinToString(", ")

private fun JsonObject.arraySortNames(name: String): String = ((this[name] as? JsonArray).orEmpty())
    .mapNotNull { (it as? JsonObject)?.string("sort_name")?.trim()?.takeIf(String::isNotEmpty) }
    .joinToString(", ")

private fun LibraryTrackRow.metadataObject(): JsonObject? = runCatching {
    LibrarySyncProtocol.json.parseToJsonElement(rawJson) as? JsonObject
}.getOrNull()
