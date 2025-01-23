package team.bjtuss.bjtuselfservice.jsonclass

data class Result(
    val CNAME: String,
    val UP_CCODE: String,
    val UP_CNAME: String,
    val beginDate: String,
    val currentFlag: Int,
    val endDate: String,
    val xqCode: String,
    val xqId: String,
    val xqName: String
)