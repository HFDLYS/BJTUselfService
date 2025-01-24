package team.bjtuss.bjtuselfservice.jsonclass

data class HomeworkJsonType(
    val STATUS: String? = "",
    val courseNoteList: List<CourseNote> = emptyList(),
    val currentRow: Int? = 0,
    val message: String? = "",
    val page: Int? = 0,
    val size: Int? = 0,
    val total: Int? = 0,
    val totalPage: Int? = 0
)