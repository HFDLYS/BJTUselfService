package team.bjtuss.bjtuselfservice.shared.feature.phydemo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
    // 页面先显示缓存；若本次会话尚未成功查询（如登录同步还没跑到），自动补一次。
    LaunchedEffect(model) { model.refresh(onlyIfUnsynced = true) }
    PhysicsDemoResults(state, { scope.launch { model.refresh() } }, modifier)
}

@Composable
fun PhysicsDemoSettingsScreen(model: PhysicsDemoModel, modifier: Modifier = Modifier,
    onAccountSaved: () -> Unit = {}) {
    val state by model.state.collectAsState()
    val scope = rememberCoroutineScope()
    var username by remember(model) { mutableStateOf("") }
    var password by remember(model) { mutableStateOf("") }
    var ready by remember(model) { mutableStateOf(false) }
    LaunchedEffect(model) {
        model.initialize()
        username = model.state.value.username
        ready = true
    }
    IndependentAccountSettings(username, password, state.configured && username.trim() == state.username,
        ready, state.refreshing, { username = it }, { password = it }, onSave = {
            scope.launch {
                if (model.configure(username, password)) {
                    password = ""
                    onAccountSaved()
                }
            }
        }, message = state.message, failed = state.failed, modifier = modifier,
        websiteUrl = "http://phydemo.bjtu.edu.cn/",
        websiteRedirectUrl = "https://physpace.bjtu.edu.cn:8089/phydemo/login",
        canClear = state.configured,
        onClear = { scope.launch { model.clearConfiguration(); password = "" } })
}

@Composable
internal fun PhysicsDemoResults(state: PhysicsDemoState,
    onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    TopScrollColumn(state = scroll,modifier = modifier.fillMaxSize().platformLoginKeyboardAvoidance(enabled = true).desktopTouchScroll(scroll),
        contentModifier = Modifier.padding(start = 20.dp,end = 20.dp,
            top = 20.dp + LocalTopBarClearance.current,bottom = 20.dp + LocalBottomBarClearance.current),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Button(onClick = onRefresh, enabled = state.enabled && !state.refreshing) {
            Text(if (state.refreshing) "查询中…" else "刷新预约")
        }
        state.message?.let { Text(it,color = if (state.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
        if (state.fromCache) Text("当前为上次查询的缓存",style = MaterialTheme.typography.labelMedium)
        state.lastSync?.takeIf { it.isNotBlank() }?.let { Text("上次查询：${it.replace('T', ' ').take(19)}",style = MaterialTheme.typography.bodySmall) }
        // 与物理实验一致：按实验日期先后逐条显示，未预约的实验排在最后。
        sortedPhysicsDemoBookings(state.bookings).forEach { booking ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp),verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(booking.name,style = MaterialTheme.typography.titleMedium)
                    Text("${booking.date} · ${booking.timeRange}")
                    Text("第七教学楼 ${booking.location}",style = MaterialTheme.typography.bodyMedium)
                    if (booking.teacher.isNotBlank()) Text("助教：${booking.teacher}",style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        physicsDemoNames.filter { name -> state.bookings.none { it.name == name } }.forEach { name ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp),verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(name,style = MaterialTheme.typography.titleMedium)
                    Text("尚未预约",color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
