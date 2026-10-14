package team.bjtuss.bjtuselfservice.shared.domain.course

import team.bjtuss.bjtuselfservice.shared.domain.classroomoccupancy.SLOT_TIME_RANGES

/**
 * 第二大节错峰：教室在逸夫教学楼（YF）或思源西楼（SX）时，10:10–12:00 整体推迟到 10:30–12:20。
 * 只改时间，不新增节次；课表网格仍画在第二节，只是色块略微下移。
 */
const val STAGGERED_SLOT_INDEX = 1
const val STAGGERED_SHIFT_MINUTES = 20

/** 只认海淀主校区这两栋楼的楼名或教室号前缀；“思源楼/思源东楼”、威海“逸夫楼”不算。 */
private val staggeredRoomCode = Regex("""(^|[^A-Za-z0-9])(YF|SX)\s*\d""", RegexOption.IGNORE_CASE)

fun isStaggeredPlace(place: String): Boolean =
    "逸夫教学楼" in place || "思源西楼" in place || staggeredRoomCode.containsMatchIn(place)

/** 普通课程且位于第二节、教室在错峰楼宇；实验/考试按自身真实时间，不参与。 */
fun Course.isStaggered(enabled: Boolean): Boolean =
    enabled && scheduleEventKind == null && courseLocationIndex / 8 == STAGGERED_SLOT_INDEX &&
        isStaggeredPlace(coursePlace)

/** 课程实际上课时间 "HH:mm-HH:mm"；节次越界时为 null。 */
fun Course.classTimeRange(staggeredEnabled: Boolean): String? {
    val range = SLOT_TIME_RANGES.getOrNull(courseLocationIndex / 8) ?: return null
    return if (isStaggered(staggeredEnabled)) range.shiftedBy(STAGGERED_SHIFT_MINUTES) else range
}

private fun String.shiftedBy(minutes: Int): String = split('-').joinToString("-") { time ->
    val (hour, minute) = time.trim().split(':').map(String::toInt)
    val total = hour * 60 + minute + minutes
    "${(total / 60).toString().padStart(2, '0')}:${(total % 60).toString().padStart(2, '0')}"
}
