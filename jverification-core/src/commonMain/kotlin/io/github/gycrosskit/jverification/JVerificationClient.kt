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

/** 认证终态与拒绝原因；READY 仅表示初始化/预取号成功，TOKEN 才包含换票凭据。 */
enum class VerificationStatus {
    READY, TOKEN, CONSENT_REQUIRED, CANCELED, TIMEOUT, BUSY, CLOSED, UNSUPPORTED, FAILED
}

/**
 * Token 只交给宿主后端换票；toString 不输出 token、carrier 或 SDK content。
 * @property status 组件归一结果，TOKEN 必须携带非空凭据；其他状态禁止携带 token。
 * @property vendorCode 厂商结果码，组件自有状态默认 0。
 * @property token 短期敏感换票凭据，默认 null；禁止日志或持久化。
 * @property carrier 厂商运营商标识，默认 null；不代表手机号或登录用户。
 */
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
    /** 同意之后初始化 SDK；成功以 READY 回调，不能打开授权页。 */
    fun initialize(callback: (VerificationResult) -> Unit)
    /** 预取号缓存，不打开页面；成功以 READY 回调。 */
    fun preLogin(callback: (VerificationResult) -> Unit)
    /** 拉起授权页；opened 可先于唯一终态回调，Token 仅在 TOKEN 状态返回。 */
    fun authenticate(opened: () -> Unit = {}, callback: (VerificationResult) -> Unit)
    /** 结束当前尝试并屏蔽迟到事件；厂商底层请求可能继续执行。 */
    fun cancel()
    /** 撤销宿主同意时清除厂商预取号缓存；仅操作当前实例拥有的 SDK。 */
    fun clearPreLoginCache()
    /** 永久结束实例，释放页面和回调所有权；不能重新认证。 */
    fun close()
}

/**
 * 每个进程保持一个 client；状态与事件依赖宿主提供的串行 dispatcher，重入返回 BUSY。
 * @param driver 本实例使用并在 close 时释放的原生驱动。
 * @param dispatcher 必须为 Main、单线程或 Kuikly 页面 dispatcher；不支持并发 Default/IO dispatcher，Android/iOS 默认 Main。
 * @param prepareTimeoutMillis 单次初始化/预取号超时，毫秒，默认 10000，必须为正数。
 * @param authenticationTimeoutMillis 授权等待超时，毫秒，默认 120000，必须为正数。
 */
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

    /** 同意后初始化并预取号；未同意会撤销在途认证和缓存，调用协程取消会传播取消。 */
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

    /** 主动取消在途尝试，等待者返回 CANCELED；之后可在重新同意后重试。 */
    suspend fun cancel() = withContext(dispatcher) {
        generation++
        pending?.complete(VerificationResult(VerificationStatus.CANCELED))
        driver.cancel()
    }

    /** 撤销同意并清除缓存，取消前后均不交付旧 Token。 */
    suspend fun revokeConsent() = withContext(dispatcher) {
        consent = false
        cancel()
        driver.clearPreLoginCache()
    }

    /** 幂等永久关闭；清理在 NonCancellable 中执行，不被调用方取消跳过。 */
    suspend fun close() = withContext(dispatcher + NonCancellable) {
        if (!closed) {
            closed = true
            revokeConsent()
            driver.close()
        }
    }
}
