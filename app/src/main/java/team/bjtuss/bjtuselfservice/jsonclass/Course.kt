package team.bjtuss.bjtuselfservice.jsonclass

data class Course(
    val begin_date: String,
    val boy: String,
    val course_num: String,
    val end_date: String,
    val fz_id: String,
    val id: Int,
    val name: String,
    val pic: String?,
    val selective_course_id: Any?,
    val teacher_id: Int,
    val teacher_name: String,
    val type: Int,
    val xq_code: String
)