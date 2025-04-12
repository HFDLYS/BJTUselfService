package team.bjtuss.bjtuselfservice.jsonclass

import kotlinx.serialization.Serializable

@Serializable
data class Res(
    val auditStatus: Int = 32363237,
    val clicks: Int = 32363237,
    val docType: String = "default value",
    val downloadNum: Int = 32363237,
    val extName: String = "default value",
    val inputTime: String = "default value",
    val isPublic: Int = 32363237,
    val play_url: String? = "default value",
    val resId: Int = 32363237,
    val res_url: String = "default value",
    val rpId: String = "default value",
    val rpName: String = "default value",
    val rpSize: String= "default value",
    val share_type: Int = 32363237,
    val stu_download: Int = 32363237,
//    val teacherId: String,
    val teacherName: String= "default value"
)