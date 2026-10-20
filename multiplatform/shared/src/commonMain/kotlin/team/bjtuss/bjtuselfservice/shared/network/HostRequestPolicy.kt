package team.bjtuss.bjtuselfservice.shared.network

import kotlin.time.TimeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/**
 * 每个学校主机的请求节流策略。
 *
 * 教务 aa 有短窗口限流：约 3 秒内超过 8 个请求就返回 HTTP 503（约 600 字节，几十毫秒即回），
 * 按 1 次/秒持续请求不会触发，且 1 秒内恢复（2026-10-10 实网探测）。因此 aa 串行、
 * 控制起始间隔，并对幂等 GET 的 503 退避重试；智慧教学可承受更高并发。
 */
data class HostRequestPolicy(
    val maxConcurrent: Int,
    val minStartIntervalMillis: Long = 0,
    val throttleRetryDelaysMillis: List<Long> = emptyList(),
) {
    init {
        require(maxConcurrent >= 1)
        require(minStartIntervalMillis >= 0)
    }
}

internal const val AA_HOST = "aa.bjtu.edu.cn"
internal const val HTTP_SERVICE_UNAVAILABLE = 503

fun defaultHostRequestPolicy(host: String): HostRequestPolicy = when (host) {
    AA_HOST -> HostRequestPolicy(
        maxConcurrent = 1,
        minStartIntervalMillis = 400,
        throttleRetryDelaysMillis = listOf(1_000, 2_000, 4_000),
    )
    "cas.bjtu.edu.cn", "sso.bjtu.edu.cn" -> HostRequestPolicy(maxConcurrent = 1)
    // 智慧教学（作业/课件）：原 Android 由 OkHttp 默认每主机 5 并发。
    "123.121.147.7", "bksycenter.bjtu.edu.cn" -> HostRequestPolicy(maxConcurrent = 5)
    else -> HostRequestPolicy(maxConcurrent = 2)
}

/** 单个主机的并发闸门 + 起始间隔 + 503 退避；退避期间继续占用许可，让同主机其他请求一起等待。 */
internal class HostRequestGate(private val policy: HostRequestPolicy) {
    private val permits = Semaphore(policy.maxConcurrent)
    private val spacing = Mutex()
    private var lastStart: TimeSource.Monotonic.ValueTimeMark? = null

    suspend fun run(
        request: SchoolHttpRequest,
        execute: suspend () -> SchoolHttpResponse,
    ): SchoolHttpResponse = permits.withPermit {
        var response = spaced(execute)
        if (request.method != SchoolHttpMethod.GET) return@withPermit response
        for (backoff in policy.throttleRetryDelaysMillis) {
            if (response.statusCode != HTTP_SERVICE_UNAVAILABLE) break
            delay(backoff)
            response = spaced(execute)
        }
        response
    }

    private suspend fun spaced(execute: suspend () -> SchoolHttpResponse): SchoolHttpResponse {
        if (policy.minStartIntervalMillis > 0) {
            spacing.withLock {
                val elapsed = lastStart?.elapsedNow()?.inWholeMilliseconds
                if (elapsed != null && elapsed < policy.minStartIntervalMillis) {
                    delay(policy.minStartIntervalMillis - elapsed)
                }
                lastStart = TimeSource.Monotonic.markNow()
            }
        }
        return execute()
    }
}
