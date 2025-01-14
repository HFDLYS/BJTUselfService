package team.bjtuss.bjtuselfservice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BorderAll
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import team.bjtuss.bjtuselfservice.screen.CourseScheduleScreen
import team.bjtuss.bjtuselfservice.screen.EmailScreen
import team.bjtuss.bjtuselfservice.screen.GradeScreen
import team.bjtuss.bjtuselfservice.screen.HomeScreen
import team.bjtuss.bjtuselfservice.screen.LoginScreen
import team.bjtuss.bjtuselfservice.screen.LoginViewModel
import team.bjtuss.bjtuselfservice.screen.ScreenStatus
import team.bjtuss.bjtuselfservice.screen.SettingScreen
import team.bjtuss.bjtuselfservice.screen.SpaceScreen
import team.bjtuss.bjtuselfservice.ui.theme.BJTUselfServicecomposeTheme
import team.bjtuss.bjtuselfservice.viewmodel.CourseScheduleViewModel
import team.bjtuss.bjtuselfservice.viewmodel.GradeViewModel
import kotlinx.coroutines.launch


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Model.init(this)

        setContent {
            BJTUselfServicecomposeTheme(dynamicColor = true) {
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

@Composable
fun App(loginViewModel: LoginViewModel) {
    val navController = rememberNavController()


    val gradeViewModel: GradeViewModel = viewModel()
    val courseScheduleViewModel: CourseScheduleViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = RouteManager.Navigation,
        enterTransition = { activityEnterTransition() },
        exitTransition = { activityExitTransition() },
        popEnterTransition = { activityPopEnterTransition() },
        popExitTransition = { activityPopExitTransition() }
    ) {
        composable(RouteManager.Navigation) {
            AppNavigation(navController, loginViewModel)
        }
        composable(RouteManager.CourseSchedule) {
            CourseScheduleScreen(courseScheduleViewModel)
        }
        composable(RouteManager.HomeWorkAndExam) {
            Greeting("Android")
        }
        composable(RouteManager.ClassroomPeopleEstimation) {
            SettingScreen(loginViewModel)
        }
        composable(RouteManager.BJTUMiaoMiaoHouse) {
            EmailScreen()
        }
        composable(RouteManager.Grade) {
            GradeScreen(gradeViewModel)
        }
    }


}


data class PageItem(
    val route: String,
    val title: String,
    val icon: ImageVector
)

object RouteManager {
    const val Home: String = "Home"
    const val Space: String = "Space"
    const val Setting: String = "Setting"
    const val Navigation: String = "Navigation"
    const val HomeWorkAndExam: String = "HomeWorkAndExam"
    const val ClassroomPeopleEstimation: String = "ClassroomPeopleEstimation"
    const val BJTUMiaoMiaoHouse: String = "BJTUMiaoMiaoHouse"
    const val Grade: String = "Grade"
    const val CourseSchedule: String = "CourseSchedule"
}

@Composable
fun AppNavigation(navController: NavController, loginViewModel: LoginViewModel) {
    val pages = listOf(
        PageItem(RouteManager.Home, "首页", Icons.Default.Home),
        PageItem(RouteManager.Space, "空间", Icons.Default.BorderAll),
        PageItem(RouteManager.Setting, "设置", Icons.Default.Settings)
    )

    val pagerState = rememberPagerState(pageCount = { pages.size })

    var targetPage by remember { mutableIntStateOf(pagerState.currentPage) }
    val coroutineScope = rememberCoroutineScope()
    Scaffold(bottomBar = {

        NavigationBar {
            pages.forEachIndexed { index, pageItem ->
                NavigationBarItem(
                    selected = targetPage == index,
                    onClick = {
                        targetPage = index
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    icon = {
                        Icon(imageVector = pageItem.icon, contentDescription = pageItem.title)
                    },
                    label = {
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
                    HomeScreen()
                }

                1 -> {
                    SpaceScreen(navController = navController)
                }

                2 -> {
                    SettingScreen(loginViewModel = loginViewModel)
                }
            }
        }

    })


}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}


private const val DEFAULT_ENTER_DURATION = 300
private const val DEFAULT_EXIT_DURATION = 220

private fun AnimatedContentTransitionScope<NavBackStackEntry>.activityEnterTransition(): EnterTransition {
    return slideIntoContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Start,
        animationSpec = tween(DEFAULT_ENTER_DURATION, easing = LinearOutSlowInEasing),
        initialOffset = { it }
    )
}

@Suppress("UnusedReceiverParameter")
private fun AnimatedContentTransitionScope<NavBackStackEntry>.activityExitTransition(): ExitTransition {
    return scaleOut(
        animationSpec = tween(DEFAULT_ENTER_DURATION),
        targetScale = 1F
    )
}


@Suppress("UnusedReceiverParameter")
private fun AnimatedContentTransitionScope<NavBackStackEntry>.activityPopEnterTransition(): EnterTransition {
    return scaleIn(
        animationSpec = tween(DEFAULT_EXIT_DURATION),
        initialScale = 1F
    )
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.activityPopExitTransition(): ExitTransition {
    return slideOutOfContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.End,
        animationSpec = tween(DEFAULT_EXIT_DURATION, easing = FastOutLinearInEasing),
        targetOffset = { it }
    )
}


