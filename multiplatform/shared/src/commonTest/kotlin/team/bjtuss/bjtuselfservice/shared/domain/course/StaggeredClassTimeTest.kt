package team.bjtuss.bjtuselfservice.shared.domain.course

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StaggeredClassTimeTest {
    private fun course(index: Int, place: String, kind: String? = null) = Course(
        courseId = "C1", courseName = "课程", courseTeacher = "教师", courseLocationIndex = index,
        courseTime = "第1-16周", coursePlace = place, isCurrentSemester = false, scheduleEventKind = kind,
    )

    @Test fun recognisesYifuAndSiyuanWestByBuildingOrRoomCode() {
        assertTrue(isStaggeredPlace("海淀主校区,逸夫教学楼,YF302"))
        assertTrue(isStaggeredPlace("海淀西校区,思源西楼,SX501"))
        assertTrue(isStaggeredPlace("YF 101"))
        assertTrue(isStaggeredPlace("sx203"))
        assertFalse(isStaggeredPlace("海淀主校区,思源楼,SY101"))
        assertFalse(isStaggeredPlace("海淀主校区,思源东楼,SD101"))
        assertFalse(isStaggeredPlace("威海校区,逸夫楼,302"))
        assertFalse(isStaggeredPlace("第九教学楼,9SX01"))
        assertFalse(isStaggeredPlace(""))
    }

    @Test fun onlySecondSlotOrdinaryCoursesShiftWhenEnabled() {
        // 周二第二节：1 * 8 + 2。
        val yifu = course(10, "海淀主校区,逸夫教学楼,YF302")
        assertEquals("10:30-12:20", yifu.classTimeRange(staggeredEnabled = true))
        assertEquals("10:10-12:00", yifu.classTimeRange(staggeredEnabled = false))
        assertTrue(yifu.isStaggered(true))
        assertFalse(yifu.isStaggered(false))

        assertEquals("08:00-09:50", course(2, "海淀主校区,逸夫教学楼,YF302").classTimeRange(true))
        assertEquals("10:10-12:00", course(10, "海淀主校区,思源楼,SY101").classTimeRange(true))
        assertFalse(course(10, "海淀主校区,逸夫教学楼,YF302", kind = "physicslab").isStaggered(true))
        assertNull(course(80, "逸夫教学楼").classTimeRange(true))
    }
}
