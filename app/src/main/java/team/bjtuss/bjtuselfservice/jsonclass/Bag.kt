package team.bjtuss.bjtuselfservice.jsonclass

import kotlinx.serialization.Serializable

@Serializable
data class Bag(
    val add_time: String = "default value",
    val bag_content: String = "default value",
    val bag_name: String = "default value",
    val course_code: String = "default value",
    val fz_id: String = "default value",
    val id: Int,
    val resource_type: String = "default value",
    val sequ: String = "default value",
    val share_type: Int = 32363237,
    val show_type: Int = 32363237,
    val sort: Int = 32363237,
    val tId: Int = 32363237,
    val tag_level: Int = 32363237,
//    val teacher_id: String,
    val up_id: Int = 32363237
)