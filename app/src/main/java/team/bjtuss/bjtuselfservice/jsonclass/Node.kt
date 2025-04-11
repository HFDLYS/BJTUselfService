package team.bjtuss.bjtuselfservice.jsonclass

data class Node(
    val id: Int,
    val link: String? = "default value",
    val name: String,
    val pId: String? = "default value",
)