package team.bjtuss.bjtuselfservice.jsonclass

import androidx.room.Embedded
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import team.bjtuss.bjtuselfservice.entity.CoursewareCourseEntity

@Serializable
data class CoursewareNode(
    val id: Int = 0,
    val course: CoursewareCourseEntity = CoursewareCourseEntity(),
    var res: Res? = null,
    var bag: Bag? = null,
    var children: List<CoursewareNode> = emptyList(),
)

