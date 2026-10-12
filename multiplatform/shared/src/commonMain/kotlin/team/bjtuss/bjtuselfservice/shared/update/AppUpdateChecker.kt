package team.bjtuss.bjtuselfservice.shared.update

import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpMethod
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpRequest
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpTransport

/**
 * KMP 三端更新检测：对齐原安卓 [GitRepository] 的「获取最新 Release → 比较版本 →
 * 弹窗 → 跳转 GitHub 发布页」流程，但指向本 fork 仓库，并显式支持 pre-release。
 *
 * 与原版的差异：原版用 `releases/latest`（GitHub 不返回 pre-release），本应用
 * pre-release 阶段改用 `releases` 列表取第一条（最新创建，含 pre-release）。
 */
object AppUpdateChecker {

    const val REPO = "JasonZhang1225/BJTUselfService-KMP-Refreshed"

    /** 当前构建版本。与 androidApp versionName / 设置页展示一致。 */
    const val CURRENT_VERSION = "1.8.4-Alpha-phydemo-fix2"

    private const val RELEASES_URL = "https://api.github.com/repos/$REPO/releases"

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class Release(
        @SerialName("tag_name") val tagName: String,
        val name: String? = null,
        val body: String? = null,
        @SerialName("html_url") val htmlUrl: String,
        @SerialName("published_at") val publishedAt: String? = null,
        val prerelease: Boolean = false,
        val draft: Boolean = false,
    )

    sealed interface Result {
        /** 成功获取到最新 Release（无论是否更新）。 */
        data class Success(val release: Release) : Result

        /** 网络失败或响应不可解析。 */
        data object Unavailable : Result
    }

    /**
     * 拉取最新 Release（含 pre-release，跳过 draft）。网络异常与非 2xx 都归为
     * [Result.Unavailable]，不向外抛，调用方据此提示「检查失败」。
     */
    suspend fun fetchLatest(transport: SchoolHttpTransport): Result = withContext(Dispatchers.Default) {
        val response = runCatching {
            transport.executePublic(
                SchoolHttpRequest(
                    method = SchoolHttpMethod.GET,
                    url = RELEASES_URL,
                    headers = mapOf("Accept" to "application/vnd.github+json"),
                ),
            )
        }.getOrNull() ?: return@withContext Result.Unavailable
        if (response.statusCode !in 200..299) return@withContext Result.Unavailable
        val releases = runCatching {
            json.decodeFromString<List<Release>>(response.bodyText())
        }.getOrNull() ?: return@withContext Result.Unavailable
        val latest = releases.firstOrNull { !it.draft } ?: return@withContext Result.Unavailable
        Result.Success(latest)
    }

    /** 是否有更新：远端 tag 规范比较后严格高于 [CURRENT_VERSION]。 */
    fun isNewer(release: Release, current: String = CURRENT_VERSION): Boolean =
        compareVersions(release.tagName, current) > 0

    /**
     * 语义化版本比较：忽略 `v` 前缀，逐段比较点号数字；数字段相同则按后缀
     *（如 `-KMP`）做字典序。与原版 `String.compareTo` 相比能正确处理
     * `1.7.10` vs `1.7.9` 这类数字大小。
     *
     * @return 负数 a 旧于 b；0 相等；正数 a 新于 b。
     */
    fun compareVersions(a: String, b: String): Int {
        fun parts(v: String): Pair<List<Int>, String> {
            val noV = v.removePrefix("v")
            val core = noV.substringBefore('-')
            val suffix = noV.substringAfter('-', "")
            val nums = core.split('.').map { it.toIntOrNull() ?: 0 }
            return nums to suffix
        }
        val (numsA, suffixA) = parts(a)
        val (numsB, suffixB) = parts(b)
        val len = min(numsA.size, numsB.size)
        for (i in 0 until len) {
            if (numsA[i] != numsB[i]) return numsA[i].compareTo(numsB[i])
        }
        if (numsA.size != numsB.size) return numsA.size.compareTo(numsB.size)
        return suffixA.compareTo(suffixB)
    }
}
