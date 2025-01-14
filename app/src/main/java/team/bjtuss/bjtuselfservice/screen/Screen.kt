package team.bjtuss.bjtuselfservice.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.Blue
import androidx.compose.ui.graphics.Color.Companion.Green
import androidx.compose.ui.graphics.Color.Companion.Red
import androidx.compose.ui.graphics.Color.Companion.Yellow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import team.bjtuss.bjtuselfservice.R
import team.bjtuss.bjtuselfservice.RouteManager
import team.bjtuss.bjtuselfservice.StudentAccountManager
import team.bjtuss.bjtuselfservice.component.SpaceCard
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.sp

@Composable
fun HomeScreen() {
    val studentAccountManager = StudentAccountManager.getInstance()

//    val name = studentAccountManager.stuName
//
//    Text(text = "Hello, $name")

    var status by remember {
        mutableStateOf<StudentAccountManager.Status?>(null)
    }

    studentAccountManager.status.thenAccept {
        status = it
    }

    val ecardBalance = "校园卡余额：${status?.EcardBalance}".let {
        if (status?.EcardBalance?.toDoubleOrNull() ?: 0.0 < 20) {
            "$it，会不会不够用了"
        } else {
            it
        }
    }

    val netBalance = "校园网余额：${status?.NetBalance}".let {
        if (status?.NetBalance == "0") {
            "$it，😱下个月要没网了"
        } else {
            it
        }
    }

    val newMailCount = "新邮件：${status?.NewMailCount}".let {
        if (status?.NewMailCount != "0") {
            "$it，记得去看哦"
        } else {
            it
        }
    }



    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Email,
                contentDescription = "New Mail",
                tint = Color.Blue
            )
            Spacer(modifier = Modifier.width(8.dp))
            BJTUMailLoginScreen({ Text(newMailCount, fontSize = 18.sp) })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.AccountBalanceWallet,
                contentDescription = "Ecard Balance",
                tint = Color.Green
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = ecardBalance, fontSize = 18.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Wifi,
                contentDescription = "Net Balance",
                tint = Color.Red
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = netBalance, fontSize = 18.sp)
        }
    }
}

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
            SpaceCard(title = space.title, image = space.image, backgroundColor = space.color) {
                navController.navigate(space.route)
            }
        }


    }
}

private val spaces = listOf(
    Space("成绩", R.drawable.diary_img, Blue, RouteManager.Grade),
    Space("课程表", R.drawable.ai_chat_img, Red, RouteManager.CourseSchedule),
    Space("作业与考试", R.drawable.bookmarks_img, Green, RouteManager.HomeWorkAndExam),
    Space("教室人数评估", R.drawable.calendar_img, Yellow, RouteManager.ClassroomPeopleEstimation),
    Space(
        "北交妙妙屋",
        R.drawable.bookmarks_img,
        Color(0xFF00FF00),
        RouteManager.BJTUMiaoMiaoHouse
    ),
)

private data class Space(
    val title: String,
    val image: Int,
    val color: Color,
    val route: String
)




