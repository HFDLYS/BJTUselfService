package team.bjtuss.bjtuselfservice.jsonclass

data class Res(
    val auditStatus: Int,
    val clicks: Int,
    val docType: String,
    val downloadNum: Int,
    val extName: String,
    val inputTime: String,
    val isPublic: Int,
    val play_url: String?,
    val resId: Int,
    val res_url: String,
    val rpId: String,
    val rpName: String,
    val rpSize: String,
    val share_type: Int,
    val stu_download: Int,
//    val teacherId: String,
    val teacherName: String
)