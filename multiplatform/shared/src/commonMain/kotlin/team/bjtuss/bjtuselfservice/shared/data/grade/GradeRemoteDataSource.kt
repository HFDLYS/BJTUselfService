package team.bjtuss.bjtuselfservice.shared.data.grade

import team.bjtuss.bjtuselfservice.shared.network.SchoolEndpoints

import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import team.bjtuss.bjtuselfservice.shared.domain.grade.Grade
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpMethod
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpRequest
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpTransport
import team.bjtuss.bjtuselfservice.shared.network.looksLikeSessionExpired
import team.bjtuss.bjtuselfservice.shared.network.parseOffMain

private const val AA_GRADE_URL =
    "https://aa.bjtu.edu.cn/score/scores/stu/view/?page=1&perpage=500&ctype="

enum class GradeRemoteFailure {
    NETWORK,
    SESSION_EXPIRED,
    MALFORMED_RESPONSE,
}

class GradeRemoteException(
    val reason: GradeRemoteFailure,
) : Exception("Unable to refresh grades: ${reason.name}")

interface GradeRemoteDataSource {
    suspend fun fetchGrades(): List<Grade>
}

/**
 * 复用登录 transport 的 Cookie 会话。两类成绩必须全部成功，避免半份快照覆盖完整缓存。
 */
class SchoolGradeRemoteDataSource(
    private val transport: SchoolHttpTransport,
    private val requestDelayMillis: Long = 100,
) : GradeRemoteDataSource {
    override suspend fun fetchGrades(): List<Grade> {
        val allGrades = mutableListOf<Grade>()
        listOf("ln", "lr").forEach { courseType ->
            if (requestDelayMillis > 0) delay(requestDelayMillis)
            val response = try {
                transport.execute(
                    SchoolHttpRequest(
                        method = SchoolHttpMethod.GET,
                        url = AA_GRADE_URL + courseType,
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
            when (val parsed = parseOffMain { parseGradeTable(response.bodyText()) }) {
                is GradeTableParseResult.Failure -> {
                    throw GradeRemoteException(GradeRemoteFailure.MALFORMED_RESPONSE)
                }
                is GradeTableParseResult.Success -> allGrades += parsed.grades
            }
        }
        return allGrades.distinctBy { grade ->
            Triple(grade.courseName, grade.courseScore, grade.courseCredits)
        }
    }
}
