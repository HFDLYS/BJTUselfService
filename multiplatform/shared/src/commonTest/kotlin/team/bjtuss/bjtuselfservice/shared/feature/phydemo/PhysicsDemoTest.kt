package team.bjtuss.bjtuselfservice.shared.feature.phydemo

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlin.test.*
import kotlin.time.Instant
import team.bjtuss.bjtuselfservice.shared.auth.Credentials
import team.bjtuss.bjtuselfservice.shared.network.*
import team.bjtuss.bjtuselfservice.shared.domain.calendar.*
import team.bjtuss.bjtuselfservice.shared.domain.classroomoccupancy.OccupancyWeekDate
import team.bjtuss.bjtuselfservice.shared.domain.course.Course
import team.bjtuss.bjtuselfservice.shared.feature.course.*
import team.bjtuss.bjtuselfservice.shared.feature.shell.PHYSICS_DEMO_ROUTE_ID
import team.bjtuss.bjtuselfservice.shared.feature.shell.nativeRouteTitle
import team.bjtuss.bjtuselfservice.shared.feature.shell.isNativeTabRoute

internal const val demoLoginFixture = "<form action='/phydemo/login' method='post'><input name='username'><input name='password' type='password'></form>"
internal const val demoEmptyFixture = "<h3>已预约实验信息</h3><div class='form_tips'><h3>无预约信息</h3></div>"
internal fun demoBookingFixture(name: String = "光学", date: String = "2026-10-19", time: String = "14:10:00~15:50:00") = """
    <h3>已预约实验信息</h3><div class='p_10'><fieldset><legend>已预约实验：<a>$name</a></legend>
    <dl><dt>实验日期：</dt><dd>$date</dd></dl><dl><dt>预约的时间段：</dt><dd>时段三,$time&nbsp;</dd></dl>
    <dl><dt>实验周次：</dt><dd>第6周</dd></dl><dl><dt>助教：</dt><dd>测试助教()</dd></dl></fieldset>
    <input class='btn_cancel' onclick="del('/phydemo/appointment/delete/123/')"></div>
""".trimIndent()

class PhysicsDemoTest {
    private fun weeks() = listOf(OccupancyWeekDate(6,"","",LocalDate(2026,10,19)),OccupancyWeekDate(7,"","",LocalDate(2026,10,26)))

    @Test fun parsesActualFieldsetStructureAndBothKinds() {
        val bookings = parsePhysicsDemoBookings(demoBookingFixture() + demoBookingFixture("近代与综合","2026-10-21"))
        assertEquals(2,bookings.size)
        assertEquals("14:10-15:50",bookings[0].timeRange)
        assertEquals("7418",bookings[0].location)
        assertEquals("7416",bookings[1].location)
        assertEquals("测试助教",bookings[0].teacher)
        assertEquals(LocalDate(2026,10,19),bookings[0].date)
    }
    @Test fun validEmptyIsDifferentFromLoginAndBrokenResults() {
        assertTrue(parsePhysicsDemoBookings(demoEmptyFixture).isEmpty())
        listOf(demoLoginFixture,"<h1>错误</h1>",demoBookingFixture(date="2026-02-30"),
            demoBookingFixture(time="14:10:00~16:10:00"),demoBookingFixture(name="未知实验"),
            demoBookingFixture().replace("实验日期：","未知字段：")).forEach {
            assertFailsWith<PhysicsDemoFailure> { parsePhysicsDemoBookings(it) }
        }
    }
    @Test fun loginUsesIndependentFreshSessionAndOnlyQueriesProtectedResults() = runBlocking {
        val requests = mutableListOf<SchoolHttpRequest>()
        var resets = 0
        val transport = object : SchoolHttpTransport {
            override fun clearSession() { error("CAS must stay intact") }
            override fun clearPhysicsDemoSession() { resets++ }
            override suspend fun execute(request: SchoolHttpRequest): SchoolHttpResponse {
                requests += request
                return when {
                    request.method == SchoolHttpMethod.POST -> SchoolHttpResponse(302,request.url,mapOf("Location" to listOf("/phydemo/arrange/")))
                    request.url.endsWith("/login") -> SchoolHttpResponse(200,request.url,body=demoLoginFixture.encodeToByteArray())
                    else -> SchoolHttpResponse(200,request.url,body=demoBookingFixture().encodeToByteArray())
                }
            }
        }
        val remote=PhysicsDemoRemote(transport)
        repeat(2) { assertEquals(1,remote.fetch(Credentials("fixture","fixture-secret")).size) }
        assertEquals(2,resets)
        assertTrue(requests.all { it.url in listOf("$DEMO_ORIGIN${DEMO_PREFIX}login","$DEMO_ORIGIN${DEMO_PREFIX}appointment/") })
        assertTrue(requests.filter { it.method==SchoolHttpMethod.POST }.all { it.url.endsWith("/login") })
        assertFalse(requests[1].toString().contains("fixture-secret"))
    }
    @Test fun loginRedirectCannotSendCredentialsToAnotherSite() = runBlocking {
        val transport=object:SchoolHttpTransport {
            override fun clearSession() {}
            override suspend fun execute(request:SchoolHttpRequest)=SchoolHttpResponse(302,request.url,mapOf("Location" to listOf("https://example.com/")))
        }
        assertFailsWith<PhysicsDemoFailure> { PhysicsDemoRemote(transport).fetch(Credentials("fixture","fixture")) }
        Unit
    }
    @Test fun fragmentsKeepOrdinaryOverlapAndOnlyTheActualDate() {
        val booking=PhysicsDemoBooking("光学",LocalDate(2026,10,19),3)
        val fragments=scheduleEventCourses(emptyList(),emptyList(),weeks(),listOf(booking))
        assertEquals(1,fragments.size)
        val event=fragments.single()
        assertEquals("phydemo",event.scheduleEventKind)
        assertEquals("演示实验",event.scheduleEventTypeLabel)
        assertEquals(100f/110f,event.eventSlotHeight)
        val ordinary=Course(1,"fixture","普通课程","",25,"第6-7周","",false)
        val state=CourseScheduleUiState(courses=listOf(ordinary),supplementalCourses=fragments,academicWeeks=weeks())
        assertEquals(2,state.coursesForPage(6,LocalDate(2026,10,19)).size)
        assertEquals(1,state.coursesForPage(7,LocalDate(2026,10,26)).size)
        assertTrue(scheduleEventCourses(emptyList(),emptyList(),listOf(OccupancyWeekDate(1,"","",LocalDate(2027,3,1))),listOf(booking)).isEmpty())
    }
    @Test fun calendarUsesActualTimesStableIdsAndRespectsSelectedWeeks() {
        val booking=PhysicsDemoBooking("近代与综合",LocalDate(2026,10,21),6)
        fun export(range:IntRange,bookings:List<PhysicsDemoBooking>)=generateAcademicCalendarIcs(emptyList(),emptyList(),weeks(),range,
            Instant.parse("2026-10-11T00:00:00Z"),physicsDemos=bookings)
        val exported=export(6..7,listOf(booking,booking))
        assertEquals(1,exported.physicsDemoEventCount)
        val event=exported.events.single()
        assertEquals("2026-10-21T20:30:00",event.startLocal)
        assertEquals("2026-10-21T22:10:00",event.endLocal)
        assertEquals(AcademicCalendarEventKind.PHYSICS_DEMO,event.kind)
        assertTrue(event.stableId.startsWith("course-phydemo-"))
        assertEquals(event.stableId,managedCourseStableIdFromMarker(managedCourseEventMarker(event.stableId)))
        assertEquals(event.stableId,export(6..7,listOf(booking.copy(teacher="另一位助教"))).events.single().stableId)
        assertEquals(0,export(7..7,listOf(booking)).physicsDemoEventCount)
        assertEquals(0,export(6..7,emptyList()).physicsDemoEventCount)
        assertTrue(exported.ics.contains("DTSTART;TZID=Asia/Shanghai:20261021T203000"))
    }
    @Test fun sharedNativeRouteHasAQueryTitleAndDoesNotAddABottomTab() {
        assertEquals("演示实验查询",nativeRouteTitle(PHYSICS_DEMO_ROUTE_ID))
        assertFalse(isNativeTabRoute(PHYSICS_DEMO_ROUTE_ID))
    }
}
