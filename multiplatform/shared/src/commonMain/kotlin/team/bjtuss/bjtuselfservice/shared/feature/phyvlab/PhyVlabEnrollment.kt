package team.bjtuss.bjtuselfservice.shared.feature.phyvlab

import team.bjtuss.bjtuselfservice.shared.domain.phyvlab.PhyVlabActivity
import team.bjtuss.bjtuselfservice.shared.feature.physicslab.PhysicsLab

/**
 * 物理在线“物理实验”课把本学期全部实验都列成作业，学生只需交自己在 wlsy 约上的那几个。
 * 作业标题形如 `34 光纤特性和光信号传输`、`37，38 万用表设计（难度系数1.1）`；
 * 约课结果是 `光纤特性和光信号传输`。两边归一化后比较。
 */
fun isPhysicsExperimentCourse(courseName: String): Boolean =
    "物理实验" in courseName && "演示" !in courseName

/** 去掉开头序号（含“37，38”这类多个序号）和结尾括号段，统一全半角并去空白。 */
fun normalizeExperimentName(raw: String): String {
    var text = raw.map(::toHalfWidth).joinToString("").trim()
    text = text.replace(LEADING_NUMBERS, "")
    while (true) {
        val trimmed = text.replace(TRAILING_BRACKET, "").trim()
        if (trimmed == text) break
        text = trimmed
    }
    return text.replace(WHITESPACE, "")
}

/** 返回匹配到的约课实验名；对不上返回 null。先比完全相等，再比互相包含（至少 4 个字，避免误配）。 */
fun matchPhysicsLabBooking(activityTitle: String, bookings: List<PhysicsLab>): String? {
    val title = normalizeExperimentName(activityTitle)
    if (title.isEmpty()) return null
    val names = bookings.map { it.name to normalizeExperimentName(it.name) }.filter { it.second.isNotEmpty() }
    names.firstOrNull { it.second == title }?.let { return it.first }
    return names.firstOrNull { (_, name) ->
        minOf(name.length, title.length) >= 4 && (name in title || title in name)
    }?.first
}

/** 物理在线作业的“是否属于我”判定结果。 */
enum class PhyVlabEnrollment {
    /** 不是物理实验课（大学物理、演示实验等），照常算。 */
    NOT_APPLICABLE,
    /** 物理实验课，且对上了约课结果。 */
    BOOKED,
    /** 物理实验课，约课结果可用但没对上：不算待做。 */
    NOT_BOOKED,
    /** 物理实验课，但没有约课缓存（未配置 wlsy 或未同步）：无法判断。 */
    UNVERIFIED,
}

data class PhyVlabEnrollmentResult(val enrollment: PhyVlabEnrollment, val matchedBooking: String? = null)

/** [bookings] 为 null 表示约课缓存不可用。 */
fun phyVlabEnrollment(activity: PhyVlabActivity, bookings: List<PhysicsLab>?): PhyVlabEnrollmentResult {
    if (!isPhysicsExperimentCourse(activity.courseName)) return PhyVlabEnrollmentResult(PhyVlabEnrollment.NOT_APPLICABLE)
    if (bookings.isNullOrEmpty()) return PhyVlabEnrollmentResult(PhyVlabEnrollment.UNVERIFIED)
    val matched = matchPhysicsLabBooking(activity.title, bookings)
    return if (matched != null) PhyVlabEnrollmentResult(PhyVlabEnrollment.BOOKED, matched)
    else PhyVlabEnrollmentResult(PhyVlabEnrollment.NOT_BOOKED)
}

private val LEADING_NUMBERS = Regex("^\\d+(\\s*[,、]\\s*\\d+)*\\s*[.、]?\\s*")
private val TRAILING_BRACKET = Regex("\\s*[(\\[][^()\\[\\]]*[)\\]]\\s*$")
private val WHITESPACE = Regex("\\s+")

private fun toHalfWidth(char: Char): Char = when (char) {
    '　' -> ' '
    in '！'..'～' -> (char.code - 0xFEE0).toChar()
    '【' -> '['
    '】' -> ']'
    else -> char
}
