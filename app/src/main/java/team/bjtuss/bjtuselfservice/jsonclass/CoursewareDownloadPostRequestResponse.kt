package team.bjtuss.bjtuselfservice.jsonclass

data class CoursewareDownloadPostRequestResponse(
    val download_type: String,
    val flag: Boolean,
    val html: String,
    val rpUrl: String
)