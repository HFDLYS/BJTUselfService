package team.bjtuss.bjtuselfservice.reminder

data class ClassroomSubscription(
    val building: String,
    val room: String,
    val day: Int,
    val slot: Int,
    val slots: List<Int>? = emptyList(),
)
