package team.bjtuss.bjtuselfservice.shared.feature.phydemo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import team.bjtuss.bjtuselfservice.shared.feature.settings.IndependentAccountSettings
import team.bjtuss.bjtuselfservice.shared.feature.scroll.desktopTouchScroll
import team.bjtuss.bjtuselfservice.shared.feature.shell.LocalBottomBarClearance
import team.bjtuss.bjtuselfservice.shared.feature.shell.LocalTopBarClearance
import team.bjtuss.bjtuselfservice.shared.feature.shell.TopScrollColumn
import team.bjtuss.bjtuselfservice.shared.platformLoginKeyboardAvoidance

@Composable
fun PhysicsDemoScreen(model: PhysicsDemoModel, modifier: Modifier = Modifier) {
    val state by model.state.collectAsState()
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var ready by remember { mutableStateOf(false) }
    var showAccount by remember { mutableStateOf(false) }
    LaunchedEffect(model) {
        model.initialize(); username = model.state.value.username; ready = true
        showAccount = !model.state.value.configured
    }
    if (showAccount) {
        IndependentAccountSettings(username,password,state.configured && username.trim() == state.username,
            ready,state.refreshing,{username = it},{password = it}, onSave = {
                scope.launch { if (model.configure(username,password)) { password = ""; showAccount = false } }
            },message = state.message,failed = state.failed,modifier = modifier,
            websiteUrl = "http://phydemo.bjtu.edu.cn/",canClear = state.configured,
            onClear = { scope.launch { model.clearConfiguration(); password = "" } })
        return
    }
    PhysicsDemoResults(state, { enabled -> scope.launch { model.setEnabled(enabled) } },
        { scope.launch { model.refresh() } }, { showAccount = true }, modifier)
}

@Composable
internal fun PhysicsDemoResults(state: PhysicsDemoState, onEnabled: (Boolean) -> Unit,
    onRefresh: () -> Unit, onAccountSettings: () -> Unit, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    TopScrollColumn(state = scroll,modifier = modifier.fillMaxSize().platformLoginKeyboardAvoidance(enabled = true).desktopTouchScroll(scroll),
        contentModifier = Modifier.padding(start = 20.dp,end = 20.dp,
            top = 20.dp + LocalTopBarClearance.current,bottom = 20.dp + LocalBottomBarClearance.current),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("连接校园网查询已预约的演示实验，按实际时间显示在课程表，并可导出到日历。",
            style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.SpaceBetween,verticalAlignment = Alignment.CenterVertically) {
            Text("显示与同步演示实验",Modifier.weight(1f))
            Switch(state.enabled,onEnabled,enabled = !state.refreshing)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onRefresh,enabled = state.enabled && !state.refreshing) { Text(if (state.refreshing) "查询中…" else "刷新预约") }
            OutlinedButton(onClick = onAccountSettings,enabled = !state.refreshing) { Text("独立账号设置") }
        }
        state.message?.let { Text(it,color = if (state.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
        if (state.fromCache) Text("当前为上次查询的缓存",style = MaterialTheme.typography.labelMedium)
        state.lastSync?.takeIf { it.isNotBlank() }?.let { Text("上次查询：${it.replace('T', ' ').take(19)}",style = MaterialTheme.typography.bodySmall) }
        physicsDemoNames.forEach { name ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp),verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(name,style = MaterialTheme.typography.titleMedium)
                    val bookings = state.bookings.filter { it.name == name }
                    if (bookings.isEmpty()) Text("尚未预约",color = MaterialTheme.colorScheme.onSurfaceVariant)
                    bookings.forEach { booking ->
                        Text("${booking.date} · ${booking.timeRange}")
                        Text("第七教学楼 ${booking.location}",style = MaterialTheme.typography.bodyMedium)
                        if (booking.teacher.isNotBlank()) Text("助教：${booking.teacher}",style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
