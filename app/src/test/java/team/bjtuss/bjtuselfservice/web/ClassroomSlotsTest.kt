package team.bjtuss.bjtuselfservice.web

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Test

class ClassroomSlotsTest {
    private fun slots(cells: String, day: Int = 0): List<Int> {
        val row = Jsoup.parse("<table><tr><td>A101</td>$cells</tr></table>")
            .select("tr td")
        return MisDataManager.parseClassroomSlots(row, day)
    }

    @Test
    fun missingColumnsAreUnknown() {
        assertEquals(listOf(0, -1, -1, -1, -1, -1, -1), slots("<td style='background-color: #fff'></td>"))
    }

    @Test
    fun unknownColorsAndMissingStylesAreUnknown() {
        assertEquals(listOf(-1, -1), slots("<td></td><td style='background-color: #123456'></td>").take(2))
    }

    @Test
    fun parsesKnownColorsAndCssSpacing() {
        val colors = listOf("#FFFFFF", "#e46868", "#9e6868", "#394ed6", "#77bf6d", "#d8cc56", "#fff")
        val cells = colors.joinToString("") { "<td style='width: 10px; background-color:$it;'></td>" }
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 0), slots(cells))
        assertEquals(List(7) { -1 }, slots(cells, day = 1))
    }
}
