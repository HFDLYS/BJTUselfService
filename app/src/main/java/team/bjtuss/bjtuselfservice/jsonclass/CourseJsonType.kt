package team.bjtuss.bjtuselfservice.jsonclass

data class CourseJsonType(
    val STATUS: String,
    val courseList: List<Course> = emptyList(),
    val currentRows: Int,
    val message: String,
    val page: Int,
    val rows: Int,
    val total: Int,
    val totalPage: Int
)