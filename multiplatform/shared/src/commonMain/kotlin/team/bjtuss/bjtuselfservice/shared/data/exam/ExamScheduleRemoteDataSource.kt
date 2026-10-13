package team.bjtuss.bjtuselfservice.shared.data.exam

import team.bjtuss.bjtuselfservice.shared.network.SchoolEndpoints

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import team.bjtuss.bjtuselfservice.shared.domain.exam.ExamSchedule
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpMethod
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpRequest
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpTransport
import team.bjtuss.bjtuselfservice.shared.network.looksLikeSessionExpired
import team.bjtuss.bjtuselfservice.shared.network.parseOffMain

private const val EXAM_URL = "https://aa.bjtu.edu.cn/examine/examplanstudent/stulist/"

enum class ExamScheduleRemoteFailure {
    NETWORK,
    SESSION_EXPIRED,
    MALFORMED_RESPONSE,
}

class ExamScheduleRemoteException(
    val reason: ExamScheduleRemoteFailure,
) : Exception("Unable to refresh exams: ${reason.name}")

interface ExamScheduleRemoteDataSource {
    suspend fun fetchExams(): List<ExamSchedule>
}

class SchoolExamScheduleRemoteDataSource(
    private val transport: SchoolHttpTransport,
    private val requestDelayMillis: Long = 100,
) : ExamScheduleRemoteDataSource {
    override suspend fun fetchExams(): List<ExamSchedule> {
        val response = try {
            if (requestDelayMillis > 0) delay(requestDelayMillis)
            transport.execute(
                SchoolHttpRequest(
                    method = SchoolHttpMethod.GET,
                    url = EXAM_URL,
                    headers = mapOf("Host" to "aa.bjtu.edu.cn"),
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            throw ExamScheduleRemoteException(ExamScheduleRemoteFailure.NETWORK)
        }
        if (response.statusCode !in 200..299) {
            throw ExamScheduleRemoteException(ExamScheduleRemoteFailure.NETWORK)
        }
        if (parseOffMain { response.looksLikeSessionExpired() } || !response.finalUrl.startsWith(SchoolEndpoints.AA_ORIGIN)) {
            throw ExamScheduleRemoteException(ExamScheduleRemoteFailure.SESSION_EXPIRED)
        }
        return when (val parsed = parseOffMain { parseExamScheduleTable(response.bodyText()) }) {
            is ExamScheduleParseResult.Failure -> {
                throw ExamScheduleRemoteException(ExamScheduleRemoteFailure.MALFORMED_RESPONSE)
            }
            is ExamScheduleParseResult.Success -> parsed.exams
        }
    }
}
