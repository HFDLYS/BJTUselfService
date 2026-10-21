package team.bjtuss.bjtuselfservice.repository

import java.io.IOException
import team.bjtuss.bjtuselfservice.jsonclass.Course
import team.bjtuss.bjtuselfservice.jsonclass.CourseJsonType

/** An authenticated response may contain no courses; a failed request must not erase caches. */
internal fun coursesFromResponse(response: CourseJsonType?): List<Course> {
    if (response == null) {
        throw IOException("课程平台尚未连接，请重新登录后重试")
    }
    if (response.STATUS != "0") {
        throw IOException("获取课程列表失败，请稍后重试")
    }
    return response.courseList ?: throw IOException("课程列表响应不完整")
}
