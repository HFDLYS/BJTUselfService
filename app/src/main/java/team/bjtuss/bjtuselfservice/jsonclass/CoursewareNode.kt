package team.bjtuss.bjtuselfservice.jsonclass

data class CoursewareNode(
    val id: Int=0,
    val course: Course= Course(),
    var res: Res?=null,
    var bag: Bag?=null,
    var children: List<CoursewareNode> = emptyList(),
)

