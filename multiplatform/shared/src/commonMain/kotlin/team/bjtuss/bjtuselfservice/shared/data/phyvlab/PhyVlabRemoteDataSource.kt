package team.bjtuss.bjtuselfservice.shared.data.phyvlab

import team.bjtuss.bjtuselfservice.shared.network.SchoolEndpoints

import kotlinx.coroutines.CancellationException
import com.fleeksoft.ksoup.Ksoup
import team.bjtuss.bjtuselfservice.shared.domain.phyvlab.PhyVlabActivity
import team.bjtuss.bjtuselfservice.shared.domain.phyvlab.PhyVlabAssignmentDetail
import team.bjtuss.bjtuselfservice.shared.domain.phyvlab.PhyVlabCourse
import team.bjtuss.bjtuselfservice.shared.domain.phyvlab.PhyVlabEvent
import team.bjtuss.bjtuselfservice.shared.domain.homework.HomeworkFileContent
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpMethod
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpRequest
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpResponse
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpTransport
import team.bjtuss.bjtuselfservice.shared.network.parseOffMain

private val PHYVLAB_COURSES_URL = "${SchoolEndpoints.PHYVLAB_ORIGIN}/my/courses.php"
private val PHYVLAB_COURSE_VIEW_URL = "${SchoolEndpoints.PHYVLAB_ORIGIN}/course/view.php"
private val PHYVLAB_CALENDAR_URL = "${SchoolEndpoints.PHYVLAB_ORIGIN}/calendar/view.php"

enum class PhyVlabRemoteFailure {
    NETWORK,
    PARSE,
    SESSION_EXPIRED,
}

class PhyVlabRemoteException(
    val reason: PhyVlabRemoteFailure,
) : Exception("PhyVlab request failed: ${reason.name}")

interface PhyVlabRemoteDataSource {
    suspend fun fetchCourses(): List<PhyVlabCourse>
    suspend fun fetchCourseActivities(course: PhyVlabCourse): List<PhyVlabActivity>
    suspend fun fetchEvents(monthTimestampSeconds: Long): List<PhyVlabEvent>
    suspend fun fetchAssignmentDetail(activity: PhyVlabActivity): PhyVlabAssignmentDetail
    suspend fun submitAssignment(activity: PhyVlabActivity, files: List<HomeworkFileContent>)
    suspend fun saveAssignment(activity: PhyVlabActivity, files: List<HomeworkFileContent>, removed: Set<String>) {
        if (removed.isNotEmpty()) throw PhyVlabRemoteException(PhyVlabRemoteFailure.PARSE)
        submitAssignment(activity, files)
    }
    suspend fun finalizationStatement(activity: PhyVlabActivity): String? = throw PhyVlabRemoteException(PhyVlabRemoteFailure.PARSE)
    suspend fun finalizeAssignment(activity: PhyVlabActivity, acceptedStatement: String?) { throw PhyVlabRemoteException(PhyVlabRemoteFailure.PARSE) }
}

/**
 * 物理在线只读数据源。只访问 HTTPS 的 phyvlab 域名；登录会话复用 App 的
 * CAS/Ktor Cookie jar，未登录时服务器会跳回登录入口，按会话失效处理。
 */
class SchoolPhyVlabRemoteDataSource(
    private val transport: SchoolHttpTransport,
) : PhyVlabRemoteDataSource {
    private val assignmentClient = team.bjtuss.bjtuselfservice.shared.data.moodle.MoodleAssignmentClient(
        SchoolEndpoints.PHYVLAB_ORIGIN,
        read = { fetchPage(it, SchoolEndpoints.PHYVLAB_ORIGIN) }, write = { transport.executeWithoutRedirects(it) })
    override suspend fun saveAssignment(activity: PhyVlabActivity, files: List<HomeworkFileContent>, removed: Set<String>) {
        assignmentClient.saveFiles(activity.id, files, removed)
    }
    override suspend fun finalizationStatement(activity: PhyVlabActivity) = assignmentClient.finalizationStatement(activity.id)
    override suspend fun finalizeAssignment(activity: PhyVlabActivity, acceptedStatement: String?) { assignmentClient.finalize(activity.id, acceptedStatement) }

    override suspend fun fetchCourses(): List<PhyVlabCourse> {
        val response = fetchPage(PHYVLAB_COURSES_URL, referer = "${SchoolEndpoints.PHYVLAB_ORIGIN}/?redirect=0")
        return when (val parsed = parseOffMain { parsePhyVlabCourses(response.bodyText()) }) {
            is PhyVlabParseResult.Failure -> parse()
            is PhyVlabParseResult.Success -> parsed.value.also {
                phyVlabDebug("courses parsed count=${it.size}")
            }
        }
    }

    override suspend fun fetchCourseActivities(course: PhyVlabCourse): List<PhyVlabActivity> {
        val response = fetchPage(
            url = "$PHYVLAB_COURSE_VIEW_URL?id=${course.id}",
            referer = course.courseUrl,
        )
        return when (val parsed = parseOffMain { parsePhyVlabActivities(response.bodyText(), course.id, course.name) }) {
            is PhyVlabParseResult.Failure -> parse()
            is PhyVlabParseResult.Success -> parsed.value
        }
    }

    override suspend fun fetchEvents(monthTimestampSeconds: Long): List<PhyVlabEvent> {
        val response = fetchPage(
            url = "$PHYVLAB_CALENDAR_URL?view=month&time=$monthTimestampSeconds",
            referer = PHYVLAB_CALENDAR_URL,
        )
        return when (val parsed = parseOffMain { parsePhyVlabEvents(response.bodyText()) }) {
            is PhyVlabParseResult.Failure -> parse()
            is PhyVlabParseResult.Success -> parsed.value
        }
    }

    override suspend fun fetchAssignmentDetail(activity: PhyVlabActivity): PhyVlabAssignmentDetail {
        val response = fetchPage(activity.activityUrl, referer = "${SchoolEndpoints.PHYVLAB_ORIGIN}/course/view.php?id=${activity.courseId}")
        return when (val parsed = parseOffMain { parsePhyVlabAssignmentPage(response.bodyText(), activity) }) {
            is PhyVlabParseResult.Failure -> parse()
            is PhyVlabParseResult.Success -> {
                var page = parsed.value

                // Moodle 默认详情页只给“编辑提交”链接，真正的 filemanager 草稿上下文
                // 在编辑页生成；该 GET 仍是只读，不会改变提交状态。
                if (page.submissionContext == null) {
                    // 主题可能把“添加/编辑提交”渲染成无 href 的按钮；Moodle
                    // 的标准编辑入口仍是该活动 id + action=editsubmission。
                    val editUrl = page.editSubmissionUrl
                        ?: "${SchoolEndpoints.PHYVLAB_ORIGIN}/mod/assign/view.php?id=${activity.id}&action=editsubmission"
                    val editResponse = try {
                        fetchPage(editUrl, referer = activity.activityUrl)
                    } catch (error: PhyVlabRemoteException) {
                        // 编辑页只是为了补充原生上传所需的 filemanager 上下文；
                        // 某些 Moodle 主题/作业状态会让该入口返回 404。主详情页
                        // 已经成功时不能把这个可选请求的失败升级成详情失败。
                        // 会话失效则必须继续向上抛出，交给统一恢复逻辑处理。
                        if (error.reason == PhyVlabRemoteFailure.SESSION_EXPIRED) throw error
                        phyVlabDebug("optional edit page unavailable reason=${error.reason}")
                        null
                    }
                    editResponse?.let { response ->
                        when (val editPage = parsePhyVlabAssignmentPage(response.bodyText(), activity)) {
                            is PhyVlabParseResult.Failure -> Unit
                            is PhyVlabParseResult.Success -> {
                                page = editPage.value.copy(
                                    detail = mergeAssignmentDetails(page.detail, editPage.value.detail),
                                )

                            }
                        }
                    }
                }
                phyVlabDebug("assignment detail ready canSubmit=${page.detail.canSubmit}")
                page.detail
            }
        }
    }

    private fun mergeAssignmentDetails(
        original: PhyVlabAssignmentDetail,
        secondary: PhyVlabAssignmentDetail,
    ): PhyVlabAssignmentDetail = secondary.copy(
        description = secondary.description.ifBlank { original.description },
        submissionStatus = secondary.submissionStatus.ifBlank { original.submissionStatus },
        submissionDateText = secondary.submissionDateText ?: original.submissionDateText,
        submissionDateTimestamp = secondary.submissionDateTimestamp ?: original.submissionDateTimestamp,
        gradingStatus = secondary.gradingStatus ?: original.gradingStatus,
        gradeText = secondary.gradeText ?: original.gradeText,
        feedbackText = secondary.feedbackText ?: original.feedbackText,
        submittedFiles = secondary.submittedFiles.ifEmpty { original.submittedFiles },
        canSubmit = secondary.canSubmit || original.canSubmit,
        canFinalize = secondary.canFinalize || original.canFinalize,
        isDraft = secondary.isDraft || original.isDraft,
    )

    override suspend fun submitAssignment(activity: PhyVlabActivity, files: List<HomeworkFileContent>) {
        saveAssignment(activity, files, emptySet())
    }
    private suspend fun fetchPage(url: String, referer: String): SchoolHttpResponse {
        phyVlabDebug("page GET start ${safePhyVlabEndpoint(url)}")
        val response = execute(
            SchoolHttpRequest(
                method = SchoolHttpMethod.GET,
                url = url,
                headers = mapOf(
                    "Accept" to "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8",
                    "Referer" to referer,
                ),
            ),
        )
        phyVlabDebug(
            "page GET ${safePhyVlabEndpoint(url)} -> ${response.statusCode} " +
                "${safePhyVlabEndpoint(response.finalUrl)} bytes=${response.body.size}",
        )
        if (response.statusCode !in 200..299) network()
        if (!response.finalUrl.startsWith(SchoolEndpoints.PHYVLAB_ORIGIN)) sessionExpired()
        if (response.finalUrl.contains("/login/index.php") ||
            response.finalUrl.contains("/enrol/index.php")
        ) {
            sessionExpired()
        }
        if (response.body.isEmpty()) parse()
        // 某些反向代理会在原请求地址直接返回登录页（不发生 HTTP 跳转）。
        // 不能把这类页面误判成“已登录但没有课程”。
        if (parseOffMain { looksLikePhyVlabLoginPage(response.bodyText()) }) {
            phyVlabDebug("page classified as login")
            sessionExpired()
        }
        return response
    }

    private suspend fun execute(request: SchoolHttpRequest): SchoolHttpResponse = try {
        transport.execute(request)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        val causeType = error.cause?.let { it::class.simpleName }
        phyVlabDebug(
            "transport failed method=${request.method} endpoint=${safePhyVlabEndpoint(request.url)} " +
                "error=${error::class.simpleName ?: "unknown"} cause=${causeType ?: "none"}",
        )
        network()
    }
}

private fun network(): Nothing = throw PhyVlabRemoteException(PhyVlabRemoteFailure.NETWORK)
private fun parse(): Nothing = throw PhyVlabRemoteException(PhyVlabRemoteFailure.PARSE)
private fun sessionExpired(): Nothing = throw PhyVlabRemoteException(PhyVlabRemoteFailure.SESSION_EXPIRED)

private fun looksLikePhyVlabLoginPage(html: String): Boolean {
    val document = Ksoup.parse(html)
    return document.selectFirst("a[href*='/auth/oauth2/login.php']") != null ||
        document.selectFirst("form[action*='/login/index.php']") != null ||
        (document.text().contains("用户名或邮箱") && document.text().contains("密码"))
}
