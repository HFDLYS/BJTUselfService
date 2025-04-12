package team.bjtuss.bjtuselfservice

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BorderAll
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.launch
import team.bjtuss.bjtuselfservice.MainApplication.Companion.appContext
import team.bjtuss.bjtuselfservice.RouteManager.ClassroomDetection
import team.bjtuss.bjtuselfservice.repository.fetchLatestRelease
import team.bjtuss.bjtuselfservice.screen.BuildingScreen
import team.bjtuss.bjtuselfservice.screen.ClassroomScreen
import team.bjtuss.bjtuselfservice.screen.CourseScheduleScreen
import team.bjtuss.bjtuselfservice.screen.CoursewareScreen
import team.bjtuss.bjtuselfservice.screen.EmailScreen
import team.bjtuss.bjtuselfservice.screen.ExamScheduleScreen
import team.bjtuss.bjtuselfservice.screen.GradeScreen
import team.bjtuss.bjtuselfservice.screen.HomeScreen
import team.bjtuss.bjtuselfservice.screen.HomeworkScreen
import team.bjtuss.bjtuselfservice.screen.LoginScreen
import team.bjtuss.bjtuselfservice.screen.OtherFunctionScreen
import team.bjtuss.bjtuselfservice.screen.SettingScreen
import team.bjtuss.bjtuselfservice.screen.SpaceScreen
import team.bjtuss.bjtuselfservice.ui.theme.BJTUselfServicecomposeTheme
import team.bjtuss.bjtuselfservice.utils.FilePickerManager
import team.bjtuss.bjtuselfservice.viewmodel.ClassroomViewModel
import team.bjtuss.bjtuselfservice.viewmodel.CourseScheduleViewModel
import team.bjtuss.bjtuselfservice.viewmodel.CoursewareViewModel
import team.bjtuss.bjtuselfservice.viewmodel.ExamScheduleViewModel
import team.bjtuss.bjtuselfservice.viewmodel.GradeViewModel
import team.bjtuss.bjtuselfservice.viewmodel.HomeworkViewModel
import team.bjtuss.bjtuselfservice.viewmodel.LoginViewModel
import team.bjtuss.bjtuselfservice.viewmodel.MainViewModel
import team.bjtuss.bjtuselfservice.viewmodel.MainViewModelFactory
import team.bjtuss.bjtuselfservice.viewmodel.ScreenStatus
import team.bjtuss.bjtuselfservice.viewmodel.SettingViewModel
import team.bjtuss.bjtuselfservice.viewmodel.StatusViewModel
import team.bjtuss.bjtuselfservice.web.ClassroomCapacityService
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        CaptchaModel.init(this)




        setContent {
            BJTUselfServicecomposeTheme(dynamicColor = true) {
                CheckForUpdate()
//                val filePickerLauncher = rememberLauncherForActivityResult(
//                    contract = ActivityResultContracts.GetContent(),
//                    onResult = { uri ->
//                        uri?.let {
//                            FilePickerManager.handleResult(it)
//                        }
//                    }
//                )
//                FilePickerManager.init(filePickerLauncher)


                Surface {
                    val loginViewModel: LoginViewModel = viewModel()
                    val screenStatus by loginViewModel.screenStatus.collectAsState()
                    when (screenStatus) {
                        is ScreenStatus.LoginScreen -> {
                            LoginScreen(loginViewModel)
                        }

                        is ScreenStatus.AppScreen -> {
                            App(loginViewModel)
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("CoroutineCreationDuringComposition")
@Composable
fun CheckForUpdate() {
    val versionName =
        appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
    var versionLatest by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var showDialog by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf("") }
    var downloadUrl by remember { mutableStateOf<String?>(null) }
    var updateMarkdown by remember { mutableStateOf("") }
    var hasChecked by remember { mutableStateOf(false) }

    scope.launch {
        if (hasChecked) return@launch
        hasChecked = true
        val release = fetchLatestRelease()
        updateMessage = release?.let {
            val instant = Instant.parse(it.publishedAt)
            val localDateTime = instant.atZone(ZoneId.systemDefault())
            "发布时间: ${
                localDateTime.format(
                    DateTimeFormatter.ofPattern(
                        "yyyy年M月d日 HH:mm",
                        Locale.getDefault()
                    )
                )
            }"
        } ?: "检查失败，请稍后再试"
        updateMarkdown = release?.body ?: ""
        versionLatest = release?.tagName ?: ""
        downloadUrl = release?.htmlUrl
        if (versionLatest.isNotEmpty() && (versionName < versionLatest))
            showDialog = true
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                downloadUrl?.let { url ->
                    Button(
                        onClick = {
                            showDialog = false
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            appContext.startActivity(intent)
                        }
                    ) {
                        Text("前往下载")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("人习于枸且非一日")
                }
            },
            text = {
                Column {
                    Text(
                        "发现新版本${versionLatest}！",
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                    Text(
                        updateMessage,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    LazyColumn {
                        item {
                            MarkdownText(
                                markdown = updateMarkdown
                            )
                        }
                    }
                }
            },
            title = { Text("检查更新") }
        )
    }
}

@Composable
fun App(loginViewModel: LoginViewModel) {
    val navController = rememberNavController()
    val classroomViewModel: ClassroomViewModel = viewModel()
    val gradeViewModel: GradeViewModel = viewModel()
    val courseScheduleViewModel: CourseScheduleViewModel = viewModel()
    val examScheduleViewModel: ExamScheduleViewModel = viewModel()
    val homeworkViewModel: HomeworkViewModel = viewModel()
    val statusViewModel: StatusViewModel = viewModel()
    val settingViewModel: SettingViewModel = viewModel()
    val coursewareViewModel: CoursewareViewModel = viewModel()
    val mainViewModel: MainViewModel = viewModel(

        factory = MainViewModelFactory(
            gradeViewModel,
            courseScheduleViewModel,
            examScheduleViewModel,
            classroomViewModel,
            homeworkViewModel,
            statusViewModel,
            settingViewModel,
            coursewareViewModel,
        )
    )





    NavHost(navController = navController,
        startDestination = RouteManager.Navigation,
        enterTransition = { activityEnterTransition() },
        exitTransition = { activityExitTransition() },
        popEnterTransition = { activityPopEnterTransition() },
        popExitTransition = { activityPopExitTransition() }) {
        composable(RouteManager.Navigation) {
            AppNavigation(navController, loginViewModel, mainViewModel)
        }
        composable(RouteManager.CourseSchedule) {
            CourseScheduleScreen(mainViewModel)
        }
        composable(RouteManager.ExamSchedule) {
            ExamScheduleScreen(mainViewModel)
        }
        composable(RouteManager.Building) {
            BuildingScreen(navController)
        }
        composable(
            route = ClassroomDetection,
            arguments = listOf(navArgument("buildingName") { type = NavType.StringType })
        ) {
            val buildingName = it.arguments?.getString("buildingName") ?: ""
            ClassroomScreen(buildingName = buildingName, mainViewModel)
        }
        composable(RouteManager.Grade) {
            GradeScreen(mainViewModel)
        }
        composable(RouteManager.Email) {
            EmailScreen()
        }
        composable(RouteManager.HomeWork) {
            HomeworkScreen(mainViewModel)
        }
        composable(RouteManager.OtherFunction) {
            OtherFunctionScreen()
        }
        composable(RouteManager.Courseware) {
            CoursewareScreen(mainViewModel)
        }


    }


}


data class PageItem(
    val route: String, val title: String, val icon: ImageVector
)

object RouteManager {
    const val Home: String = "Home"
    const val Space: String = "Space"
    const val Setting: String = "Setting"
    const val Navigation: String = "Navigation"
    const val ExamSchedule: String = "Exam"
    const val Building: String = "Building"
    const val ClassroomDetection: String = "ClassroomDetection/{buildingName}"
    const val Grade: String = "Grade"
    const val CourseSchedule: String = "CourseSchedule"
    const val Email: String = "Email"
    const val HomeWork: String = "HomeWork"
    const val OtherFunction: String = "OtherFunction"

    //    const val CoursePlatform: String = "CoursePlatform"
    const val Courseware: String = "Courseware"
}

@Composable
fun AppNavigation(
    navController: NavController, loginViewModel: LoginViewModel, mainViewModel: MainViewModel
) {
    val pages = listOf(
        PageItem(RouteManager.Home, "首页", Icons.Default.Home),
        PageItem(RouteManager.Space, "应用", Icons.Default.BorderAll),
        PageItem(RouteManager.Setting, "设置", Icons.Default.Settings)
    )

    val pagerState = rememberPagerState(pageCount = { pages.size })

    val clickSequence = remember { mutableStateListOf<Int>() }
    val answer = listOf(1, 2, 2, 0, 1, 1)
    var targetPage by remember { mutableIntStateOf(pagerState.currentPage) }
    val coroutineScope = rememberCoroutineScope()
    Scaffold(bottomBar = {

        NavigationBar {
            pages.forEachIndexed { index, pageItem ->
                NavigationBarItem(selected = targetPage == index, onClick = {
                    targetPage = index
                    clickSequence.add(index)
                    if (clickSequence.size > answer.size) {
                        clickSequence.removeAt(0)
                    }
                    if (clickSequence.toList() == answer) {
                        ClassroomCapacityService.ok = true
                    }
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(index)
                    }
                }, icon = {
                    Icon(imageVector = pageItem.icon, contentDescription = pageItem.title)
                }, label = {
                    Text(text = pageItem.title)
                })
            }
        }

    }, content = { paddingValues ->

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(paddingValues),
            userScrollEnabled = false
        ) { page ->
            when (page) {
                0 -> {
                    HomeScreen(
                        navController = navController, mainViewModel = mainViewModel
                    )
                }

                1 -> {
                    SpaceScreen(navController = navController)
                }

                2 -> {
                    SettingScreen(loginViewModel = loginViewModel, mainViewModel = mainViewModel)
                }
            }
        }

    })


}


private const val DEFAULT_ENTER_DURATION = 300
private const val DEFAULT_EXIT_DURATION = 220

private fun AnimatedContentTransitionScope<NavBackStackEntry>.activityEnterTransition(): EnterTransition {
    return slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Start,
        animationSpec = tween(DEFAULT_ENTER_DURATION, easing = LinearOutSlowInEasing),
        initialOffset = { it })
}

@Suppress("UnusedReceiverParameter")
private fun AnimatedContentTransitionScope<NavBackStackEntry>.activityExitTransition(): ExitTransition {
    return scaleOut(
        animationSpec = tween(DEFAULT_ENTER_DURATION), targetScale = 1F
    )
}


@Suppress("UnusedReceiverParameter")
private fun AnimatedContentTransitionScope<NavBackStackEntry>.activityPopEnterTransition(): EnterTransition {
    return scaleIn(
        animationSpec = tween(DEFAULT_EXIT_DURATION), initialScale = 1F
    )
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.activityPopExitTransition(): ExitTransition {
    return slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.End,
        animationSpec = tween(DEFAULT_EXIT_DURATION, easing = FastOutLinearInEasing),
        targetOffset = { it })
}


