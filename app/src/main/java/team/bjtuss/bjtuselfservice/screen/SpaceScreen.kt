package team.bjtuss.bjtuselfservice.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import sv.lib.squircleshape.CornerSmoothing
import sv.lib.squircleshape.SquircleShape
import team.bjtuss.bjtuselfservice.R
import team.bjtuss.bjtuselfservice.RouteManager


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

@Composable
fun SpaceCard(
    title: String,
    image: Int,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = modifier,
        shape = SquircleShape(
            48.dp,
            CornerSmoothing.Medium
        ),
        elevation = CardDefaults.elevatedCardElevation(
            8.dp
        ),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
                .copy(alpha = 0.1f)
                .compositeOver(backgroundColor),
        )
    ) {
        Column(
            contentModifier
                .clickable { onClick() }
                .aspectRatio(1.0f)
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            )
            Image(
                modifier = Modifier
                    .size(70.dp)
                    .align(Alignment.End),
                painter = painterResource(id = image),
                contentDescription = title
            )

        }
    }
}

private val spaces = listOf(
    Space("成绩", R.drawable.grade, RouteManager.Grade),
    Space("课程表", R.drawable.course, RouteManager.CourseSchedule),
    Space("考试安排", R.drawable.exam, RouteManager.ExamSchedule),
    Space("作业", R.drawable.homework, RouteManager.HomeWork),
    Space("课件", R.drawable.homework, RouteManager.Courseware),
    Space("教室人数评估", R.drawable.detect, RouteManager.Building),
    Space("其他功能", R.drawable.other_function, RouteManager.OtherFunction)
)

private data class Space(
    val title: String,
    val image: Int,
    val route: String
)


