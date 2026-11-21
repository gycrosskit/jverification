package io.github.gycrosskit.jverification

import android.app.Activity
import android.os.Looper
import cn.jiguang.verifysdk.api.JVerificationInterface as SDK
import cn.jiguang.verifysdk.api.JVerifyUIConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AndroidJVerificationDriverTest {
    private fun driver(activity: Activity = Activity(), key: String = "fixture-key") =
        AndroidJVerificationDriver(activity, key, ::JVerifyUIConfig)

    @Test
    fun initializationOwnerTransferRejectsForeignOwnerAndDeliversOnlyCurrentWaiter() {
        SDK.reset()
        val first = driver(); val foreign = driver(); val replacement = driver()
        val old = mutableListOf<VerificationResult>(); val blocked = mutableListOf<VerificationResult>()
        val current = mutableListOf<VerificationResult>()
        try {
            first.initialize(old::add)
            foreign.initialize(blocked::add)
            assertEquals(listOf(VerificationStatus.BUSY), blocked.map { it.status })
            foreign.cancel(); foreign.clearPreLoginCache(); foreign.close()
            assertEquals(0, SDK.clearCalls)
            first.close()
            replacement.initialize(current::add)
            assertEquals(1, SDK.initCalls, "same-key replacement joins the in-flight SDK init")
            SDK.initialized = true; SDK.initCallback!!.onResult(8000, "ready"); Looper.drain()
            assertEquals(emptyList(), old)
            assertEquals(listOf(VerificationStatus.READY), current.map { it.status })
            replacement.clearPreLoginCache(); assertEquals(1, SDK.clearCalls)
            val otherKey = driver(key = "different-key")
            try { otherKey.initialize(blocked::add) } finally { otherKey.close() }
            assertEquals(VerificationStatus.BUSY, blocked.last().status)
        } finally { first.close(); foreign.close(); replacement.close(); Looper.drain() }
    }

    @Test
    fun authenticationRejectsInvalidActivityAndSettlesOpenedAndCredentialOnlyOnce() {
        SDK.reset()
        val activity = Activity(); val owner = driver(activity)
        val results = mutableListOf<VerificationResult>(); var opened = 0
        try {
            activity.isFinishing = true
            owner.authenticate({ opened++ }, results::add)
            assertEquals(VerificationStatus.UNSUPPORTED, results.last().status)
            assertEquals(0, SDK.loginCalls)
            activity.isFinishing = false; activity.isDestroyed = true
            owner.authenticate({ opened++ }, results::add)
            assertEquals(0, SDK.loginCalls)
            activity.isDestroyed = false
            owner.authenticate({ opened++ }, results::add)
            val events = SDK.settings!!.listener!!; val callback = SDK.loginCallback!!
            events.onEvent(2, null); events.onEvent(2, null)
            callback(6000, " valid-token ", "carrier", null)
            callback(6000, "duplicate", null, null); events.onEvent(2, null)
            Looper.drain()
            assertEquals(1, opened)
            assertEquals(1, results.count { it.status == VerificationStatus.TOKEN })
            assertEquals(" valid-token ", results.last().token)
            owner.authenticate({}, results::add)
            SDK.loginCallback!!(6000, " \t", null, null); Looper.drain()
            assertEquals(VerificationStatus.FAILED, results.last().status); assertNull(results.last().token)
            owner.authenticate({}, results::add)
            val late = SDK.loginCallback!!; val lateEvent = SDK.settings!!.listener!!
            val count = results.size
            owner.cancel()
            lateEvent.onEvent(2, null); late(6000, "late-token", null, null); Looper.drain()
            assertEquals(count, results.size); assertEquals(1, SDK.dismissCalls)
            owner.close(); owner.close(); assertEquals(1, SDK.dismissCalls)
            owner.initialize(results::add)
            assertEquals(VerificationStatus.CLOSED, results.last().status)
        } finally { owner.close(); Looper.drain() }
    }

    @Test
    fun foreignCancellationCannotClearOwnerWaitersOrDismissOwnerPage() {
        SDK.reset()
        val owner = driver(); val foreign = driver(); val results = mutableListOf<VerificationResult>()
        try {
            owner.initialize(results::add)
            foreign.cancel()
            SDK.initCallback!!.onResult(8000, "ready"); Looper.drain()
            assertEquals(listOf(VerificationStatus.READY), results.map { it.status })
            owner.authenticate({}, results::add)
            foreign.cancel(); foreign.close()
            assertEquals(0, SDK.dismissCalls)
            owner.cancel(); assertEquals(1, SDK.dismissCalls)
        } finally { owner.close(); foreign.close(); Looper.drain() }
    }

    @Test
    fun dismissFailureInvalidatesCallbacksAndDoesNotStrandSdkOwner() {
        SDK.reset()
        val owner = driver()
        val replacement = driver()
        val oldResults = mutableListOf<VerificationResult>()
        val newResults = mutableListOf<VerificationResult>()
        var oldOpened = 0
        try {
            owner.authenticate({ oldOpened++ }, oldResults::add)
            val canceledEvent = SDK.settings!!.listener!!
            val canceledResult = SDK.loginCallback!!
            SDK.dismissFailure = IllegalStateException("dismiss failed")
            owner.cancel()
            canceledEvent.onEvent(2, null)
            canceledResult(6000, "canceled", null, null)
            owner.cancel()
            assertEquals(1, SDK.dismissCalls)

            owner.authenticate({ oldOpened++ }, oldResults::add)
            val closedEvent = SDK.settings!!.listener!!
            val closedResult = SDK.loginCallback!!
            owner.close()
            owner.close()
            assertEquals(2, SDK.dismissCalls)
            replacement.authenticate({}, newResults::add)
            closedEvent.onEvent(2, null)
            closedResult(6000, "closed", null, null)
            SDK.loginCallback!!(6000, "current", null, null)
            Looper.drain()

            assertEquals(0, oldOpened)
            assertEquals(emptyList(), oldResults)
            assertEquals(3, SDK.loginCalls)
            assertEquals("current", newResults.single().token)
        } finally {
            SDK.dismissFailure = null
            owner.close()
            replacement.close()
            Looper.drain()
        }
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun sdkCacheFailureBeforeAndDuringClientCloseStillReleasesOwner() = runTest {
        SDK.reset()
        SDK.initialized = true
        val owner = driver()
        val replacement = driver()
        val client = JVerificationClient(owner, StandardTestDispatcher(testScheduler))
        val newResults = mutableListOf<VerificationResult>()
        var oldOpened = 0
        try {
            val pending = async { client.authenticate(true) { oldOpened++ } }
            runCurrent()
            val oldEvent = SDK.settings!!.listener!!
            val oldResult = SDK.loginCallback!!
            SDK.clearFailure = IllegalStateException("cache clear failed")

            owner.cancel()
            owner.clearPreLoginCache()
            client.close()
            client.close()
            assertEquals(2, SDK.clearCalls)
            assertEquals(VerificationStatus.CANCELED, pending.await().status)
            assertEquals(VerificationStatus.CLOSED, client.authenticate(true).status)

            replacement.authenticate({}, newResults::add)
            assertEquals(2, SDK.loginCalls)
            oldEvent.onEvent(2, null)
            oldResult(6000, "closed", null, null)
            SDK.loginCallback!!(6000, "current", null, null)
            Looper.drain()
            assertEquals(0, oldOpened)
            assertEquals("current", newResults.single().token)
        } finally {
            SDK.clearFailure = null
            owner.close()
            replacement.close()
            Looper.drain()
        }
    }

    @Test
    fun canceledPreLoginAndUnsupportedCapabilityCannotLeakLateResults() {
        SDK.reset()
        val owner = driver(); val results = mutableListOf<VerificationResult>()
        try {
            SDK.enabled = false; owner.preLogin(results::add)
            assertEquals(VerificationStatus.UNSUPPORTED, results.last().status)
            SDK.enabled = true; owner.preLogin(results::add)
            val old = SDK.preLoginCallback!!; owner.cancel()
            owner.preLogin(results::add); val current = SDK.preLoginCallback!!
            old(7000, null, null); current(7000, null, null); Looper.drain()
            assertEquals(listOf(VerificationStatus.UNSUPPORTED, VerificationStatus.READY), results.map { it.status })
        } finally { owner.close(); Looper.drain() }
    }
}
