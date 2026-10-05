package io.github.gycrosskit.jverification.kuikly

import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import io.github.gycrosskit.jverification.VerificationStatus
import kotlin.test.*

class JVerificationModuleTest {
    @Test fun closeAndDisposeShareOneNativeCloseAndDiscardLateCredentials() {
        val module = JVerificationModule()
        var opened = 0
        val statuses = mutableListOf<VerificationStatus>()
        module.authenticate({ opened++ }) { statuses += it.status }
        val late = module.calls.single()
        assertEquals(1, module.liveCallbacks.size)
        module.close()
        module.dispose()
        module.close()
        assertEquals(1, module.calls.count { it.method == "close" })
        assertTrue(module.liveCallbacks.isEmpty())
        late.deliver(JSONObject().apply { put("event", "opened") })
        late.deliver(JSONObject().apply { put("status", "TOKEN"); put("token", "secret") })
        assertEquals(0, opened)
        assertTrue(statuses.isEmpty())
        module.initialize { statuses += it.status }
        assertEquals(listOf(VerificationStatus.CLOSED), statuses)
        assertEquals(2, module.calls.size)
    }

    @Test fun blankTokenFailsAndOpenedEventDoesNotEndAuthentication() {
        val module = JVerificationModule()
        val events = mutableListOf<String>()
        module.authenticate({ events += "opened" }) { events += it.status.name }
        val call = module.calls.single()
        repeat(2) { call.deliver(JSONObject().apply { put("event", "opened") }) }
        assertEquals(listOf("opened"), events)
        assertEquals(1, module.liveCallbacks.size)
        call.deliver(JSONObject().apply { put("status", "TOKEN"); put("token", " ") })
        assertEquals(listOf("opened", "FAILED"), events)
        assertTrue(module.liveCallbacks.isEmpty())
        call.deliver(JSONObject().apply { put("status", "TOKEN"); put("token", "secret") })
        assertEquals(2, events.size)
        module.dispose()
    }
}
