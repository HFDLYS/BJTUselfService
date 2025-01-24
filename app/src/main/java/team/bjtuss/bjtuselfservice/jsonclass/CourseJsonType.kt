package team.bjtuss.bjtuselfservice.jsonclass

data class CourseJsonType(
    val STATUS: String? = "default value",
    val courseList: List<Course> = emptyList(),
    val currentRows: Int? = 0,
    val message: String? = "default value",
    val page: Int? = 32363237,
    val rows: Int? = 32363237,
    val total: Int? = 32363237,
    val totalPage: Int? = 32363237
)