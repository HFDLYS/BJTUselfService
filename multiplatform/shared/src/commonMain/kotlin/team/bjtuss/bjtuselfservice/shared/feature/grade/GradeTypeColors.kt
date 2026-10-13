package team.bjtuss.bjtuselfservice.shared.feature.grade

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import team.bjtuss.bjtuselfservice.shared.domain.grade.CourseType

internal data class GradeTypeColors(
    val container: Color,
    val onContainer: Color,
    /** 描边用色：半透明的 onContainer，浅深色下都能看清。 */
    val border: Color = onContainer.copy(alpha = 0.55f),
)

/**
 * 课程性质配色（浅色/深色两套）。按当前页面主题取色，保持原生 sheet 与主题预览一致。
 * 色板保证 onContainer 与 container 对比度、深色模式不刺眼。
 */
@Composable
internal fun courseTypeColors(type: CourseType): GradeTypeColors {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return when (type) {
        CourseType.REQUIRED -> if (dark) {
            GradeTypeColors(container = Color(0xFF4A1F1D), onContainer = Color(0xFFF2B9B4))
        } else {
            GradeTypeColors(container = Color(0xFFF6D2D0), onContainer = Color(0xFF7D1F1A))
        }

        // 限选用琥珀，避免和必修的红粉糊在一起。
        CourseType.LIMITED -> if (dark) {
            GradeTypeColors(container = Color(0xFF3D2A12), onContainer = Color(0xFFF3C98A))
        } else {
            GradeTypeColors(container = Color(0xFFF8E4C4), onContainer = Color(0xFF8A4B12))
        }

        CourseType.ELECTIVE -> if (dark) {
            GradeTypeColors(container = Color(0xFF223065), onContainer = Color(0xFFC3CDFA))
        } else {
            GradeTypeColors(container = Color(0xFFDCE4FC), onContainer = Color(0xFF2F4DB5))
        }

        // 体育与课程表、首页、小组件共用青绿（CourseCategoryPalette）。
        CourseType.PHYSICAL_EDUCATION -> team.bjtuss.bjtuselfservice.shared.feature.course.courseCategoryColors(
            team.bjtuss.bjtuselfservice.shared.feature.course.CourseColorCategory.PHYSICAL_EDUCATION,
        )

        CourseType.UNKNOWN -> if (dark) {
            GradeTypeColors(container = Color(0xFF333438), onContainer = Color(0xFFC6C7CB))
        } else {
            GradeTypeColors(container = Color(0xFFE7E7E9), onContainer = Color(0xFF5F6368))
        }
    }
}
