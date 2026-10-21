package team.bjtuss.bjtuselfservice.repository

import java.io.IOException
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertTrue
import org.junit.Test
import team.bjtuss.bjtuselfservice.jsonclass.CourseJsonType

class CoursePlatformDataTest {
    private val adapter = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        .adapter(CourseJsonType::class.java)

    @Test
    fun authenticatedEmptySemesterHasNoCourses() {
        assertTrue(coursesFromResponse(adapter.fromJson("""{"STATUS":"0","courseList":[]}""")).isEmpty())
    }

    @Test(expected = IOException::class)
    fun uninitializedSessionIsNotAnEmptySemester() {
        coursesFromResponse(null)
    }

    @Test(expected = IOException::class)
    fun failedResponseCannotEraseCachedCourses() {
        coursesFromResponse(CourseJsonType(STATUS = "1"))
    }

    @Test(expected = IOException::class)
    fun missingCourseListIsNotAnEmptySemester() {
        coursesFromResponse(adapter.fromJson("""{"STATUS":"0"}"""))
    }
}
