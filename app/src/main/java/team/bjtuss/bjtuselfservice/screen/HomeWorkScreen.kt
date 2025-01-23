package team.bjtuss.bjtuselfservice.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.room.util.TableInfo
import team.bjtuss.bjtuselfservice.repository.SmartCurriculumPlatformRepository
import team.bjtuss.bjtuselfservice.viewmodel.MainViewModel
import androidx.compose.runtime.getValue

@Composable
fun HomeWorkScreen(mainViewModel: MainViewModel) {
    LaunchedEffect(Unit) {
        mainViewModel.homeworkViewModel.syncDataAndClearChange()
    }
    val homeworkList by mainViewModel.homeworkViewModel.homeworkList.collectAsState()
    LazyColumn {
        items(homeworkList.size) {
            val item = homeworkList[it]
            Card {
                Column {
                    Text(text = item.title)
                    Text(text = item.courseName)
                    Text(text = item.endTime)
                    // Text(text = item.content)
                    Text(text = item.createDate)
                }
            }
        }
    }
}
