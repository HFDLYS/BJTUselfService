package team.bjtuss.bjtuselfservice.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class ExamScheduleEntity(
    @PrimaryKey(autoGenerate = true)
    override var id: Int = 0,
    var examType: String? = null,
    var courseName: String? = null,
    var examTimeAndPlace: String? = null,
    var examStatus: String? = null,
    var detail: String? = "",
) : BaseEntity() {
    constructor(
        examType: String,
        courseName: String,
        examTimeAndPlace: String,
        examStatus: String,
        detail: String
    ) : this(
        id = 0,
        examType = examType,
        courseName = courseName,
        examTimeAndPlace = examTimeAndPlace,
        examStatus = examStatus,
        detail = detail
    )
}