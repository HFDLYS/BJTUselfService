package team.bjtuss.bjtuselfservice.entity

import androidx.room.Entity
import androidx.room.PrimaryKey


/**
 * 成绩实体类
 */

@Entity
data class GradeEntity(
    @PrimaryKey(autoGenerate = true)
    override var id: Int =0,  // 默认值为0，Room 会自动处理 id
    val courseName: String,
    val courseTeacher: String,
    val courseScore: String,
    val courseCredits: String,
    val courseYear: String,
    @JvmField
    var tag: String,
    @JvmField
    var detail: String,
) : BaseEntity() {
    constructor(
        courseName: String,
        courseTeacher: String,
        courseScore: String,
        courseCredits: String,
        courseYear: String
    ) : this(
        id = 0,
        courseName = courseName,
        courseTeacher = courseTeacher,
        courseScore = courseScore,
        courseCredits = courseCredits,
        courseYear = courseYear,
        tag = "",
        detail = ""
    )
}

