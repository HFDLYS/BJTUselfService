package team.bjtuss.bjtuselfservice.shared.feature.course

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import team.bjtuss.bjtuselfservice.shared.domain.course.Course
import team.bjtuss.bjtuselfservice.shared.domain.grade.CourseType
import team.bjtuss.bjtuselfservice.shared.domain.grade.courseTypeForCourseName
import team.bjtuss.bjtuselfservice.shared.feature.grade.GradeTypeColors

/**
 * 课程配色的唯一来源：按课程性质（必修/限选/任选/体育/未知）与特殊安排（实验/考试）分类，
 * 课程表色块、首页卡片与日程、iOS 小组件都从这里取色，不再按课程名哈希。
 *
 * [widgetKind] 写进小组件快照，Swift 侧按它查同一张表（CourseScheduleWidget/WeekCourseWidget.swift）。
 */
enum class CourseColorCategory(val widgetKind: String) {
    REQUIRED("required"),
    LIMITED("limited"),
    ELECTIVE("elective"),
    PHYSICAL_EDUCATION("pe"),
    UNKNOWN("unknown"),
    LAB("lab"),
    EXAM("exam"),
}

/**
 * 一类课程的色值（0xRRGGBB）。container/content 是课程表色块的底色与文字/描边色，
 * 浅色深色各一套；accent 是色条、图标、进行中描边用的强调色，两种主题下都够醒目。
 */
internal data class CourseCategoryTones(
    val lightContainer: Long,
    val lightContent: Long,
    val darkContainer: Long,
    val darkContent: Long,
    val accent: Long,
)

internal val CourseColorCategory.tones: CourseCategoryTones
    get() = when (this) {
        CourseColorCategory.REQUIRED -> CourseCategoryTones(0xF6D2D0, 0x7D1F1A, 0x4A1F1D, 0xF2B9B4, 0xD9574E)
        // 课程表里限选用玫红，和必修的砖红分开；成绩页的限选仍用琥珀（见 GradeTypeColors）。
        CourseColorCategory.LIMITED -> CourseCategoryTones(0xF4D7E6, 0x9C3F70, 0x48243A, 0xF0B2D2, 0xC9558A)
        CourseColorCategory.ELECTIVE -> CourseCategoryTones(0xDCE4FC, 0x2F4DB5, 0x223065, 0xC3CDFA, 0x4F6FD8)
        // 体育用青绿：深浅两套都由 accent 0x16A4A4 推出。
        CourseColorCategory.PHYSICAL_EDUCATION -> CourseCategoryTones(0xD3F0EE, 0x0E6B69, 0x163B3B, 0x9EDDD9, 0x16A4A4)
        CourseColorCategory.UNKNOWN -> CourseCategoryTones(0xE7E7E9, 0x5F6368, 0x333438, 0xC6C7CB, 0x7D828A)
        CourseColorCategory.LAB -> CourseCategoryTones(0xEADBFA, 0x64368E, 0x3B2858, 0xDEC6F6, 0x9B6BCF)
        CourseColorCategory.EXAM -> CourseCategoryTones(0xFFE6C8, 0xAF641C, 0x3D2A12, 0xEAA04B, 0xE0952F)
    }

internal fun CourseType.colorCategory(): CourseColorCategory = when (this) {
    CourseType.REQUIRED -> CourseColorCategory.REQUIRED
    CourseType.LIMITED -> CourseColorCategory.LIMITED
    CourseType.ELECTIVE -> CourseColorCategory.ELECTIVE
    CourseType.PHYSICAL_EDUCATION -> CourseColorCategory.PHYSICAL_EDUCATION
    CourseType.UNKNOWN -> CourseColorCategory.UNKNOWN
}

/** 课程或日程事件属于哪一类：实验/考试按事件种类，普通课程按培养方案里的课程性质。 */
internal fun courseColorCategory(course: Course, courseTypesByCode: Map<String, CourseType>?): CourseColorCategory =
    when (course.scheduleEventKind) {
        "physicslab", "phydemo" -> CourseColorCategory.LAB
        "exam" -> CourseColorCategory.EXAM
        else -> courseTypesByCode?.let { courseTypeForCourseName(course.courseId, it).colorCategory() }
            ?: CourseColorCategory.UNKNOWN
    }

private fun rgb(value: Long): Color = Color(0xFF000000 or value)

/** 课程表色块同款底色与文字色，随当前主题深浅切换。 */
@Composable
internal fun courseCategoryColors(category: CourseColorCategory): GradeTypeColors {
    val tones = category.tones
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (dark) GradeTypeColors(rgb(tones.darkContainer), rgb(tones.darkContent))
    else GradeTypeColors(rgb(tones.lightContainer), rgb(tones.lightContent))
}

/** 色条、图标、进行中描边用的强调色。 */
internal fun courseCategoryAccent(category: CourseColorCategory): Color = rgb(category.tones.accent)
