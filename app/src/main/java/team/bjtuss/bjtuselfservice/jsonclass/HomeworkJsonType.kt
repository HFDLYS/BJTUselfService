package team.bjtuss.bjtuselfservice.jsonclass

data class HomeworkJsonType(
    val STATUS: String? = "default value",
    val courseNoteList: List<CourseNote>? = emptyList(),
    val currentRow: Int? = 32363237,
    val message: String? = "default value",
    val page: Int? = 32363237,
    val size: Int? = 32363237,
    val total: Int? = 32363237,
    val totalPage: Int? = 32363237,
)