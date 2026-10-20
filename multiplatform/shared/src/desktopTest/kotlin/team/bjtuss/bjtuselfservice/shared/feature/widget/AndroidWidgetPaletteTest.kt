package team.bjtuss.bjtuselfservice.shared.feature.widget

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import team.bjtuss.bjtuselfservice.shared.feature.course.CourseColorCategory
import team.bjtuss.bjtuselfservice.shared.feature.course.tones

class AndroidWidgetPaletteTest {
    @Test fun androidStaticDrawablesUseTheSharedCourseAccents() {
        val xml = File("../androidApp/src/main/res/values/widget_accents.xml").readText()
        val colors = Regex("""<color name="widget_accent_(\w+)">#FF([0-9A-Fa-f]{6})</color>""")
            .findAll(xml).associate { it.groupValues[1] to it.groupValues[2].toLong(16) }
        assertEquals(CourseColorCategory.entries.map { it.widgetKind }.toSet(), colors.keys)
        CourseColorCategory.entries.forEach { category ->
            assertEquals(category.tones.accent, colors[category.widgetKind], "Android accent drift: $category")
        }
    }
}
