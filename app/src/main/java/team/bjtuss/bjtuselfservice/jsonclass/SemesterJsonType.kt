package team.bjtuss.bjtuselfservice.jsonclass

data class SemesterJsonType(
    val STATUS: String?="default value",
    val result: List<Result>?=emptyList()
)