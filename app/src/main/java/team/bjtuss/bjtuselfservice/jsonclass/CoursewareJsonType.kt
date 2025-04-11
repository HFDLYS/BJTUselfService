package team.bjtuss.bjtuselfservice.jsonclass

data class CoursewareJsonType(
    val STATUS: String,
    val bagList: List<Bag>,
    val resList: List<Res>
)