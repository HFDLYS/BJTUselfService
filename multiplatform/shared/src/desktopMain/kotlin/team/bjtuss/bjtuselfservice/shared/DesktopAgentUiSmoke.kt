package team.bjtuss.bjtuselfservice.shared

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import team.bjtuss.bjtuselfservice.shared.auth.Credentials
import team.bjtuss.bjtuselfservice.shared.cache.AppPreferences
import team.bjtuss.bjtuselfservice.shared.cache.CacheStore
import team.bjtuss.bjtuselfservice.shared.cache.db.CacheDatabaseSql
import team.bjtuss.bjtuselfservice.shared.feature.citel.*
import team.bjtuss.bjtuselfservice.shared.feature.settings.SettingsScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.settings.SettingsWorkspace
import team.bjtuss.bjtuselfservice.shared.files.*
import team.bjtuss.bjtuselfservice.shared.update.AppUpdateChecker

/** Native desktop UI verification with in-memory fixtures; never touches accounts or school network. */
@Composable
fun DesktopAgentUiSmoke() {
    val cache = remember {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CacheDatabaseSql.Schema.create(driver)
        CacheStore(driver)
    }
    DisposableEffect(cache) { onDispose { cache.close() } }
    val task = remember { CitelTask(1, 1, "测试课程", "顺序表练习（测试题）", "$CITEL_BASE/mod/programming/view.php?id=1", true) }
    val model = remember {
        CitelModel("fixture", cache, null, object : CitelDataSource {
            override suspend fun fetch(credentials: Credentials) = listOf(task)
            override suspend fun programmingStatus(credentials: Credentials, task: CitelTask) =
                CitelProgrammingResult("AC", true, emptyList(), false)
            override suspend fun problem(credentials: Credentials, task: CitelTask, withResults: Boolean) =
                CitelProblem(task.title, "Grade: 10 / Input:console", "<h4>输入说明</h4><p>读取整数 n 并输出平方。</p><pre>2\n4</pre>",
                    cases = listOf(CitelProblemCase(1, "公开样例", input = "2\n", expected = "4\n")))
        })
    }
    val settings = remember { SettingsScreenModel(AppPreferences(), { true }, { true }, { true }, { AppUpdateChecker.Result.Unavailable }, prepareUninstall = { true }) }
    var showSettings by remember { mutableStateOf(false) }
    var dark by remember { mutableStateOf(false) }
    LaunchedEffect(model) { model.configure("fixture", "fixture", false, true); model.showTask(task) }
    PlatformAppTheme(useDarkTheme = dark, dynamicColorEnabled = false) {
        Surface(Modifier.fillMaxSize()) {
            Column {
                Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showSettings = false }) { Text("CITEL 测试页") }
                    TextButton(onClick = { showSettings = true }) { Text("设置测试页") }
                    TextButton(onClick = { dark = !dark }) { Text(if (dark) "浅色" else "深色") }
                }
                if (showSettings) SettingsWorkspace(settings,"测试账号",currentPlatform(),expanded = false,onLogout = {},modifier = Modifier.weight(1f))
                else CitelDetailWorkspace(model,UnavailableHomeworkFileGateway,{},modifier = Modifier.weight(1f))
            }
        }
    }
}
