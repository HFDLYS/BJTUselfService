package team.bjtuss.bjtuselfservice.shared.data.classroomoccupancy

import team.bjtuss.bjtuselfservice.shared.network.SchoolEndpoints

import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import team.bjtuss.bjtuselfservice.shared.domain.classroomoccupancy.ClassroomOccupancy
import team.bjtuss.bjtuselfservice.shared.domain.classroomoccupancy.OccupancySemester
import team.bjtuss.bjtuselfservice.shared.domain.classroomoccupancy.OccupancyWeekDate
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpMethod
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpRequest
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpResponse
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpTransport
import team.bjtuss.bjtuselfservice.shared.network.looksLikeSessionExpired
import team.bjtuss.bjtuselfservice.shared.network.parseOffMain

private const val CALENDAR_PAGE_URL = "https://bksy.bjtu.edu.cn/Admin/SemesterTranPage.aspx?noRemark=1"

/**
 * 校历页（bksy 公开页）请求的独立超时。bksy 与 aa 不同域，走代理线路时可能
 * 慢/挂起；共享 transport 的全局请求锁会因此堵住所有 aa 查询（实测 iOS 开代理
 * 时教学楼占用一直转圈），必须显式短超时兜底——Ktor 默认 30s 超时对
 * 「连接建立但无数据」的挂起不一定生效。
 */
private const val WEEK_DATES_TIMEOUT_MILLIS = 6_000L

enum class ClassroomOccupancyRemoteFailure {
    NETWORK,
    SESSION_EXPIRED,
    MALFORMED_RESPONSE,
}

class ClassroomOccupancyRemoteException(
    val reason: ClassroomOccupancyRemoteFailure,
) : Exception("Unable to query classroom occupancy: ${reason.name}")

/** room_view 一次请求的结果：教室占用 + 同页学期下拉（能解析则带上）。 */
data class ClassroomOccupancyPage(
    val rooms: List<ClassroomOccupancy>,
    val semesterOptions: SemesterOptions? = null,
)

interface ClassroomOccupancyRemoteDataSource {
    /**
     * [buildingId] 为教务 jxlh 教学楼数字 ID（见 OCCUPANCY_BUILDINGS），不能传楼名；
     * [semesterId] 为 zxjxjhh 学期 value（如 `2025-2026-2-2`），null = 当前学期（省略参数）。
     */
    suspend fun fetchOccupancy(week: Int, buildingId: String, semesterId: String? = null): ClassroomOccupancyPage

    /** 学期下拉（zxjxjhh options）；解析失败抛 MALFORMED_RESPONSE。 */
    suspend fun fetchSemesters(): SemesterOptions

    /**
     * 校历周日期（bksy 公开页，不在 aa 会话下）；日期只是 UI 参考，
     * 任何失败都返回空 Map 而非抛错。
     */
    suspend fun fetchWeekDates(): Map<String, List<OccupancyWeekDate>>
}

/**
 * 通过共享 transport 访问教务教室使用查询（room_view）与教务处校历页（bksy）。
 * room_view 的会话失效判定与考试一致：finalUrl 不在 aa.bjtu.edu.cn 下即被
 * 重定向回登录页；bksy 为公开页面，不做会话校验。
 */
class SchoolClassroomOccupancyRemoteDataSource(
    private val transport: SchoolHttpTransport,
    private val requestDelayMillis: Long = 100,
) : ClassroomOccupancyRemoteDataSource {

    override suspend fun fetchOccupancy(
        week: Int,
        buildingId: String,
        semesterId: String?,
    ): ClassroomOccupancyPage {
        require(week in 1..53) { "week out of range" }
        val url = buildString {
            append("${SchoolEndpoints.ROOM_VIEW_URL}?zc=$week")
            append("&jxlh=${buildingId.encodeURLParameter()}")
            if (semesterId != null) {
                append("&zxjxjhh=${semesterId.encodeURLParameter()}")
            }
            append("&page=1&perpage=500")
        }
        val response = executeWithAaSession(url)
        val (parsed, semesterOptions) = parseOffMain {
            val body = response.bodyText()
            // 同页自带 zxjxjhh 下拉：顺带解析，单独预取失败时弹层仍能列出学期。
            parseClassroomOccupancyTable(body) to try {
                parseSemesterOptions(body)
            } catch (_: Exception) {
                null
            }
        }
        val rooms = when (parsed) {
            is ClassroomOccupancyParseResult.Failure ->
                throw ClassroomOccupancyRemoteException(ClassroomOccupancyRemoteFailure.MALFORMED_RESPONSE)
            is ClassroomOccupancyParseResult.Success -> parsed.rooms
        }
        return ClassroomOccupancyPage(rooms = rooms, semesterOptions = semesterOptions)
    }

    override suspend fun fetchSemesters(): SemesterOptions {
        // 下拉在列表页本身就渲染出来，按最小参数取一次页面即可。
        val response = executeWithAaSession("${SchoolEndpoints.ROOM_VIEW_URL}?zc=1&jxlh=1&page=1&perpage=5")
        return parseOffMain { parseSemesterOptions(response.bodyText()) }
            ?: throw ClassroomOccupancyRemoteException(ClassroomOccupancyRemoteFailure.MALFORMED_RESPONSE)
    }

    override suspend fun fetchWeekDates(): Map<String, List<OccupancyWeekDate>> {
        val response = try {
            // 校历是 bksy 公开页：走 executePublic（独立客户端、不占会话锁）+ 短超时。
            // 代理下 bksy 挂起时绝不能堵住 aa 的 room_view（切周转圈的根因）。
            withTimeoutOrNull(WEEK_DATES_TIMEOUT_MILLIS) {
                if (requestDelayMillis > 0) delay(requestDelayMillis)
                transport.executePublic(
                    SchoolHttpRequest(
                        method = SchoolHttpMethod.GET,
                        url = CALENDAR_PAGE_URL,
                    ),
                )
            } ?: return emptyMap()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return emptyMap()
        }
        if (response.statusCode !in 200..299) return emptyMap()
        return parseOffMain { parseAcademicWeeks(response.bodyText()) }
    }

    /** room_view 请求公共部分：限速 + 会话校验（bksy 公开页不走这里）。 */
    private suspend fun executeWithAaSession(url: String): SchoolHttpResponse {
        val response = try {
            // 学校有频率限制，沿用各模块 100ms 的请求间隔惯例。
            if (requestDelayMillis > 0) delay(requestDelayMillis)
            transport.execute(
                SchoolHttpRequest(
                    method = SchoolHttpMethod.GET,
                    url = url,
                    headers = mapOf("Host" to "aa.bjtu.edu.cn"),
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            throw ClassroomOccupancyRemoteException(ClassroomOccupancyRemoteFailure.NETWORK)
        }
        if (response.statusCode !in 200..299) {
            throw ClassroomOccupancyRemoteException(ClassroomOccupancyRemoteFailure.NETWORK)
        }
        if (parseOffMain { response.looksLikeSessionExpired() } || !response.finalUrl.startsWith(SchoolEndpoints.AA_ORIGIN)) {
            throw ClassroomOccupancyRemoteException(ClassroomOccupancyRemoteFailure.SESSION_EXPIRED)
        }
        return response
    }
}
