package io.github.gycrosskit.jverification

import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class JVerificationClientTest {
    private class Driver : JVerificationDriver {
        var initCalls = 0
        var authCalls = 0
        var preCalls = 0
        var cleared = false
        var reply: ((VerificationResult) -> Unit)? = null
        override fun initialize(callback: (VerificationResult) -> Unit) { initCalls++; callback(VerificationResult(VerificationStatus.READY)) }
        override fun preLogin(callback: (VerificationResult) -> Unit) { preCalls++; callback(VerificationResult(VerificationStatus.READY)) }
        override fun authenticate(callback: (VerificationResult) -> Unit) { authCalls++; reply = callback }
        override fun cancel() {}
        override fun clearPreLoginCache() { cleared = true }
        override fun close() {}
    }

    @Test fun consentPreloadAndRevocation() = runTest {
        val driver = Driver()
        val client = JVerificationClient(driver, StandardTestDispatcher(testScheduler))
        assertEquals(VerificationStatus.CONSENT_REQUIRED, client.prepare(false).status)
        assertEquals(0, driver.initCalls)
        assertEquals(VerificationStatus.READY, client.prepare(true).status)
        assertEquals(1, driver.preCalls)
        assertEquals(0, driver.authCalls)
        var result: VerificationResult? = null
        val job = launch { result = client.authenticate(true) }
        runCurrent()
        assertEquals(VerificationStatus.BUSY, client.authenticate(true).status)
        client.revokeConsent()
        job.join()
        assertEquals(VerificationStatus.CANCELED, result?.status)
        driver.reply?.invoke(VerificationResult(VerificationStatus.TOKEN, 6000, "secret"))
        assertNull(result?.token)
        assertEquals(true, driver.cleared)
        client.close()
        assertEquals(VerificationStatus.CLOSED, client.authenticate(true).status)
    }

    @Test fun timeoutAndRedaction() = runTest {
        val client = JVerificationClient(Driver(), StandardTestDispatcher(testScheduler), authenticationTimeoutMillis = 10)
        assertEquals(VerificationStatus.TIMEOUT, client.authenticate(true).status)
        assertFalse(VerificationResult(VerificationStatus.TOKEN, 6000, "secret").toString().contains("secret"))
    }

    @Test fun revocationAfterTokenCompletionBeforeResume() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val driver = Driver()
        val client = JVerificationClient(driver, dispatcher)
        var result: VerificationResult? = null
        val job = launch(dispatcher) { result = client.authenticate(true) }
        runCurrent()
        driver.reply!!.invoke(VerificationResult(VerificationStatus.TOKEN, 6000, "secret"))
        // 模拟 Main 已先收到撤销事件，认证协程仍在调度队列中。
        launch(dispatcher, start = CoroutineStart.UNDISPATCHED) { client.revokeConsent() }.join()
        job.join()
        assertEquals(VerificationStatus.CANCELED, result?.status)
        assertNull(result?.token)
    }
}
