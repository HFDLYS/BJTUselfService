package team.bjtuss.bjtuselfservice.jsonclass

data class CourseResourceResponse(
    val STATUS: String,
    val bagList: List<Bag>?,
    val resList: List<Res>?,
)

