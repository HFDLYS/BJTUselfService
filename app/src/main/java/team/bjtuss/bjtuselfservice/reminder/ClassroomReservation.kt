package team.bjtuss.bjtuselfservice.reminder

data class ClassroomReservation(
    val id: Long,
    val building: String,
    val room: String,
    val date: String,
    val slots: List<Int>,
    val createdAt: Long = System.currentTimeMillis(),
)
