package team.bjtuss.bjtuselfservice.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class HomeworkEntity(
    @PrimaryKey(autoGenerate = true)
    override var id: Int,
    val courseId: Int,
    val courseName: String,
    val title: String,
    val content: String,
    val createDate: String,
    val endTime: String,
    val openDate: String,
    val status: Int,
    val submitCount: Int,
    val allCount: Int,
    val subStatus: String,
    val homeworkType: Int,// 0: 作业 1: 课程设计 2: 实验报告
) : BaseEntity() {
    constructor(
        courseId: Int,
        courseName: String,
        title: String,
        content: String,
        createDate: String,
        endTime: String,
        openDate: String,
        status: Int,
        submitCount: Int,
        allCount: Int,
        subStatus: String,
        homeworkType: Int
    ) : this(
        id = 0,
        courseId = courseId,
        courseName = courseName,
        title = title,
        content = content,
        createDate = createDate,
        endTime = endTime,
        openDate = openDate,
        status = status,
        submitCount = submitCount,
        allCount = allCount,
        subStatus = subStatus,
        homeworkType = homeworkType
    )
}