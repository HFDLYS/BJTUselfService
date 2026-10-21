package team.bjtuss.bjtuselfservice.utils

import kotlin.math.abs

data class PlannerRoom(
    val name: String,
    val capacity: Int,
)

data class PlannerStep(
    val slot: Int,
    val room: String,
    val floor: Int,
)

sealed interface PlannerResult {
    data class Success(
        val steps: List<PlannerStep>,
        val floorChanges: Int,
    ) : PlannerResult

    data class Failure(val reason: String) : PlannerResult
}

/**
 * Plans the cheapest sequence of classrooms for a set of periods.
 *
 * The graph is layered by period. Every room that is free in that period is a
 * node; moving from a room in period i to a room in period i+1 costs the
 * absolute floor difference. A dynamic program gives the shortest path.
 */
object ClassroomPlanner {
    fun plan(
        rooms: List<PlannerRoom>,
        slotsByRoom: Map<String, List<Int>>,
        slots: List<Int>,
        minCapacity: Int = 0,
    ): PlannerResult {
        if (slots.isEmpty()) return PlannerResult.Failure("请至少选择一个节次")
        val orderedSlots = slots.distinct().sorted()
        val floors = rooms.associate { it.name to floorOf(it.name) }
        val freeBySlot = orderedSlots.map { slot ->
            rooms.filter { room ->
                room.capacity >= minCapacity &&
                    slotsByRoom[room.name]?.getOrNull(slot) == 0
            }.map { it.name }
        }

        freeBySlot.forEachIndexed { index, free ->
            if (free.isEmpty()) {
                return PlannerResult.Failure(
                    "第 " + (orderedSlots[index] + 1) + " 节没有满足容量要求的空闲教室"
                )
            }
        }

        var costs: Map<String, Int> = freeBySlot.first().associateWith { 0 }
        val backPointers = mutableListOf<Map<String, String?>>()

        for (index in 1 until freeBySlot.size) {
            val nextRooms = freeBySlot[index]
            val nextCosts = mutableMapOf<String, Int>()
            val back = mutableMapOf<String, String?>()
            for (next in nextRooms) {
                var bestCost = Int.MAX_VALUE
                var bestPrev: String? = null
                for ((prev, prevCost) in costs) {
                    val stepCost = abs((floors[prev] ?: 0) - (floors[next] ?: 0))
                    val total = prevCost + stepCost
                    if (total < bestCost) {
                        bestCost = total
                        bestPrev = prev
                    }
                }
                nextCosts[next] = bestCost
                back[next] = bestPrev
            }
            costs = nextCosts
            backPointers.add(back)
        }

        var lastRoom = costs.minByOrNull { it.value }?.key
            ?: return PlannerResult.Failure("无法生成教室路径")
        val path = ArrayDeque<String>()
        path.addFirst(lastRoom)
        for (index in backPointers.indices.reversed()) {
            val prev = backPointers[index][lastRoom]
                ?: return PlannerResult.Failure("无法生成教室路径")
            path.addFirst(prev)
            lastRoom = prev
        }

        val steps = path.mapIndexed { index, room ->
            PlannerStep(orderedSlots[index], room, floors[room] ?: 0)
        }
        val changes = steps.zipWithNext().sumOf { (a, b) ->
            abs(a.floor - b.floor)
        }
        return PlannerResult.Success(steps, changes)
    }

    fun floorOf(roomName: String): Int {
        val match = Regex("(\\d)").find(roomName)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }
}
