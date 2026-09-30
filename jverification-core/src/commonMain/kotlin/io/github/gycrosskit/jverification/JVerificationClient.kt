package io.github.gycrosskit.jverification

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

enum class VerificationStatus {
    READY, TOKEN, CONSENT_REQUIRED, CANCELED, TIMEOUT, BUSY, CLOSED, UNSUPPORTED, FAILED
}

/** Token 只交给宿主后端换票；不输出 SDK content，避免意外记录凭据。 */
class VerificationResult(
    val status: VerificationStatus,
    val vendorCode: Int = 0,
    val token: String? = null,
    val carrier: String? = null,
) {
    init {
        require(if (status == VerificationStatus.TOKEN) !token.isNullOrBlank() else token == null)
    }
    override fun toString(): String = "VerificationResult(status=$status, vendorCode=$vendorCode)"
}

/** 三个原生 SDK 的薄边界。回调可来自任意线程；业务同意、页面和后端均由宿主提供。 */
interface JVerificationDriver {
    fun initialize(callback: (VerificationResult) -> Unit)
    fun preLogin(callback: (VerificationResult) -> Unit)
    fun authenticate(opened: () -> Unit = {}, callback: (VerificationResult) -> Unit)
    fun cancel()
    fun clearPreLoginCache()
    fun close()
}

/** 每个进程保持一个 client；Android/iOS 使用 Main，Kuikly 传入其页面 dispatcher。 */
class JVerificationClient(
    private val driver: JVerificationDriver,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val prepareTimeoutMillis: Long = 10_000,
    private val authenticationTimeoutMillis: Long = 120_000,
) {
    private var pending: CompletableDeferred<VerificationResult>? = null
    private var busy = false
    private var closed = false
    private var consent = false
    private var generation = 0L

    init { require(prepareTimeoutMillis > 0 && authenticationTimeoutMillis > 0) }

    suspend fun prepare(consentGranted: Boolean): VerificationResult = perform(consentGranted, false)

    /** SDK 确认授权页打开后通知一次；通知在 client dispatcher 执行，不结束认证。 */
    suspend fun authenticate(consentGranted: Boolean, opened: () -> Unit = {}): VerificationResult =
        perform(consentGranted, true, opened)

    private suspend fun perform(granted: Boolean, login: Boolean, opened: () -> Unit = {}): VerificationResult = withContext(dispatcher) {
        if (closed) return@withContext VerificationResult(VerificationStatus.CLOSED)
        if (!granted) {
            revokeConsent()
            return@withContext VerificationResult(VerificationStatus.CONSENT_REQUIRED)
        }
        if (busy) return@withContext VerificationResult(VerificationStatus.BUSY)
        consent = true
        busy = true
        val attempt = generation
        try {
            val initialized = await(prepareTimeoutMillis) { _, reply -> driver.initialize(reply) }
            if (initialized.status != VerificationStatus.READY) return@withContext initialized
            if (!consent || generation != attempt) return@withContext VerificationResult(VerificationStatus.CANCELED)
            // 预取号不会拉起授权页；认证也不会偷偷改变宿主的同意状态。
            val result = await(if (login) authenticationTimeoutMillis else prepareTimeoutMillis, opened) { onOpened, reply ->
                if (login) driver.authenticate(onOpened, reply) else driver.preLogin(reply)
            }
            // Token 回调已完成、协程尚未恢复时也可能撤销同意，不能返回旧凭据。
            if (closed || !consent || generation != attempt) VerificationResult(VerificationStatus.CANCELED) else result
        } catch (_: TimeoutCancellationException) {
            driver.cancel()
            VerificationResult(VerificationStatus.TIMEOUT)
        } catch (error: CancellationException) {
            driver.cancel()
            throw error
        } catch (_: Exception) {
            driver.cancel()
            VerificationResult(VerificationStatus.FAILED)
        } finally {
            pending = null
            busy = false
        }
    }

    private suspend fun await(
        timeout: Long,
        opened: () -> Unit = {},
        start: (() -> Unit, (VerificationResult) -> Unit) -> Unit,
    ): VerificationResult = coroutineScope {
        val reply = CompletableDeferred<VerificationResult>()
        pending = reply
        val attempt = generation
        var didOpen = false
        try {
            withTimeout(timeout) {
                // 事件和结果按同一 dispatcher 排队，SDK 连续发 opened/Token 时不丢打开通知。
                start({ launch {
                    if (reply.isActive && !didOpen && !closed && consent && generation == attempt) {
                        didOpen = true
                        opened()
                    }
                } }, { result -> launch { reply.complete(result) } })
                reply.await()
            }
        } finally {
            // SDK 不能总是取消底层请求；结束等待后也不接收迟到的 Token。
            reply.cancel()
        }
    }

    suspend fun cancel() = withContext(dispatcher) {
        generation++
        pending?.complete(VerificationResult(VerificationStatus.CANCELED))
        driver.cancel()
    }

    suspend fun revokeConsent() = withContext(dispatcher) {
        consent = false
        cancel()
        driver.clearPreLoginCache()
    }

    suspend fun close() = withContext(dispatcher + NonCancellable) {
        if (!closed) {
            closed = true
            revokeConsent()
            driver.close()
        }
    }
}
