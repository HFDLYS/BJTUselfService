package team.bjtuss.bjtuselfservice.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import team.bjtuss.bjtuselfservice.R
import team.bjtuss.bjtuselfservice.RouteManager
import team.bjtuss.bjtuselfservice.component.SpaceCard


@Composable
fun SpaceScreen(navController: NavController) {

    LazyVerticalGrid(
        modifier = Modifier.fillMaxSize(),
        columns = GridCells.Adaptive(140.dp),
        contentPadding = PaddingValues(
            top = 10.dp,
            bottom = 32.dp,
            start = 10.dp,
            end = 10.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(spaces.size) { index ->
            val space = spaces[index]
            SpaceCard(
                title = space.title,
                image = space.image,
                backgroundColor = MaterialTheme.colorScheme.primary
            ) {
                navController.navigate(space.route)
            }
        }
    }
}

private val spaces = listOf(
    Space("成绩", R.drawable.grade, RouteManager.Grade),
    Space("课程表", R.drawable.course, RouteManager.CourseSchedule),
    Space("考试安排", R.drawable.exam, RouteManager.ExamSchedule),
    Space("教室人数评估", R.drawable.detect, RouteManager.Building),
    // Space("北交妙妙屋", R.drawable.bookmarks_img, Color(0xFF00FF00), RouteManager.BJTUMiaoMiaoHouse),
)

private data class Space(
    val title: String,
    val image: Int,
    val route: String
)


