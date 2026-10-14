package team.bjtuss.bjtuselfservice.shared.feature.phyvlab

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import team.bjtuss.bjtuselfservice.shared.domain.phyvlab.PhyVlabActivity
import team.bjtuss.bjtuselfservice.shared.feature.physicslab.PhysicsLab

class PhyVlabEnrollmentTest {
    private val bookings = listOf(
        PhysicsLab(LocalDate(2026, 11, 12), 4, "光纤特性和光信号传输", "7305", "教师甲", 1),
        PhysicsLab(LocalDate(2026, 11, 20), 4, "利用超声光栅测定液体中声速", "7317", "教师乙", 1),
        PhysicsLab(LocalDate(2026, 11, 26), 4, "声源定位的GPS模拟", "7312A", "教师丙", 2),
        PhysicsLab(LocalDate(2026, 12, 8), 4, "超声专题", "7312B", "教师丁", 2),
    )

    @Test
    fun normalizesNumbersAndDifficultySuffix() {
        assertEquals("光栅光谱仪测光谱", normalizeExperimentName("33 光栅光谱仪测光谱（难度系数1.1）"))
        assertEquals("万用表设计", normalizeExperimentName("37，38 万用表设计（难度系数1.1）"))
        assertEquals("光纤特性和光信号传输", normalizeExperimentName("34 光纤特性和光信号传输"))
        assertEquals("双棱镜干涉测量光波波长", normalizeExperimentName("36 双棱镜干涉测量光波波长 (难度系数 1.1)"))
    }

    @Test
    fun matchesBookedExperimentsOnly() {
        assertEquals("光纤特性和光信号传输", matchPhysicsLabBooking("34 光纤特性和光信号传输", bookings))
        assertEquals("利用超声光栅测定液体中声速", matchPhysicsLabBooking("35 利用超声光栅测定液体中声速", bookings))
        assertNull(matchPhysicsLabBooking("33 光栅光谱仪测光谱（难度系数1.1）", bookings))
        assertNull(matchPhysicsLabBooking("37，38 万用表设计（难度系数1.1）", bookings))
    }

    @Test
    fun shortNamesDoNotMatchByContainment() {
        val shortBooking = listOf(PhysicsLab(LocalDate(2026, 11, 12), 4, "光学", "7305", "", 1))
        assertNull(matchPhysicsLabBooking("12 光学平台调节与几何光学", shortBooking))
    }

    @Test
    fun onlyPhysicsExperimentCourseIsFiltered() {
        assertTrue(isPhysicsExperimentCourse("物理实验II_(2026秋)"))
        assertFalse(isPhysicsExperimentCourse("大学物理演示实验_(2026秋)"))
        assertFalse(isPhysicsExperimentCourse("大学物理II_(2026秋)"))

        val lecture = activity("大学物理II_(2026秋)", "第一章作业")
        assertEquals(PhyVlabEnrollment.NOT_APPLICABLE, phyVlabEnrollment(lecture, bookings).enrollment)
        assertEquals(PhyVlabEnrollment.BOOKED, phyVlabEnrollment(activity("物理实验II_(2026秋)", "34 光纤特性和光信号传输"), bookings).enrollment)
        assertEquals(PhyVlabEnrollment.NOT_BOOKED, phyVlabEnrollment(activity("物理实验II_(2026秋)", "33 光栅光谱仪测光谱（难度系数1.1）"), bookings).enrollment)
        assertEquals(PhyVlabEnrollment.UNVERIFIED, phyVlabEnrollment(activity("物理实验II_(2026秋)", "34 光纤特性和光信号传输"), null).enrollment)
        assertEquals(PhyVlabEnrollment.UNVERIFIED, phyVlabEnrollment(activity("物理实验II_(2026秋)", "34 光纤特性和光信号传输"), emptyList()).enrollment)
    }

    private fun activity(course: String, title: String) = PhyVlabActivity(
        id = title.hashCode(), courseId = course.hashCode(), courseName = course, title = title,
        activityType = "assign", activityUrl = "https://phyvlab.bjtu.edu.cn/mod/assign/view.php?id=1",
    )
}
