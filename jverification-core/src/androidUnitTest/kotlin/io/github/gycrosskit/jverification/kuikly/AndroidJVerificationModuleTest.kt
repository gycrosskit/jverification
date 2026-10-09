package io.github.gycrosskit.jverification.kuikly

import android.os.Looper
import io.github.gycrosskit.jverification.*
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import kotlin.concurrent.thread
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class AndroidJVerificationModuleTest {
    private class Driver : JVerificationDriver {
        var initialized = 0; var cancelled = 0; var closed = 0
        var result: ((VerificationResult) -> Unit)? = null
        var opened: (() -> Unit)? = null
        override fun initialize(callback: (VerificationResult) -> Unit) { assertMain(); initialized++; result = callback }
        override fun preLogin(callback: (VerificationResult) -> Unit) { assertMain(); result = callback }
        override fun authenticate(opened: () -> Unit, callback: (VerificationResult) -> Unit) { assertMain(); this.opened = opened; result = callback }
        override fun cancel() { assertMain(); cancelled++ }
        override fun clearPreLoginCache() { assertMain() }
        override fun close() { assertMain(); closed++ }
    }
    @Test fun consentFactoryMainAndNativeResultAreRequired() {
        val driver = Driver(); var factories = 0
        val module = AndroidJVerificationModule { assertMain(); factories++; driver }
        val replies = mutableListOf<JSONObject>()
        module.call("initialize", "{\"consentGranted\":1}") { replies += JSONObject(it as String) }
        assertEquals("CONSENT_REQUIRED", replies.single().getString("status")); assertEquals(0, factories)
        thread { module.call("initialize", "{\"consentGranted\":true}") { replies += JSONObject(it as String) } }.join()
        assertEquals(0, factories)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, factories); assertEquals(1, replies.size)
        thread { driver.result!!(VerificationResult(VerificationStatus.READY, 8000)) }.join()
        assertEquals(1, replies.size)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("READY", replies.last().getString("status"))
    }
    @Test fun openedAndTerminalAreOnceAndNeverSynthesized() {
        val driver = Driver(); val module = AndroidJVerificationModule { driver }; val replies = mutableListOf<JSONObject>()
        module.call("authenticate", "{\"consentGranted\":true}") { replies += JSONObject(it as String) }
        assertTrue(replies.isEmpty())
        driver.opened!!(); driver.opened!!()
        assertEquals("opened", replies.single().getString("event"))
        driver.result!!(VerificationResult(VerificationStatus.TOKEN, 6000, "token"))
        driver.opened!!(); driver.result!!(VerificationResult(VerificationStatus.TOKEN, 6000, "late"))
        assertEquals(2, replies.size); assertEquals("token", replies.last().getString("token"))
    }
    @Test fun factoryShutdownClosesUnadoptedDriverWithoutStartingSdk() {
        for (destroy in listOf(true, false)) {
            val driver = Driver()
            lateinit var module: AndroidJVerificationModule
            module = AndroidJVerificationModule {
                if (destroy) module.onDestroy() else module.call("close", "{}", null)
                driver
            }
            var replies = 0
            module.call("initialize", "{\"consentGranted\":true}") { replies++ }
            assertEquals(0, driver.initialized)
            assertEquals(1, driver.closed)
            assertEquals(0, replies)
            module.onDestroy()
            assertEquals(1, driver.closed)
        }
    }
    @Test fun cancelDestroyAndQueuedFactoryCannotReviveOldRenderer() {
        val oldDriver = Driver(); val old = AndroidJVerificationModule { oldDriver }; val nextDriver = Driver()
        val next = AndroidJVerificationModule { nextDriver }; var oldReplies = 0; var nextReplies = 0
        old.call("authenticate", "{\"consentGranted\":true}") { oldReplies++ }
        val late = oldDriver.result!!
        old.call("cancel", "{}", null)
        late(VerificationResult(VerificationStatus.TOKEN, 6000, "old")); assertEquals(0, oldReplies)
        next.call("initialize", "{\"consentGranted\":true}") { nextReplies++ }
        old.onDestroy(); old.onDestroy()
        nextDriver.result!!(VerificationResult(VerificationStatus.READY))
        assertEquals(1, nextReplies); assertEquals(0, nextDriver.closed)
        var created = 0
        val queued = AndroidJVerificationModule { created++; Driver() }
        thread { queued.call("initialize", "{\"consentGranted\":true}", null); queued.onDestroy() }.join()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, created)
    }
}
private fun assertMain() = assertEquals(Looper.getMainLooper(), Looper.myLooper())
