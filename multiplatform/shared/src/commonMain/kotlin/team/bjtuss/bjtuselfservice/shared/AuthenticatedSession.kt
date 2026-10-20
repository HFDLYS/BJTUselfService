package team.bjtuss.bjtuselfservice.shared

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import team.bjtuss.bjtuselfservice.shared.cache.AppPreferences
import team.bjtuss.bjtuselfservice.shared.data.home.HomeChangeFeedRepository
import team.bjtuss.bjtuselfservice.shared.auth.StudentProfile
import team.bjtuss.bjtuselfservice.shared.feature.classroom.ClassroomScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.classroomoccupancy.ClassroomOccupancyScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.course.CourseScheduleScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.courseware.CoursewareScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.exam.ExamScheduleScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.grade.GradeScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.home.HomeScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.homework.HomeworkScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.mailbox.MailboxScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.otherfunction.OtherFunctionScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.phyvlab.PhyVlabScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.settings.SettingsScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.shell.PHYSICS_DEMO_ROUTE_ID
import team.bjtuss.bjtuselfservice.shared.feature.shell.PHYSICS_DEMO_SETTINGS_ROUTE_ID
import team.bjtuss.bjtuselfservice.shared.feature.shell.PHYVLAB_SETTINGS_ROUTE_ID
import team.bjtuss.bjtuselfservice.shared.files.CoursewareDirectoryGateway
import team.bjtuss.bjtuselfservice.shared.files.HomeworkFileGateway
import team.bjtuss.bjtuselfservice.shared.calendar.SystemCalendarGateway

/**
 * 登录后页面共享的应用级会话。
 *
 * 平台原生导航会为二级页面创建新的 Activity/UIViewController，但它们必须继续观察同一组
 * ScreenModel 和会话状态，不能各自重新登录或重新创建 Repository。此对象只在当前进程内
 * 存活，不负责落盘，也不持有 Activity、UIViewController 或 Window。
 */
class AuthenticatedSession(
    val profile: StudentProfile,
    entryLoggingIn: Boolean,
    val gradeModel: GradeScreenModel,
    val courseScheduleModel: CourseScheduleScreenModel,
    val examScheduleModel: ExamScheduleScreenModel,
    val homeworkModel: HomeworkScreenModel,
    val coursewareModel: CoursewareScreenModel,
    val otherFunctionModel: OtherFunctionScreenModel,
    val classroomModel: ClassroomScreenModel,
    val classroomOccupancyModel: ClassroomOccupancyScreenModel,
    val settingsModel: SettingsScreenModel,
    val loginSyncPreferences: AppPreferences,
    val mailboxModel: MailboxScreenModel,
    val phyVlabModel: PhyVlabScreenModel,
    val homeModel: HomeScreenModel,
    val homeChangeFeed: HomeChangeFeedRepository,
    val homeworkFileGateway: HomeworkFileGateway,
    val coursewareDirectoryGateway: CoursewareDirectoryGateway,
    val systemCalendarGateway: SystemCalendarGateway,
    val onLogout: () -> Unit,
    /** 业务会话失效时，在 App 内复用当前内存中的 CAS 凭据恢复。 */
    val reauthenticateSession: (suspend () -> Boolean)? = null,
    /** 刷新前探测 MIS 会话；返回 false 时由共享刷新协调器触发恢复。 */
    val probeSession: (suspend () -> Boolean)? = null,
    val physicsLabModel: team.bjtuss.bjtuselfservice.shared.feature.physicslab.PhysicsLabModel? = null,
    val citelModel: team.bjtuss.bjtuselfservice.shared.feature.citel.CitelModel? = null,
    val aggregateFilterStore: team.bjtuss.bjtuselfservice.shared.feature.assignment.AggregateAssignmentFilterStore? = null,
    val physicsDemoModel: team.bjtuss.bjtuselfservice.shared.feature.phydemo.PhysicsDemoModel? = null,
    val phyVlabAccountModel: team.bjtuss.bjtuselfservice.shared.feature.phyvlab.PhyVlabAccountModel? = null,
) {
    private val appResumeGenerationState = MutableStateFlow(0L)
    private val appForegroundGenerationState = MutableStateFlow(0L)
    private val appResumeMutex = Mutex()
    private var claimedAppResumeGeneration = 0L
    private var claimedAppForegroundGeneration = 0L
    private val foregroundRefreshPolicy = AppForegroundRefreshPolicy()
    private val idleClickRefreshPolicy = AppIdleClickRefreshPolicy()
    private val appIdleClickGenerationState = MutableStateFlow(0L)
    private var claimedAppIdleClickGeneration = 0L

    /**
     * M17：一级入口集合变化时通知宿主原生 tab 容器（例如「物理在线」开关会增减底栏项）。
     * 由宿主装配 tab 容器时赋值；Compose 状态变化不会自动传到 UIKit。
     */
    var onNativeTabItemsChanged: ((List<String>) -> Unit)? = null

    /**
     * 宿主玻璃 TabBar 实际占掉的底部高度（点；iOS 上 1pt == 1dp）。
     *
     * 宿主报告完整 tab bar frame 的高度（包含 frame 中的底部安全区）。
     * 应用壳据此为滚动内容末尾提供净空；滚动视口仍延伸到胶囊下方。
     */
    var glassTabBarBottomInsetDp: Float by mutableStateOf(0f)

    /** True when UIKit already bounds the body beside/below its adaptive system bars. */
    var systemManagedContentBounds: Boolean by mutableStateOf(false)

    /**
     * 窗口底部系统安全区（Home Indicator / 导航条），点；iOS 上 1pt == 1dp。
     *
     * 全出血宿主里 Compose 的 `WindowInsets.navigationBars` 可能是 0。
     * 没有底栏的二级页使用这份安全区；一级页使用完整底栏 frame，不重复相加。
     * Android 打开新 Activity 前保存当前窗口已测得的导航条边距，避免首帧为零后课表收缩。
     */
    var systemBottomInsetDp: Float by mutableStateOf(0f)

    /**
     * 宿主原生导航栏实际占掉的顶部高度（点；iOS 上 1pt == 1dp），即导航栏 frame.maxY。
     *
     * 与底栏同一道理：内容要能伸进导航栏下面，原生 blur 才有东西可采样，而 UIKit 不会把
     * 导航栏算进 Compose 的可用 inset（`WindowInsets.statusBars` 停在栏底），所以栏底高度
     * 只能由宿主在布局时写进来。只在 opt-in 的页面以滚动内容顶边距消费（`LocalTopBarClearance`），
     * 未 opt-in 的页面保持原来的 Spacer 占位，行为不变。
     */
    var glassTopBarInsetDp: Float by mutableStateOf(0f)

    /** 平台回到前台时递增；应用壳会针对当前页面的失效请求自动重试一次。 */
    val appResumeGeneration: StateFlow<Long> = appResumeGenerationState.asStateFlow()
    /** iOS／桌面后台满阈值的全局刷新事件；不是每次聚焦事件。 */
    val appForegroundGeneration: StateFlow<Long> = appForegroundGenerationState.asStateFlow()

    /** 短暂切换窗口不访问网络；后台满 15 分钟后，回前台全局刷新一次。 */
    fun notifyAppBecameActive() {
        if (foregroundRefreshPolicy.foreground()) appForegroundGenerationState.update { it + 1L }
    }

    val appIdleClickGeneration: StateFlow<Long> = appIdleClickGenerationState.asStateFlow()

    /** Windows: no work happens until the next content click after 15 idle minutes. */
    fun notifyFunctionClicked() {
        if (idleClickRefreshPolicy.clicked()) appIdleClickGenerationState.update { it + 1L }
    }

    internal suspend fun claimAppIdleClickRefresh(generation: Long): Boolean = appResumeMutex.withLock {
        if (generation <= claimedAppIdleClickGeneration) false
        else { claimedAppIdleClickGeneration = generation; true }
    }

    fun notifyAppBecameInactive() {
        foregroundRefreshPolicy.background()
    }

    internal suspend fun claimAppForegroundRefresh(generation: Long): Boolean = appResumeMutex.withLock {
        if (generation <= claimedAppForegroundGeneration) false
        else { claimedAppForegroundGeneration = generation; true }
    }

    /** Page navigation may recover expired school requests without triggering a full CITEL sync. */
    fun notifyPageBecameActive() {
        appResumeGenerationState.update { it + 1L }
    }

    internal suspend fun claimAppResume(generation: Long): Boolean = appResumeMutex.withLock {
        if (generation <= 0L || generation <= claimedAppResumeGeneration) {
            false
        } else {
            claimedAppResumeGeneration = generation
            true
        }
    }

    /**
     * 智慧教学明文 HTTP 风险提示是否已在本登录态关闭。
     * 必须挂在 session 上：原生 push 的二级页会新建 Compose 树，
     * 若只用 remember，每次进课件/作业都会再弹一次。
     */
    var legacyHttpWarningDismissed: Boolean = false

    /**
     * 教室人数估计首页引导 Banner（请选择教学楼 / 人数仅供参考）
     * 是否已在本登录态关闭。
     */
    var classroomIntroBannerDismissed: Boolean = false

    /**
     * 静默入场期间的「登录中」标记。必须是可观察状态而不是构造常量：
     * 原生壳（M17 玻璃 TabBar）按会话实例装配一级入口，若登录完成就换一个新实例，
     * 整条 tab 栏会重建、各 tab 的返回栈被丢弃。改为在既有实例上更新，宿主只换状态不换人。
     */
    var entryLoggingIn: Boolean by mutableStateOf(entryLoggingIn)
}

/**
 * 可压入原生导航的页面，包括独立账号设置；是否实际压栈由窗口宽度与底栏配置决定。
 * Android 紧凑窗口会用此处校验 Activity 路由，遗漏会导致页面打开后立即关闭。
 */
fun isNativeDetailRoute(routeId: String): Boolean =
    team.bjtuss.bjtuselfservice.shared.feature.shell.AppSection.entries.any {
        it.name == routeId && it != team.bjtuss.bjtuselfservice.shared.feature.shell.AppSection.HOME &&
            it != team.bjtuss.bjtuselfservice.shared.feature.shell.AppSection.MORE
    } || routeId == "CLASSROOM_DETAIL" ||
        routeId == "CLASSROOM_OCCUPANCY_DETAIL" ||
        routeId == "HOMEWORK_DETAIL" ||
        routeId == "EXAM_DETAIL" ||
        routeId == "MAILBOX_DETAIL" ||
        routeId == "MAILBOX_COMPOSE" ||
        routeId == "CITEL_DETAIL" ||
        routeId == "CITEL_SETTINGS" ||
        routeId == "PHYVLAB_DETAIL" ||
        routeId == PHYVLAB_SETTINGS_ROUTE_ID ||
        routeId == PHYSICS_DEMO_SETTINGS_ROUTE_ID ||
        routeId == PHYSICS_DEMO_ROUTE_ID ||
        routeId == "PHYSICS_LAB_SETTINGS"
