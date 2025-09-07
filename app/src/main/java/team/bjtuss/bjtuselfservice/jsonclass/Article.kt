package team.bjtuss.bjtuselfservice.jsonclass

data class Article(
    val academy_sign: String,
    val createTime: String,
    val id: Int,
    val push_type: Int,
    val send_type: Int,
    val status: Int,
    val title: String,
    val top_flag: Int
)