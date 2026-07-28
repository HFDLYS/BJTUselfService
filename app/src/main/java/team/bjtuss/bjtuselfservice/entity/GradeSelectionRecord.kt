package team.bjtuss.bjtuselfservice.entity

data class GradeSelectionRecord(
    val courseName: String,
    val courseTeacher: String,
    val courseYear: String,
    val semester: String,
    val lastKnownScore: String,
    val lastKnownCredits: String,
    val occurrence: Int,
)
