package me.misa198.airmedy

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalNetworkAccessTest {
    @Test
    fun grantedPermissionAlwaysAllowsAccess() {
        assertEquals(LocalNetworkAccess.Granted, localNetworkAccessState(true, false, false))
        assertEquals(LocalNetworkAccess.Granted, localNetworkAccessState(true, true, false))
    }

    @Test
    fun initialRequestAndRetryableDenialOfferThePermissionDialog() {
        assertEquals(LocalNetworkAccess.Required, localNetworkAccessState(false, false, false))
        assertEquals(LocalNetworkAccess.Required, localNetworkAccessState(false, true, true))
    }

    @Test
    fun permanentDenialOffersSettingsInsteadOfRequestingAgain() {
        assertEquals(LocalNetworkAccess.Settings, localNetworkAccessState(false, true, false))
    }
}
