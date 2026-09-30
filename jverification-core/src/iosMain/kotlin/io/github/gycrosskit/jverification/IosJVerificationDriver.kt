package io.github.gycrosskit.jverification

/** Swift 原生包不依赖某个应用的 Shared.framework，宿主用闭包连接两种产物。 */
class IosJVerificationDriver(
    private val initializeNative: ((Int, String?, String?) -> Unit) -> Unit,
    private val preLoginNative: ((Int, String?, String?) -> Unit) -> Unit,
    private val authenticateNative: ((Int, String?, String?) -> Unit) -> Unit,
    private val cancelNative: () -> Unit,
    private val clearCacheNative: () -> Unit,
    private val closeNative: () -> Unit,
) : JVerificationDriver {
    override fun initialize(callback: (VerificationResult) -> Unit) = initializeNative(reply(callback, false))
    override fun preLogin(callback: (VerificationResult) -> Unit) = preLoginNative(reply(callback, false))
    override fun authenticate(callback: (VerificationResult) -> Unit) = authenticateNative(reply(callback, true))
    override fun cancel() = cancelNative()
    override fun clearPreLoginCache() = clearCacheNative()
    override fun close() = closeNative()

    private fun reply(callback: (VerificationResult) -> Unit, login: Boolean): (Int, String?, String?) -> Unit = { code, token, carrier ->
        val status = when {
            login && code == 6000 && !token.isNullOrBlank() -> VerificationStatus.TOKEN
            !login && code in listOf(0, 8000, 7000) -> VerificationStatus.READY
            code in listOf(2000, 6001, 6002) -> VerificationStatus.CANCELED
            code == -10 -> VerificationStatus.CONSENT_REQUIRED
            code == -11 -> VerificationStatus.BUSY
            code == -12 -> VerificationStatus.CLOSED
            code == -13 -> VerificationStatus.UNSUPPORTED
            code == -14 -> VerificationStatus.TIMEOUT
            else -> VerificationStatus.FAILED
        }
        callback(VerificationResult(status, code, token.takeIf { status == VerificationStatus.TOKEN }, carrier))
    }
}
