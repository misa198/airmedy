package me.misa198.airmedy.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.storage.StorageManager
import android.util.Log
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import me.misa198.airmedy.MainActivity
import me.misa198.airmedy.R
import me.misa198.airmedy.player.PlaybackService
import me.misa198.airmedy.pairing.HiveMqSyncSession
import me.misa198.airmedy.pairing.PairingEndpoint
import me.misa198.airmedy.pairing.PairingPreferences
import me.misa198.airmedy.pairing.SyncSession
import me.misa198.airmedy.sync.LibrarySyncClock
import me.misa198.airmedy.sync.LibrarySyncCapacity
import me.misa198.airmedy.sync.LibrarySyncCoordinator
import me.misa198.airmedy.sync.LibrarySyncProtocol
import me.misa198.airmedy.sync.LibrarySyncReceiptPublisher
import me.misa198.airmedy.sync.LibrarySyncResult
import me.misa198.airmedy.sync.LibrarySyncProgressReporter
import me.misa198.airmedy.sync.LibrarySyncFailure

sealed interface AndroidSyncState {
    data object Idle : AndroidSyncState
    data class Running(val planId: String? = null, val completed: Int = 0, val total: Int = 0) : AndroidSyncState
    data class Failed(val message: String, val requiredBytes: Long? = null, val availableBytes: Long? = null) : AndroidSyncState
    data class Completed(val planId: String) : AndroidSyncState
}

internal object AndroidSyncRuntime {
    private lateinit var store: AndroidLibrarySyncStore
    private lateinit var appContext: Context
    private var handoffSession: SyncSession? = null
    private val _state = MutableStateFlow<AndroidSyncState>(AndroidSyncState.Idle)
    val state: StateFlow<AndroidSyncState> = _state

    fun initialize(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        store = AndroidLibrarySyncStore(SyncDatabase.create(appContext), appContext.filesDir)
    }

    fun start(context: Context, payload: String, endpoint: PairingEndpoint, mqttSession: SyncSession) {
        initialize(context)
        handoffSession = mqttSession
        running()
        val intent = Intent(appContext, LibrarySyncService::class.java)
            .putExtra(LibrarySyncService.RequestExtra, payload)
            .putExtra(LibrarySyncService.HostExtra, endpoint.host)
            .putExtra(LibrarySyncService.PortExtra, endpoint.port)
        appContext.startForegroundService(intent)
    }

    fun running(planId: String? = null, completed: Int = 0, total: Int = 0) { _state.value = AndroidSyncState.Running(planId, completed, total) }
    fun failed(message: String, requiredBytes: Long? = null, availableBytes: Long? = null) {
        _state.value = AndroidSyncState.Failed(message, requiredBytes, availableBytes)
    }
    fun completed(planId: String) { _state.value = AndroidSyncState.Completed(planId) }
    fun idle() { _state.value = AndroidSyncState.Idle }
    /** Reconciliation must never borrow the MQTT session while foreground sync owns it. */
    suspend fun awaitNoForegroundSync() { state.first { it !is AndroidSyncState.Running } }
    suspend fun clearAll() { if (::appContext.isInitialized) store.clearAll() }
    fun tracks(): Flow<List<LibraryTrack>> = if (::appContext.isInitialized) store.tracks else flowOf(emptyList())
    internal fun syncStore(): AndroidLibrarySyncStore = store
    internal fun takeMqttSession(): SyncSession? = handoffSession.also { handoffSession = null }
}

class LibrarySyncService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()
    private var syncJob: Job? = null
    private var session: SyncSession? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val payload = intent?.getStringExtra(RequestExtra) ?: return START_NOT_STICKY
        val host = intent.getStringExtra(HostExtra) ?: return START_NOT_STICKY
        val port = intent.getIntExtra(PortExtra, 0)
        if (port !in 1..65535) return START_NOT_STICKY
        Log.i(LogTag, "Starting foreground library sync service (host=$host, port=$port)")
        AndroidSyncRuntime.initialize(applicationContext)
        showForeground(getString(R.string.sync_notification_connecting), indeterminate = true)
        val previous = syncJob
        previous?.cancel()
        syncJob = scope.launch {
            previous?.join()
            syncMutex.withLock { runSync(payload, PairingEndpoint(host, port), startId) }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.e(LogTag, "Background data sync timed out")
        AndroidSyncRuntime.failed(getString(R.string.sync_error_background_timeout))
        syncJob?.cancel()
        finishSync(startId)
    }

    override fun onDestroy() {
        Log.i(LogTag, "LibrarySyncService destroyed")
        scope.cancel()
        session?.disconnect()
        super.onDestroy()
    }

    private suspend fun runSync(payload: String, endpoint: PairingEndpoint, startId: Int) {
        val preferences = PairingPreferences(applicationContext)
        val desktop = preferences.current() ?: run {
            Log.e(LogTag, "No paired desktop found in preferences")
            AndroidSyncRuntime.failed(getString(R.string.sync_error_transport))
            finishSync(startId); return
        }
        val mobileId = preferences.identity().id
        Log.i(LogTag, "Running library sync for desktop=${desktop.desktopId} mobile=$mobileId endpoint=${endpoint.host}:${endpoint.port}")
        // A request normally arrives on the UI's already-connected session. Keep
        // that session alive after the foreground transfer stops so the desktop
        // can announce a later plan while the app remains open. A service-created
        // fallback has no UI owner and must still be cleaned up.
        val handedOffSession = AndroidSyncRuntime.takeMqttSession()
        val mqtt = (handedOffSession ?: HiveMqSyncSession()).also { session = it }
        if (!mqtt.isConnected.value) {
            Log.d(LogTag, "Connecting MQTT session for sync...")
            mqtt.connect(desktop, endpoint, mobileId, reconnect = true)
        }
        try {
            withTimeout(15_000) { mqtt.isConnected.first { it } }
            Log.i(LogTag, "MQTT session ready. Starting coordinator.handle...")
            AndroidSyncRuntime.running()
            val coordinator = LibrarySyncCoordinator(
                identityProvider = preferences,
                clock = object : LibrarySyncClock { override fun nowMillis(): Long = System.currentTimeMillis() },
                puller = AndroidLibrarySyncPuller(preferences, applicationContext.filesDir),
                store = AndroidSyncRuntime.syncStore(),
                capacity = AndroidLibrarySyncCapacity(applicationContext),
                receipts = LibrarySyncReceiptPublisher { receipt -> mqtt.publish(LibrarySyncProtocol.receiptTopic(desktop.desktopId, mobileId), receipt) },
                progress = LibrarySyncProgressReporter { completed, total ->
                    Log.d(LogTag, "Sync progress: $completed/$total assets")
                    AndroidSyncRuntime.running(completed = completed, total = total)
                    val percent = if (total == 0) 0 else (completed * 100 / total).coerceIn(0, 100)
                    showForeground(
                        getString(R.string.sync_notification_progress, percent),
                        indeterminate = false,
                        progressPercent = percent,
                    )
                },
                assetParallelism = syncDownloadParallelism(Runtime.getRuntime().availableProcessors()),
            )
            when (val result = coordinator.handle(payload, desktop)) {
                is LibrarySyncResult.Completed -> {
                    clearPlaybackIfCurrentTrackWasRemoved()
                    Log.i(LogTag, "Library sync completed successfully! Plan ID=${result.planId}")
                    AndroidSyncRuntime.completed(result.planId)
                    notifyTerminal(getString(R.string.sync_notification_complete))
                }
                is LibrarySyncResult.Failed -> {
                    val message = "Library sync failed: ${result.failure}"
                    Log.e(LogTag, message)
                    val storage = result.failure as? LibrarySyncFailure.InsufficientStorage
                    AndroidSyncRuntime.failed(message, storage?.requiredBytes, storage?.availableBytes)
                    notifyTerminal(getString(R.string.sync_notification_failed))
                }
            }
        } catch (error: Throwable) {
            currentCoroutineContext().ensureActive()
            if (error is kotlinx.coroutines.CancellationException) throw error
            Log.e(LogTag, "Library sync crashed", error)
            AndroidSyncRuntime.failed(error.message ?: getString(R.string.sync_error_transport))
            notifyTerminal(getString(R.string.sync_notification_failed))
        } finally {
            Log.d(LogTag, "Cleaning up sync MQTT session & stopping service")
            releaseMqttSession(mqtt, wasHandedOff = handedOffSession != null)
            session = null
            finishSync(startId)
        }
    }

    private fun showForeground(text: String, indeterminate: Boolean, progressPercent: Int? = null) {
        createChannel()
        startForeground(NotificationId, notification(text, indeterminate, progressPercent, ongoing = true), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    private suspend fun clearPlaybackIfCurrentTrackWasRemoved() {
        val currentTrackId = PlaybackService.queueState.value.currentTrackId
        val syncedTrackIds = AndroidSyncRuntime.syncStore().tracks.first().map { it.id }.toSet()
        if (clearPlaybackForRemovedTrack(currentTrackId, syncedTrackIds)) {
            startForegroundService(PlaybackService.intent(applicationContext, PlaybackService.ActionClearQueue))
        }
    }

    private fun notifyTerminal(text: String) {
        createChannel()
        (getSystemService(NotificationManager::class.java)).notify(NotificationId, notification(text, indeterminate = false, progressPercent = null, ongoing = false))
    }

    private fun notification(text: String, indeterminate: Boolean, progressPercent: Int?, ongoing: Boolean): Notification {
        val progress = syncNotificationProgress(progressPercent, indeterminate)
        return Notification.Builder(this, ChannelId)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(getString(R.string.sync_notification_title))
        .setContentText(text)
        .setOnlyAlertOnce(true)
        .setOngoing(ongoing)
        .setProgress(progress.max, progress.current, progress.indeterminate)
        .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        .build()
    }

    private fun createChannel() {
        (getSystemService(NotificationManager::class.java)).createNotificationChannel(NotificationChannel(ChannelId, getString(R.string.sync_notification_channel), NotificationManager.IMPORTANCE_LOW))
    }

    private fun finishSync(startId: Int) {
        if (stopSelfResult(startId)) stopForeground(STOP_FOREGROUND_REMOVE)
    }

    companion object {
        const val RequestExtra = "library_sync_request"
        const val HostExtra = "library_sync_host"
        const val PortExtra = "library_sync_port"
        private const val ChannelId = "library-sync"
        private const val NotificationId = 2101
        private const val LogTag = "AirmedyLibrarySync"

        fun cancel(context: Context) {
            context.stopService(Intent(context, LibrarySyncService::class.java))
            AndroidSyncRuntime.idle()
        }
    }
}

internal class AndroidLibrarySyncCapacity(
    private val filesDir: File,
    private val uuidForPath: (File) -> UUID,
    private val allocatableBytes: (UUID) -> Long,
) : LibrarySyncCapacity {
    constructor(context: Context) : this(
        context.filesDir,
        context.getSystemService(StorageManager::class.java)::getUuidForPath,
        context.getSystemService(StorageManager::class.java)::getAllocatableBytes,
    )

    override suspend fun availableBytes(): Long = allocatableBytes(uuidForPath(filesDir))
}

internal fun releaseMqttSession(session: SyncSession, wasHandedOff: Boolean) {
    if (!wasHandedOff) session.disconnect()
}

internal data class SyncNotificationProgress(
    val max: Int,
    val current: Int,
    val indeterminate: Boolean,
)

internal fun syncNotificationProgress(percent: Int?, indeterminate: Boolean): SyncNotificationProgress = when {
    indeterminate -> SyncNotificationProgress(max = 0, current = 0, indeterminate = true)
    percent != null -> SyncNotificationProgress(max = 100, current = percent.coerceIn(0, 100), indeterminate = false)
    else -> SyncNotificationProgress(max = 0, current = 0, indeterminate = false)
}

internal fun syncDownloadParallelism(availableProcessors: Int): Int = (availableProcessors / 2).coerceIn(2, 4)

internal fun clearPlaybackForRemovedTrack(currentTrackId: String?, syncedTrackIds: Set<String>) =
    currentTrackId != null && currentTrackId !in syncedTrackIds
