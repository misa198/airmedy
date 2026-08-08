package me.misa198.airmedy.pairing

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import me.misa198.airmedy.pairing.PairingBroadcastRecord
import me.misa198.airmedy.pairing.PairingBroadcastResolver
import me.misa198.airmedy.pairing.PairingEndpoint
import me.misa198.airmedy.pairing.PairedDesktop

interface TrustedDesktopDiscovery {
    val endpoints: Flow<PairingEndpoint>
    val unavailableEndpoints: Flow<PairingEndpoint>

    fun start(desktop: PairedDesktop)
    fun stop()
}

/** Android DNS-SD adapter for endpoint discovery of an already trusted desktop. */
class AndroidTrustedDesktopDiscovery(context: Context) : TrustedDesktopDiscovery {
    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(NsdManager::class.java)
    private val multicastLock = appContext.getSystemService(WifiManager::class.java)
        .createMulticastLock("airmedy-pairing-discovery").apply { setReferenceCounted(false) }
    private val _endpoints = MutableSharedFlow<PairingEndpoint>(extraBufferCapacity = 1)
    override val endpoints: Flow<PairingEndpoint> = _endpoints
    private val _unavailableEndpoints = MutableSharedFlow<PairingEndpoint>(extraBufferCapacity = 1)
    override val unavailableEndpoints: Flow<PairingEndpoint> = _unavailableEndpoints

    private var active = false
    private var discoveryStarted = false
    private var generation = 0L
    private var trustedDesktop: PairedDesktop? = null
    private var listener: NsdManager.DiscoveryListener? = null
    private val resolvingServices = mutableSetOf<String>()
    private val endpointsByService = mutableMapOf<String, PairingEndpoint>()

    override fun start(desktop: PairedDesktop) {
        if (active && trustedDesktop?.desktopId == desktop.desktopId) return
        stop()
        active = true
        trustedDesktop = desktop
        val currentGeneration = ++generation
        if (!multicastLock.isHeld) multicastLock.acquire()
        listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                if (!active || currentGeneration != generation) {
                    runCatching { nsdManager.stopServiceDiscovery(this) }
                } else {
                    discoveryStarted = true
                }
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!active || currentGeneration != generation || !resolvingServices.add(serviceInfo.serviceName)) return
                resolve(serviceInfo, currentGeneration)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                resolvingServices.remove(serviceInfo.serviceName)
                endpointsByService.remove(serviceInfo.serviceName)?.let(_unavailableEndpoints::tryEmit)
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                if (currentGeneration == generation) stop()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit

            override fun onDiscoveryStopped(serviceType: String) = Unit
        }
        nsdManager.discoverServices(ServiceType, NsdManager.PROTOCOL_DNS_SD, listener!!)
    }

    override fun stop() {
        val activeListener = listener
        active = false
        trustedDesktop = null
        generation++
        resolvingServices.clear()
        endpointsByService.clear()
        listener = null
        if (discoveryStarted && activeListener != null) {
            runCatching { nsdManager.stopServiceDiscovery(activeListener) }
        }
        discoveryStarted = false
        if (multicastLock.isHeld) multicastLock.release()
    }

    @Suppress("DEPRECATION")
    private fun resolve(serviceInfo: NsdServiceInfo, currentGeneration: Long) {
        nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                resolvingServices.remove(serviceInfo.serviceName)
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                resolvingServices.remove(serviceInfo.serviceName)
                val desktop = trustedDesktop ?: return
                if (!active || currentGeneration != generation) return
                val endpoint = PairingBroadcastResolver.resolve(
                    PairingBroadcastRecord(
                        srvPort = serviceInfo.port,
                        txt = serviceInfo.attributes.mapValues { (_, value) -> String(value, StandardCharsets.UTF_8) },
                    ),
                    desktop.desktopId,
                ) ?: return
                endpointsByService[serviceInfo.serviceName] = endpoint
                _endpoints.tryEmit(endpoint)
            }
        })
    }

    private companion object {
        const val ServiceType = "_airmedy-pair._tcp."
    }
}
