package io.github.gycrosskit.jverification

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IosJVerificationDriverTest {
    @Test
    fun nonTokenCodesPreserveCodeAndDiscardCredentials() {
        var nativeReply: ((Int, String?, String?) -> Unit)? = null
        val driver = IosJVerificationDriver(
            initializeNative = { nativeReply = it },
            preLoginNative = { nativeReply = it },
            authenticateNative = { _, reply -> nativeReply = reply },
            cancelNative = {}, clearCacheNative = {}, closeNative = {},
        )
        val calls: List<((VerificationResult) -> Unit) -> Unit> = listOf(
            driver::initialize, driver::preLogin, { driver.authenticate({}, it) },
        )
        for (call in calls) {
            for ((code, status) in listOf(6001 to VerificationStatus.FAILED, 6002 to VerificationStatus.CANCELED, 2000 to VerificationStatus.FAILED)) {
                var result: VerificationResult? = null
                call { result = it }
                requireNotNull(nativeReply)(code, "secret", null)
                assertEquals(status, result?.status)
                assertEquals(code, result?.vendorCode)
                assertNull(result?.token)
            }
        }
    }
}
