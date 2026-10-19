package team.bjtuss.bjtuselfservice.shared.feature.shell

import team.bjtuss.bjtuselfservice.shared.AuthenticatedSession

/**
 * M17 原生壳桥接：把紧凑端一级入口交给系统容器（iOS UIKit tab 容器 / Android
 * Activity 宿主），标题与返回同样可由宿主导航栏承载。这里只暴露 Swift/Objective-C
 * 侧需要读取的纯数据，AppSection 等壳内类型保持 internal 不外泄。
 */
data class NativeTabItem(
    val routeId: String,
    val title: String,
)

/**
 * 宿主导航栏右侧的动作区，以及没有右侧动作时仍需同步给 UIKit 的滚动状态。
 *
 * 二级页的刷新与同步状态原本自绘成单独一行胶囊，把内容区整整压掉一条；这两者本来就是
 * 导航栏级信息（Mail/Files 都在这里），所以在原生标题栏生效时交给宿主渲染。[onClick] 作为
 * ObjC block 导出给 Swift，点击直接回到本页的刷新闭包，不经过全局状态，pop 之后也不会串页。
 * 没有右侧动作的页面也会发送一个空动作对象，仅用于同步滚动状态；宿主不会为它画按钮。
 */
class NativeBarAction(
    /** 空闲时的状态文案，如「已同步」；空串表示本页没有状态要显示。 */
    val status: String,
    /** 是否提供刷新；false 时宿主只画状态，不放按钮。 */
    val canRefresh: Boolean,
    val busy: Boolean,
    val label: String,
    val onClick: () -> Unit,
    /**
     * 状态文案自己就是入口时非空（首页「同步失败」点开同步详情、课表点开失败明细）。
     * 有了它，宿主就能把状态画成按钮，Compose 侧那条唯一的入口不会再因为顶栏被撤掉而消失。
     */
    val onStatusClick: (() -> Unit)? = null,
    /** 页面级动作的文字（如课程表「添加到日历」）；null 表示本页没有。 */
    val extraLabel: String? = null,
    val onExtraClick: (() -> Unit)? = null,
    /** 页面滚动进度（0 为顶部，1 为越过顶栏过渡区），用于横向栏的玻璃材质。 */
    val scrollProgress: Float = 0f,
    /** Optional single-owner native scroller. Offsets are consumed, never estimated by item index. */
    val scrollController: NativeScrollController? = null,
)

/** 原生底栏实际承载的一级入口；物理在线开启后与主分支一样是一级 tab。 */
internal fun nativeTabSections(): List<AppSection> = bottomNavSections(true)

/** 紧凑端底部导航的原生镜像；来源与 [bottomNavSections] 同一份，避免两端漂移。 */
fun nativeTabItems(session: AuthenticatedSession): List<NativeTabItem> =
    bottomNavSections(session.settingsModel.state.value.preferences)
        .map { NativeTabItem(routeId = it.name, title = it.title) }

/** 兼容旧调用方的默认底栏判断；会话内导航使用当前偏好生成的入口集合。 */
fun isNativeTabRoute(routeId: String): Boolean =
    routeId.toAppRoute() in nativeTabSections()

/**
 * 目的地静态标题，供宿主在 push 动画开始时就放好标题栏（Compose 首帧回报有数百毫秒延迟）。
 * 返回空串表示这一页自己保留页内标题与返回入口（如写信，返回要先取消草稿），宿主据此不显示系统栏。
 * 动态标题（选中的教学楼、写信/回复邮件）随后由 [AuthenticatedAppShell] 回报覆盖。
 */
fun nativeRouteTitle(routeId: String): String {
    val route = routeId.toAppRoute() ?: return ""
    return when (route) {
        MailboxComposeRoute -> ""
        PhysicsLabSettingsRoute -> "物理实验账号设置"
        PhysicsDemoRoute -> "演示实验查询"
        PhysicsDemoSettingsRoute -> "演示实验账号设置"
        PhyVlabSettingsRoute -> "物理在线账号设置"
        ClassroomDetailRoute -> AppSection.CLASSROOMS.title
        ClassroomOccupancyDetailRoute -> AppSection.CLASSROOM_OCCUPANCY.title
        HomeworkDetailRoute -> "作业详情"
        ExamDetailRoute -> "考试详情"
        PhyVlabDetailRoute -> "物理作业详情"
        CitelDetailRoute -> "作业详情"
        CitelSettingsRoute -> "CITEL 账号设置"
        MailboxDetailRoute -> "邮件详情"
        is AppSection -> route.title
    }
}

/** A hosted page always keeps routing through its host, including after rotation/unfolding. */
internal fun usesNativeSecondaryRoutes(nativeNavigationEnabled: Boolean, windowClass: team.bjtuss.bjtuselfservice.shared.WindowClass, hostedDestination: Boolean): Boolean =
    nativeNavigationEnabled && (hostedDestination || windowClass != team.bjtuss.bjtuselfservice.shared.WindowClass.Expanded)

/** UIKit owns scrolling physics; Compose consumes the exact native delta for rendering. */
data class NativeScrollController(
    /** Exact ScrollState range, or -1 for a virtualized list whose total height is not known. */
    val maxOffsetPx: Float,
    val canScrollBackward: Boolean,
    val canScrollForward: Boolean,
    private val consume: (Float) -> Float,
    private val alignStart: () -> Unit,
) {
    fun consumeScroll(deltaPx: Float): Float = consume(deltaPx)
    /** Repair a layout/restoration anchor when UIKit is already at its origin. */
    fun alignToStart() = alignStart()
}
