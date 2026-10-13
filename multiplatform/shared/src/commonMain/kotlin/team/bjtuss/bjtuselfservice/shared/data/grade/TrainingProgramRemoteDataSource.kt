package team.bjtuss.bjtuselfservice.shared.data.grade

import team.bjtuss.bjtuselfservice.shared.network.SchoolEndpoints

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import team.bjtuss.bjtuselfservice.shared.domain.grade.CourseType
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpMethod
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpRequest
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpTransport
import team.bjtuss.bjtuselfservice.shared.network.looksLikeSessionExpired
import team.bjtuss.bjtuselfservice.shared.network.parseOffMain

private const val AA_PROGRAM_URL = "https://aa.bjtu.edu.cn/training/training/program/"

interface TrainingProgramRemoteDataSource {
    suspend fun fetchCourseTypes(): Map<String, CourseType>
}

/**
 * 与成绩刷新共用登录 transport 的 Cookie 会话（SESSION_EXPIRED 语义一致）。
 * 方案可能变动，每次手动刷新都整体重抓；辅修学生的多条 stuview 详情页全部抓取合并。
 * 列表页没有详情链接视为页面结构异常，避免空映射覆盖缓存里的旧数据。
 */
class SchoolTrainingProgramRemoteDataSource(
    private val transport: SchoolHttpTransport,
    private val requestDelayMillis: Long = 100,
) : TrainingProgramRemoteDataSource {
    override suspend fun fetchCourseTypes(): Map<String, CourseType> {
        val listResponse = executeGet(AA_PROGRAM_URL)
        val detailUrls = parseOffMain { parseProgramLinks(listResponse.bodyText()) }
        if (detailUrls.isEmpty()) {
            throw GradeRemoteException(GradeRemoteFailure.MALFORMED_RESPONSE)
        }

        val merged = linkedMapOf<String, CourseType>()
        detailUrls.forEach { url ->
            val detailResponse = executeGet(url)
            when (val parsed = parseOffMain { parseProgramCourseTypes(detailResponse.bodyText()) }) {
                is TrainingProgramParseResult.Failure ->
                    throw GradeRemoteException(GradeRemoteFailure.MALFORMED_RESPONSE)
                is TrainingProgramParseResult.Success ->
                    parsed.courseTypes.forEach { (courseId, storedText) ->
                        courseTypeForStoredText(storedText)?.let { merged[courseId] = it }
                    }
            }
        }
        return merged
    }

    private suspend fun executeGet(url: String) = run {
        if (requestDelayMillis > 0) delay(requestDelayMillis)
        val response = try {
            transport.execute(
                SchoolHttpRequest(
                    method = SchoolHttpMethod.GET,
                    url = url,
                    headers = mapOf(
                        "Host" to "aa.bjtu.edu.cn",
                        "Referer" to SchoolEndpoints.AA_HOME_URL,
                    ),
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            throw GradeRemoteException(GradeRemoteFailure.NETWORK)
        }
        if (response.statusCode !in 200..299) {
            throw GradeRemoteException(GradeRemoteFailure.NETWORK)
        }
        if (parseOffMain { response.looksLikeSessionExpired() } || !response.finalUrl.startsWith(SchoolEndpoints.AA_ORIGIN)) {
            throw GradeRemoteException(GradeRemoteFailure.SESSION_EXPIRED)
        }
        response
    }
}
