package team.bjtuss.bjtuselfservice.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 课程实体类
 */
@Entity
data class CourseEntity(
    @PrimaryKey(autoGenerate = true)
    override var id: Int = 0,
    val courseId: String,
    val courseName: String,
    val courseTeacher: String,
    val courseLocationIndex: Int,
    val courseTime: String,
    val coursePlace: String,
    val isCurrentSemester: Boolean
) : BaseEntity() {
//    constructor(
//        courseId: String,
//        courseName: String,
//        courseTeacher: String,
//        courseTime: String,
//        coursePlace: String
//    ) : this(
//        id = 0,
//        courseId = courseId,
//        courseName = courseName,
//        courseTeacher = courseTeacher,
//        courseTime = courseTime,
//        coursePlace = coursePlace,
//        isCurrentSemester = true
//    )

    constructor(
        courseId: String,
        courseName: String,
        courseTeacher: String,
        courseLocationIndex: Int,
        courseTime: String,
        coursePlace: String,
        isCurrentSemester: Boolean
    ) : this(
        id = 0,
        courseId = courseId,
        courseName = courseName,
        courseTeacher = courseTeacher,
        courseLocationIndex = courseLocationIndex,
        courseTime = courseTime,
        coursePlace = coursePlace,
        isCurrentSemester = isCurrentSemester
    )

}