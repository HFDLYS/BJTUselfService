package team.bjtuss.bjtuselfservice.shared.feature.phydemo

import com.fleeksoft.ksoup.Ksoup
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

internal const val DEMO_ORIGIN = "https://physpace.bjtu.edu.cn:8089"
internal const val DEMO_PREFIX = "/phydemo/"
val physicsDemoNames = listOf("光学", "近代与综合")
val physicsDemoTimes = listOf("08:10-09:50", "10:10-11:50", "14:10-15:50", "16:10-17:50", "18:30-20:10", "20:30-22:10")

class PhysicsDemoFailure(message: String) : Exception(message)

@Serializable
data class PhysicsDemoBooking(val name: String, val date: LocalDate, val period: Int, val teacher: String = "") {
    init { require(name in physicsDemoNames && period in 1..6) }
    val timeRange: String get() = physicsDemoTimes[period - 1]
    val location: String get() = if (name == "光学") "7418" else "7416"
}

internal fun demoCompact(value: String): String = value.replace(Regex("[\\s\\u00a0]+"), "")

fun parsePhysicsDemoBookings(html: String): List<PhysicsDemoBooking> {
    val doc = Ksoup.parse(html)
    val text = demoCompact(doc.text())
    if (doc.selectFirst("input[type=password]") != null || "已预约实验信息" !in text) {
        throw PhysicsDemoFailure("演示实验账号未登录或结果页无法验证。")
    }
    val fieldsets = doc.select("fieldset").filter { it.selectFirst("legend")?.text()?.contains("已预约实验") == true }
    if ("无预约信息" in text && fieldsets.isEmpty()) return emptyList()
    val results = fieldsets.map { fieldset ->
        val name = demoCompact(fieldset.selectFirst("legend")!!.text()).removePrefix("已预约实验：")
        if (name !in physicsDemoNames) throw PhysicsDemoFailure("已预约实验名称无法识别，保留上次结果。")
        val details = fieldset.select("dl").associate { dl ->
            demoCompact(dl.selectFirst("dt")?.text().orEmpty()) to demoCompact(dl.selectFirst("dd")?.text().orEmpty())
        }
        val date = runCatching { LocalDate.parse(details["实验日期："].orEmpty()) }.getOrNull()
            ?: throw PhysicsDemoFailure("已预约实验日期无法识别，保留上次结果。")
        val range = Regex("(\\d{2}:\\d{2})(?::\\d{2})?[~～至\\-](\\d{2}:\\d{2})(?::\\d{2})?")
            .findAll(details["预约的时间段："].orEmpty()).toList()
        if (range.size != 1) throw PhysicsDemoFailure("已预约实验时段无法识别。")
        val time = "${range[0].groupValues[1]}-${range[0].groupValues[2]}"
        val period = physicsDemoTimes.indexOf(time) + 1
        if (period !in 1..6) throw PhysicsDemoFailure("已预约实验时段发生变化。")
        PhysicsDemoBooking(name, date, period, details["助教："].orEmpty().removeSuffix("()"))
    }
    if (results.isEmpty()) throw PhysicsDemoFailure("预约结果结构发生变化，保留上次结果。")
    return results.distinct()
}
