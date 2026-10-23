package team.bjtuss.bjtuselfservice.utils

/** School week expressions, shared by scraping, highlighting and calendar export. */
internal object CourseWeeks {
    @JvmStatic
    fun extract(raw: String): String {
        val prefix = Regex("^[第0-9\\s,，、\\-－—~～至周()（）单双]+").find(raw.trim())?.value?.trim()
            ?: return "未知"
        return prefix.takeIf { it.contains("周") && parse(it).isNotEmpty() } ?: "未知"
    }

    fun parse(value: String): Set<Int> {
        val normalized = value.replace(Regex("\\s+"), "").replace("第", "").replace("周", "")
            .replace('，', ',').replace('、', ',').replace('（', '(').replace('）', ')')
            .replace('－', '-').replace('—', '-').replace('～', '-').replace('~', '-').replace("至", "-")
        val result = sortedSetOf<Int>()
        for (part in normalized.split(',')) {
            val match = Regex("^(\\d+)(?:-(\\d+))?(?:\\(([单双])\\)|([单双]))?$").matchEntire(part)
                ?: return emptySet()
            val from = match.groupValues[1].toIntOrNull() ?: return emptySet()
            val to = match.groupValues[2].ifEmpty { match.groupValues[1] }.toIntOrNull() ?: return emptySet()
            if (from !in 1..53 || to !in from..53) return emptySet()
            val parity = match.groupValues[3].ifEmpty { match.groupValues[4] }
            result += (from..to).filter { parity.isEmpty() || it % 2 == if (parity == "单") 1 else 0 }
        }
        return result
    }

}
