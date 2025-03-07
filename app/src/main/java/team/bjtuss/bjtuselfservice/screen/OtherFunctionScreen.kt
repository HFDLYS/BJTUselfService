package team.bjtuss.bjtuselfservice.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.modifier.ModifierLocalConsumer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import team.bjtuss.bjtuselfservice.repository.NetworkRepository
import team.bjtuss.bjtuselfservice.repository.OtherFunctionNetworkRepository
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp

@Composable
fun OtherFunctionScreen() {
    var isEnglishGrade by remember { mutableStateOf(false) }

    Scaffold(modifier = Modifier.fillMaxSize()) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Button(onClick = { downloadSchoolCalendar() }) { Text("校历下载") }
                }
                item {
                    Row {
                        Switch(checked = isEnglishGrade, onCheckedChange = { isEnglishGrade = it })
                        Button(onClick = { downloadGradeList(isEnglishGrade) }) {
                            val text =
                                if (isEnglishGrade) "英文" else "中文"
                            Text("下载${text}成绩单")
                        }
                    }
                }
            }

        }
    }

}


fun downloadSchoolCalendar() {
    CoroutineScope(Dispatchers.IO).launch {
        OtherFunctionNetworkRepository.downloadSchoolCalendar()
    }
}

fun downloadGradeList(isEnglish: Boolean) {
    CoroutineScope(Dispatchers.IO).launch {
        OtherFunctionNetworkRepository.downloadGradeList(isEnglish)
    }
}