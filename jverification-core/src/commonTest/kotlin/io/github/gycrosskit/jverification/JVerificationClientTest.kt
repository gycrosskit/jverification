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
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class JVerificationClientTest {
    private class Driver : JVerificationDriver {
        var initCalls = 0
        var authCalls = 0
        var preCalls = 0
        var cleared = false
        var reply: ((VerificationResult) -> Unit)? = null
        var opened: (() -> Unit)? = null
        override fun initialize(callback: (VerificationResult) -> Unit) { initCalls++; callback(VerificationResult(VerificationStatus.READY)) }
        override fun preLogin(callback: (VerificationResult) -> Unit) { preCalls++; callback(VerificationResult(VerificationStatus.READY)) }
        override fun authenticate(opened: () -> Unit, callback: (VerificationResult) -> Unit) { authCalls++; this.opened = opened; reply = callback }
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
        val driver = Driver()
        val client = JVerificationClient(driver, StandardTestDispatcher(testScheduler), authenticationTimeoutMillis = 10)
        var opened = 0
        assertEquals(VerificationStatus.TIMEOUT, client.authenticate(true) { opened++ }.status)
        driver.opened?.invoke()
        runCurrent()
        assertEquals(0, opened)
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

    @Test fun openedOnceBeforeEachFinalResult() = runTest {
        val driver = Driver()
        val client = JVerificationClient(driver, StandardTestDispatcher(testScheduler))
        assertEquals(VerificationStatus.READY, client.prepare(true).status)
        assertNull(driver.opened)
        for (status in listOf(VerificationStatus.TOKEN, VerificationStatus.CANCELED, VerificationStatus.FAILED)) {
            val events = mutableListOf<String>()
            val job = launch {
                events += client.authenticate(true) { events += "opened" }.status.name
            }
            runCurrent()
            assertTrue(events.isEmpty())
            driver.opened?.invoke()
            driver.opened?.invoke()
            runCurrent()
            assertEquals(listOf("opened"), events)
            assertTrue(job.isActive)
            driver.reply?.invoke(VerificationResult(status, token = "secret".takeIf { status == VerificationStatus.TOKEN }))
            job.join()
            driver.opened?.invoke()
            runCurrent()
            assertEquals(listOf("opened", status.name), events)
        }
        // SDK 连续通知打开和结果时仍保留顺序，且拉页失败不能伪造 opened。
        val events = mutableListOf<String>()
        val tokenJob = launch { events += client.authenticate(true) { events += "opened" }.status.name }
        runCurrent()
        driver.opened?.invoke()
        driver.reply?.invoke(VerificationResult(VerificationStatus.TOKEN, token = "secret"))
        tokenJob.join()
        assertEquals(listOf("opened", "TOKEN"), events)
        val failedJob = launch { events += client.authenticate(true) { events += "unexpected" }.status.name }
        runCurrent()
        driver.reply?.invoke(VerificationResult(VerificationStatus.FAILED))
        failedJob.join()
        assertEquals(listOf("opened", "TOKEN", "FAILED"), events)
    }

    @Test fun queuedAndLateOpenedInvalidAfterCancelRevokeAndClose() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val driver = Driver()
        val client = JVerificationClient(driver, dispatcher)
        var opened = 0
        for (stop in listOf<suspend () -> Unit>({ client.cancel() }, { client.revokeConsent() }, { client.close() })) {
            val job = launch { assertEquals(VerificationStatus.CANCELED, client.authenticate(true) { opened++ }.status) }
            runCurrent()
            val oldOpened = driver.opened
            oldOpened?.invoke()
            launch(dispatcher, start = CoroutineStart.UNDISPATCHED) { stop() }.join()
            job.join()
            oldOpened?.invoke()
            runCurrent()
            assertEquals(0, opened)
        }
    }
}
