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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import me.misa198.airmedy.sync.LibrarySyncAsset
import me.misa198.airmedy.sync.LibrarySyncManifest
import me.misa198.airmedy.sync.LibrarySyncRequest
import me.misa198.airmedy.sync.LibrarySyncStore
import me.misa198.airmedy.sync.LibrarySyncProtocol
import me.misa198.airmedy.sync.PulledAsset

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

@Entity(tableName = "sync_documents", primaryKeys = ["planId", "kind", "documentKey"])
internal data class SyncDocumentEntity(
    val planId: String,
    val kind: String,
    val documentKey: String,
    val rawJson: String,
)

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
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertDocuments(values: List<SyncDocumentEntity>)

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
        ORDER BY t.artists, t.album, t.title
    """)
    fun observeTracks(): Flow<List<LibraryTrackRow>>

    @Query("""
        SELECT a.assetId AS assetId, a.relativePath AS relativePath
        FROM sync_assets a
        INNER JOIN sync_plans p ON p.planId = a.planId
        WHERE p.active = 1 AND a.relativePath IS NOT NULL AND a.assetId LIKE 'artwork:%'
    """)
    fun observeArtworkAssets(): Flow<List<ArtworkAssetRow>>
}

@Database(
    entities = [SyncPlanEntity::class, SyncAssetEntity::class, SyncTrackEntity::class, SyncPlaylistEntity::class, SyncDocumentEntity::class],
    version = 5,
    exportSchema = false,
)
internal abstract class SyncDatabase : RoomDatabase() {
    abstract fun syncDao(): SyncDao

    companion object {
        fun create(context: Context): SyncDatabase = Room.databaseBuilder(context, SyncDatabase::class.java, "library-sync.db")
            .addMigrations(Migration2To3, Migration3To4, Migration4To5)
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
    val discNumber: Int = 0,
    val trackNumber: Int = 0,
    val syncOrder: Int = 0,
    /** Canonical lossless TrackDTO JSON from the desktop manifest (desktop path excluded). */
    val metadataJson: String = "{}",
    val artworkPath: String? = null,
    val audioPath: String? = null,
)

/** Reads non-indexed desktop metadata without requiring a Room schema change. */
fun LibraryTrack.metadataObject(): JsonObject? = runCatching {
    LibrarySyncProtocol.json.parseToJsonElement(metadataJson) as? JsonObject
}.getOrNull()

data class LibraryArtist(
    val id: String,
    val name: String,
    val createdAt: String = "",
    val artworkPath: String? = null,
)

data class LibraryAlbum(
    val id: String,
    val title: String,
    val artist: String = "",
    val createdAt: String = "",
    val artworkPath: String? = null,
    val year: Int = 0,
)

data class LibraryGenre(
    val id: String,
    val name: String,
    val createdAt: String = "",
)

data class LibraryComposer(
    val id: String,
    val name: String,
    val createdAt: String = "",
    val artworkPath: String? = null,
)

internal class AndroidLibrarySyncStore(
    private val database: SyncDatabase,
    private val filesDir: File,
) : LibrarySyncStore {
    private val dao = database.syncDao()
    val tracks: Flow<List<LibraryTrack>> = dao.observeTracks().map { rows ->
        rows.map { row ->
            LibraryTrack(
                id = row.id,
                title = row.title,
                artists = row.artists,
                album = row.album,
                albumId = row.albumId,
                artworkKey = row.artworkKey,
                playCount = row.playCount,
                createdAt = row.createdAt,
                discNumber = row.discNumber,
                trackNumber = row.trackNumber,
                syncOrder = row.syncOrder,
                metadataJson = row.rawJson,
                artworkPath = row.artworkPath,
                audioPath = row.audioPath,
            )
        }
    }
    val artists: Flow<List<LibraryArtist>> = combine(
        dao.observeTracks(),
        dao.observeArtworkAssets(),
    ) { rows, artworkAssets ->
        libraryArtistsFrom(rows, artworkAssets.associate { asset ->
            asset.assetId.removePrefix("artwork:") to asset.relativePath
        })
    }
    val albums: Flow<List<LibraryAlbum>> = combine(
        dao.observeTracks(),
        dao.observeArtworkAssets(),
    ) { rows, artworkAssets ->
        libraryAlbumsFrom(rows, artworkAssets.associate { asset ->
            asset.assetId.removePrefix("artwork:") to asset.relativePath
        })
    }
    val genres: Flow<List<LibraryGenre>> = dao.observeTracks().map { rows ->
        libraryGenresFrom(rows)
    }
    val composers: Flow<List<LibraryComposer>> = combine(
        dao.observeTracks(),
        dao.observeArtworkAssets(),
    ) { rows, artworkAssets ->
        libraryComposersFrom(rows, artworkAssets.associate { asset ->
            asset.assetId.removePrefix("artwork:") to asset.relativePath
        })
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
            dao.insertPlaylists(manifest.playlists.orEmpty().mapNotNull { it.toPlaylist(request.planId) })
            dao.insertDocuments(manifest.lyrics.entries.map { SyncDocumentEntity(request.planId, "lyric", it.key, it.value.toString()) })
            dao.insertDocuments(manifest.analysis.entries.map { SyncDocumentEntity(request.planId, "analysis", it.key, it.value.toString()) })
        }
    }

    override suspend fun isAssetCommitted(planId: String, asset: LibrarySyncAsset): Boolean =
        dao.asset(planId, asset.id)?.let {
            it.sha256 == asset.sha256 && it.size == asset.size && cachedAssetPath(filesDir, it) != null
        } == true

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

    suspend fun clearAll() {
        val assets = database.withTransaction {
            dao.staleAssets("__never_matches__").also {
                dao.deleteStaleAssets("__never_matches__"); dao.deleteStaleTracks("__never_matches__"); dao.deleteStalePlaylists("__never_matches__"); dao.deleteStaleDocuments("__never_matches__"); dao.deleteStalePlans("__never_matches__")
            }
        }
        assets.forEach { it.relativePath?.let { path -> File(filesDir, path).delete() } }
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
                createdAt = artist.string("created_at").orEmpty(),
                artworkPath = artworkKey?.let(artworkPaths::get),
            )
            artists[id] = artists[id]?.let { existing ->
                existing.copy(
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
            artist = root.arrayNames("album_artists").ifBlank { root.arrayNames("artists") },
            createdAt = album.string("created_at").orEmpty().ifBlank { track.createdAt },
            artworkPath = album.string("artwork_key")?.takeIf(String::isNotBlank)?.let(artworkPaths::get),
            year = album.int("year"),
        )
        albums[id] = albums[id]?.let { existing ->
            existing.copy(
                artist = existing.artist.ifBlank { candidate.artist },
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

        fun addGenre(rawId: String?, rawName: String, createdAt: String) {
            val name = rawName.trim()
            if (name.isBlank()) return
            val id = rawId?.trim()?.takeIf(String::isNotBlank) ?: name.lowercase()
            val candidate = LibraryGenre(
                id = id,
                name = name,
                createdAt = createdAt,
            )
            genres[id] = genres[id]?.let { existing ->
                existing.copy(
                    createdAt = listOf(existing.createdAt, candidate.createdAt)
                        .filter(String::isNotBlank)
                        .minOrNull()
                        .orEmpty(),
                )
            } ?: candidate
        }

        fun parseElement(element: kotlinx.serialization.json.JsonElement?) {
            when (element) {
                is JsonArray -> {
                    element.forEach { value ->
                        when (value) {
                            is JsonObject -> {
                                val id = value.string("id")
                                val name = value.string("name") ?: value.string("title") ?: ""
                                val createdAt = value.string("created_at").orEmpty().ifBlank { track.createdAt }
                                addGenre(id, name, createdAt)
                            }
                            is JsonPrimitive -> {
                                val name = value.contentOrNull ?: ""
                                addGenre(null, name, track.createdAt)
                            }
                            else -> {}
                        }
                    }
                }
                is JsonObject -> {
                    val id = element.string("id")
                    val name = element.string("name") ?: element.string("title") ?: ""
                    val createdAt = element.string("created_at").orEmpty().ifBlank { track.createdAt }
                    addGenre(id, name, createdAt)
                }
                is JsonPrimitive -> {
                    val str = element.contentOrNull.orEmpty()
                    str.split(',', ';', '/').forEach { part ->
                        addGenre(null, part, track.createdAt)
                    }
                }
                else -> {}
            }
        }

        parseElement(root["genres"])
        parseElement(root["genre"])
        parseElement(root["raw_genre_names"])
        parseElement(root["raw_genres"])
        parseElement(root["genre_names"])
    }
    return genres.values.toList()
}

internal fun libraryComposersFrom(
    tracks: List<LibraryTrackRow>,
    artworkPaths: Map<String, String>,
): List<LibraryComposer> {
    val composers = linkedMapOf<String, LibraryComposer>()
    tracks.forEach { track ->
        val root = runCatching { LibrarySyncProtocol.json.parseToJsonElement(track.rawJson) as? JsonObject }.getOrNull()
            ?: return@forEach

        fun addComposer(rawId: String?, rawName: String, artworkKey: String?, createdAt: String) {
            val name = rawName.trim()
            if (name.isBlank()) return
            val id = rawId?.trim()?.takeIf(String::isNotBlank) ?: name.lowercase()
            val candidate = LibraryComposer(
                id = id,
                name = name,
                createdAt = createdAt,
                artworkPath = artworkKey?.let(artworkPaths::get),
            )
            composers[id] = composers[id]?.let { existing ->
                existing.copy(
                    artworkPath = existing.artworkPath ?: candidate.artworkPath,
                    createdAt = listOf(existing.createdAt, candidate.createdAt)
                        .filter(String::isNotBlank)
                        .minOrNull()
                        .orEmpty(),
                )
            } ?: candidate
        }

        fun parseElement(element: kotlinx.serialization.json.JsonElement?) {
            when (element) {
                is JsonArray -> {
                    element.forEach { value ->
                        when (value) {
                            is JsonObject -> {
                                val id = value.string("id")
                                val name = value.string("name") ?: value.string("title") ?: ""
                                val artworkKey = value.string("artwork_key")?.takeIf(String::isNotBlank)
                                    ?: listOf("artwork_key_manual", "artwork_key_local", "artwork_key_online")
                                        .firstNotNullOfOrNull { key -> value.string(key)?.takeIf(String::isNotBlank) }
                                val createdAt = value.string("created_at").orEmpty().ifBlank { track.createdAt }
                                addComposer(id, name, artworkKey, createdAt)
                            }
                            is JsonPrimitive -> {
                                val name = value.contentOrNull ?: ""
                                addComposer(null, name, null, track.createdAt)
                            }
                            else -> {}
                        }
                    }
                }
                is JsonObject -> {
                    val id = element.string("id")
                    val name = element.string("name") ?: element.string("title") ?: ""
                    val artworkKey = element.string("artwork_key")?.takeIf(String::isNotBlank)
                        ?: listOf("artwork_key_manual", "artwork_key_local", "artwork_key_online")
                            .firstNotNullOfOrNull { key -> element.string(key)?.takeIf(String::isNotBlank) }
                    val createdAt = element.string("created_at").orEmpty().ifBlank { track.createdAt }
                    addComposer(id, name, artworkKey, createdAt)
                }
                is JsonPrimitive -> {
                    val str = element.contentOrNull.orEmpty()
                    str.split(',', ';', '/').forEach { part ->
                        addComposer(null, part, null, track.createdAt)
                    }
                }
                else -> {}
            }
        }

        parseElement(root["composers"])
        parseElement(root["composer"])
        parseElement(root["raw_composer_names"])
        parseElement(root["raw_composers"])
        parseElement(root["composer_names"])
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
