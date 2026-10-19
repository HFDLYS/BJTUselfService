package team.bjtuss.bjtuselfservice.shared.feature.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import team.bjtuss.bjtuselfservice.shared.AuthenticatedSession
import team.bjtuss.bjtuselfservice.shared.PlatformInfo
import team.bjtuss.bjtuselfservice.shared.auth.CaptchaRecognizer
import team.bjtuss.bjtuselfservice.shared.auth.Credentials
import team.bjtuss.bjtuselfservice.shared.auth.SchoolLoginProtocol
import team.bjtuss.bjtuselfservice.shared.auth.SchoolSessionRecovery
import team.bjtuss.bjtuselfservice.shared.auth.SessionProbeResult
import team.bjtuss.bjtuselfservice.shared.auth.StudentProfile
import team.bjtuss.bjtuselfservice.shared.cache.AppPreferences
import team.bjtuss.bjtuselfservice.shared.cache.CacheStore
import team.bjtuss.bjtuselfservice.shared.data.classroom.DefaultClassroomRepository
import team.bjtuss.bjtuselfservice.shared.data.classroom.SchoolClassroomRemoteDataSource
import team.bjtuss.bjtuselfservice.shared.data.classroomoccupancy.DefaultClassroomOccupancyRepository
import team.bjtuss.bjtuselfservice.shared.data.classroomoccupancy.SchoolClassroomOccupancyRemoteDataSource
import team.bjtuss.bjtuselfservice.shared.data.course.CacheStoreCourseScheduleLocalDataSource
import team.bjtuss.bjtuselfservice.shared.data.course.DefaultCourseScheduleRepository
import team.bjtuss.bjtuselfservice.shared.data.course.SchoolCourseScheduleRemoteDataSource
import team.bjtuss.bjtuselfservice.shared.data.courseware.CacheStoreCoursewareLocalDataSource
import team.bjtuss.bjtuselfservice.shared.data.courseware.DefaultCoursewareRepository
import team.bjtuss.bjtuselfservice.shared.data.courseware.SchoolCoursewareRemoteDataSource
import team.bjtuss.bjtuselfservice.shared.data.exam.CacheStoreExamScheduleLocalDataSource
import team.bjtuss.bjtuselfservice.shared.data.exam.DefaultExamScheduleRepository
import team.bjtuss.bjtuselfservice.shared.data.exam.SchoolExamScheduleRemoteDataSource
import team.bjtuss.bjtuselfservice.shared.data.grade.CacheStoreGradeLocalDataSource
import team.bjtuss.bjtuselfservice.shared.data.grade.DefaultGradeRepository
import team.bjtuss.bjtuselfservice.shared.data.grade.SchoolGradeRemoteDataSource
import team.bjtuss.bjtuselfservice.shared.data.grade.SchoolTrainingProgramRemoteDataSource
import team.bjtuss.bjtuselfservice.shared.data.home.CacheStoreHomeChangeFeedRepository
import team.bjtuss.bjtuselfservice.shared.data.home.CacheStoreHomeStatusLocalDataSource
import team.bjtuss.bjtuselfservice.shared.data.home.DefaultHomeStatusRepository
import team.bjtuss.bjtuselfservice.shared.data.home.SchoolHomeStatusRemoteDataSource
import team.bjtuss.bjtuselfservice.shared.data.home.courseChangeRecorder
import team.bjtuss.bjtuselfservice.shared.data.home.examChangeRecorder
import team.bjtuss.bjtuselfservice.shared.data.home.gradeChangeRecorder
import team.bjtuss.bjtuselfservice.shared.data.home.homeworkChangeRecorder
import team.bjtuss.bjtuselfservice.shared.data.home.phyvlabChangeRecorder
import team.bjtuss.bjtuselfservice.shared.domain.homework.HomeworkFilterPreferences
import team.bjtuss.bjtuselfservice.shared.domain.homework.HomeworkFilterPreferencesStore
import team.bjtuss.bjtuselfservice.shared.data.homework.CacheStoreHomeworkLocalDataSource
import team.bjtuss.bjtuselfservice.shared.data.homework.DefaultHomeworkRepository
import team.bjtuss.bjtuselfservice.shared.data.homework.SchoolHomeworkRemoteDataSource
import team.bjtuss.bjtuselfservice.shared.data.otherfunction.DefaultOtherFunctionRepository
import team.bjtuss.bjtuselfservice.shared.data.otherfunction.SchoolOtherFunctionRemoteDataSource
import team.bjtuss.bjtuselfservice.shared.data.phyvlab.CacheStorePhyVlabLocalDataSource
import team.bjtuss.bjtuselfservice.shared.data.phyvlab.DefaultPhyVlabRepository
import team.bjtuss.bjtuselfservice.shared.data.phyvlab.PhyVlabSessionProtocol
import team.bjtuss.bjtuselfservice.shared.data.phyvlab.SchoolPhyVlabRemoteDataSource
import team.bjtuss.bjtuselfservice.shared.feature.classroom.ClassroomScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.classroomoccupancy.ClassroomOccupancyScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.course.CourseScheduleScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.courseware.CoursewareScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.exam.ExamScheduleScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.grade.GradeScreenModel
import team.bjtuss.bjtuselfservice.shared.cache.SyncSource
import team.bjtuss.bjtuselfservice.shared.cache.markSynced
import team.bjtuss.bjtuselfservice.shared.feature.course.saveAcademicWeeksIfChanged
import team.bjtuss.bjtuselfservice.shared.feature.home.HomeScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.homework.HomeworkScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.mailbox.MailboxScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.otherfunction.OtherFunctionScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.phyvlab.PhyVlabScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.settings.SettingsScreenModel
import team.bjtuss.bjtuselfservice.shared.files.CoursewareDirectoryGateway
import team.bjtuss.bjtuselfservice.shared.files.HomeworkFileGateway
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpTransport
import team.bjtuss.bjtuselfservice.shared.network.createSchoolHttpTransport
import team.bjtuss.bjtuselfservice.shared.security.AccountSecurityCoordinator
import team.bjtuss.bjtuselfservice.shared.calendar.SystemCalendarGateway
import team.bjtuss.bjtuselfservice.shared.smartPlatformEndpointFor
import team.bjtuss.bjtuselfservice.shared.update.AppUpdateChecker

/**
 * 已登录会话的组合根：在 Compose 内按账号装配全部 Repository/ScreenModel 并产出 [AuthenticatedSession]。
 * 从 LoginRoute 抽出，登录页只负责登录流程本身与会话生命周期通知。
 */
@Composable
internal fun rememberAuthenticatedSession(
    profile: StudentProfile,
    entryLoggingIn: Boolean,
    transport: SchoolHttpTransport,
    loginProtocol: SchoolLoginProtocol,
    cacheStore: CacheStore,
    platform: PlatformInfo,
    appPreferences: AppPreferences,
    onPreferencesChanged: (AppPreferences) -> Boolean,
    securityCoordinator: AccountSecurityCoordinator,
    captchaRecognizer: CaptchaRecognizer,
    username: String,
    password: String,
    homeworkFileGateway: HomeworkFileGateway,
    coursewareDirectoryGateway: CoursewareDirectoryGateway,
    systemCalendarGateway: SystemCalendarGateway,
    onLogout: (String) -> Unit,
    onPurgeLogout: (String) -> Unit,
): AuthenticatedSession {
    val smartPlatformEndpoint = remember(platform.family) {
        smartPlatformEndpointFor(platform.family)
    }
    val homeChangeFeed = remember(profile.studentId, cacheStore) {
        CacheStoreHomeChangeFeedRepository(profile.studentId, cacheStore)
    }
    val gradeRepository = remember(profile.studentId, cacheStore) {
        DefaultGradeRepository(
            accountScope = profile.studentId,
            local = CacheStoreGradeLocalDataSource(cacheStore),
            remote = SchoolGradeRemoteDataSource(transport),
            programRemote = SchoolTrainingProgramRemoteDataSource(transport),
            onSynced = { cacheStore.markSynced(profile.studentId, SyncSource.GRADES, kotlin.time.Clock.System.now().epochSeconds) },
        )
    }
    val gradeModel = remember(gradeRepository, homeChangeFeed) {
        GradeScreenModel(gradeRepository, gradeChangeRecorder(homeChangeFeed))
    }
    // M11/M12 共用同一校历与学期来源：避免课表日期跳转再建一套 bksy 请求。
    val classroomOccupancyRepository = remember {
        DefaultClassroomOccupancyRepository(
            remote = SchoolClassroomOccupancyRemoteDataSource(transport),
        )
    }
    val courseScheduleRepository = remember(profile.studentId, cacheStore, smartPlatformEndpoint) {
        DefaultCourseScheduleRepository(
            accountScope = profile.studentId,
            local = CacheStoreCourseScheduleLocalDataSource(cacheStore),
            remote = SchoolCourseScheduleRemoteDataSource(
                transport = transport,
                endpoint = smartPlatformEndpoint,
            ),
            onSynced = { cacheStore.markSynced(profile.studentId, SyncSource.COURSES, kotlin.time.Clock.System.now().epochSeconds) },
        )
    }
    val courseScheduleModel = remember(courseScheduleRepository, homeChangeFeed, classroomOccupancyRepository) {
        CourseScheduleScreenModel(
            repository = courseScheduleRepository,
            changeRecorder = courseChangeRecorder(homeChangeFeed),
            calendarRepository = classroomOccupancyRepository,
            onCurrentAcademicWeeks = { weeks ->
                cacheStore.saveAcademicWeeksIfChanged(profile.studentId, weeks)
            },
        )
    }
    val examScheduleRepository = remember(profile.studentId, cacheStore) {
        DefaultExamScheduleRepository(
            accountScope = profile.studentId,
            local = CacheStoreExamScheduleLocalDataSource(cacheStore),
            remote = SchoolExamScheduleRemoteDataSource(transport),
            onSynced = { cacheStore.markSynced(profile.studentId, SyncSource.EXAMS, kotlin.time.Clock.System.now().epochSeconds) },
        )
    }
    val examScheduleModel = remember(examScheduleRepository, homeChangeFeed) {
        ExamScheduleScreenModel(examScheduleRepository, examChangeRecorder(homeChangeFeed))
    }
    val homeworkRepository = remember(profile.studentId, cacheStore, smartPlatformEndpoint) {
        DefaultHomeworkRepository(
            accountScope = profile.studentId,
            local = CacheStoreHomeworkLocalDataSource(cacheStore),
            remote = SchoolHomeworkRemoteDataSource(
                transport = transport,
                endpoint = smartPlatformEndpoint,
            ),
            onSynced = { cacheStore.markSynced(profile.studentId, SyncSource.HOMEWORK, kotlin.time.Clock.System.now().epochSeconds) },
        )
    }
    val homeworkModel = remember(homeworkRepository, homeChangeFeed) {
        HomeworkScreenModel(
            homeworkRepository,
            homeworkChangeRecorder(homeChangeFeed),
            filterStore = object : HomeworkFilterPreferencesStore {
                override fun load(): HomeworkFilterPreferences =
                    cacheStore.homeworkFilter(profile.studentId)

                override fun save(preferences: HomeworkFilterPreferences) {
                    cacheStore.saveHomeworkFilter(profile.studentId, preferences)
                }
            },
        )
    }
    val coursewareRepository = remember(profile.studentId, cacheStore, smartPlatformEndpoint) {
        DefaultCoursewareRepository(
            accountScope = profile.studentId,
            local = CacheStoreCoursewareLocalDataSource(cacheStore),
            remote = SchoolCoursewareRemoteDataSource(
                transport = transport,
                endpoint = smartPlatformEndpoint,
            ),
        )
    }
    val otherFunctionRepository = remember {
        DefaultOtherFunctionRepository(
            remote = SchoolOtherFunctionRemoteDataSource(transport),
        )
    }
    val otherFunctionModel = remember(otherFunctionRepository, homeworkFileGateway) {
        OtherFunctionScreenModel(otherFunctionRepository, homeworkFileGateway)
    }
    val classroomRepository = remember {
        DefaultClassroomRepository(
            remote = SchoolClassroomRemoteDataSource(createSchoolHttpTransport()),
        )
    }
    val classroomModel = remember(classroomRepository) {
        ClassroomScreenModel(classroomRepository)
    }
    val classroomOccupancyModel = remember(classroomOccupancyRepository) {
        ClassroomOccupancyScreenModel(
            repository = classroomOccupancyRepository,
            peopleRepository = classroomRepository,
            // 默认周跟随课表切片的学校当前教学周，课表未同步时回退第 1 周。
            currentWeekProvider = {
                courseScheduleModel.state.value.currentWeek.takeIf { it > 0 } ?: 1
            },
        )
    }
    val settingsModel = remember(profile.studentId, cacheStore) {
        SettingsScreenModel(
            initialPreferences = appPreferences.copy(
                physicsLabEnabled = appPreferences.physicsLabEnabled
                    ?: (cacheStore.metadata(profile.studentId, "physicslab.enabled") == "true"),
                citelEnabled = appPreferences.citelEnabled ?: (cacheStore.metadata(profile.studentId, "citel.enabled") == "true"),
            ),
            persistPreferences = onPreferencesChanged,
            clearAccountCache = {
                runCatching { cacheStore.clearAccount(profile.studentId) }.isSuccess
            },
            prepareUninstall = {
                val cleaner = team.bjtuss.bjtuselfservice.shared.feature.settings.platformLocalDataCleaner()
                val wiped = cleaner?.clearLocalData(cacheStore, removeAgent = true) == true
                if (wiped) onPurgeLogout(profile.studentId)
                wiped
            },
            wipeAllLocalData = {
                val cleaner = team.bjtuss.bjtuselfservice.shared.feature.settings.platformLocalDataCleaner()
                if (cleaner != null) {
                    val wiped = cleaner.clearLocalData(cacheStore)
                    if (wiped) onPurgeLogout(profile.studentId)
                    wiped
                } else {
                    val labAccounts = (cacheStore.metadataAccountScopes("physicslab.configured") + profile.studentId).distinct()
                    val labCredentialsPurged = runCatching {
                        labAccounts.forEach { securityCoordinator.physicsLabVault(it)?.clear() }
                        (cacheStore.metadataAccountScopes("phydemo.configured") + profile.studentId).distinct()
                            .forEach { securityCoordinator.physicsDemoVault(it)?.clear() }
                        (cacheStore.metadataAccountScopes("phyvlab.configured") + profile.studentId).distinct()
                            .forEach { securityCoordinator.phyVlabVault(it)?.clear() }
                        (cacheStore.metadataAccountScopes("citel.username") + profile.studentId).distinct()
                            .forEach { securityCoordinator.citelVault(it)?.clear() }
                    }.isSuccess
                    val cacheCleared = labCredentialsPurged && runCatching { cacheStore.clearAll() }.isSuccess
                    val credentialsPurged = securityCoordinator.purge()
                    val wiped = cacheCleared && credentialsPurged && labCredentialsPurged
                    // Stay signed out after a full wipe, or background sync could
                    // immediately recreate personal cache rows from this live session.
                    if (wiped) onPurgeLogout(profile.studentId)
                    wiped
                }
            },
            checkLatestRelease = { AppUpdateChecker.fetchLatest(transport) },
        )
    }
    val mailboxModel = remember(profile.studentId) {
        MailboxScreenModel(transport)
    }
    // 各学校业务会话的 Cookie 有不同生命周期。保留当前登录凭据的内存引用，
    // 让刷新发现会话过期时可以在 App 内自动恢复 CAS/教务链路，
    // 不把用户推到无法回传 Cookie 的系统浏览器。
    val latestCredentials = rememberUpdatedState(Credentials(username.trim(), password))
    val sessionRecovery = remember(loginProtocol, captchaRecognizer) {
        SchoolSessionRecovery(
            protocol = loginProtocol,
            captchaRecognizer = captchaRecognizer,
            credentialsProvider = {
                latestCredentials.value.takeIf(Credentials::isValid)
            },
        )
    }
    val coursewareModel = remember(coursewareRepository, sessionRecovery) {
        CoursewareScreenModel(
            repository = coursewareRepository,
            reauthenticate = sessionRecovery::attempt,
        )
    }
    val phyVlabRepository = remember {
        DefaultPhyVlabRepository(SchoolPhyVlabRemoteDataSource(transport))
    }
    // 物理在线不再走学校统一认证：MoodleSession 缺失时用独立账号登录，账号由设置页保存。
    // 协议与账号模型互相引用（协议读账号、账号保存前用协议校验），用单元素持有者打破初始化环。
    val phyVlabAccountHolder = remember(profile.studentId) {
        arrayOfNulls<team.bjtuss.bjtuselfservice.shared.feature.phyvlab.PhyVlabAccountModel>(1)
    }
    val phyVlabSessionProtocol = remember(phyVlabAccountHolder) {
        PhyVlabSessionProtocol(transport, credentials = { phyVlabAccountHolder[0]?.credentials() })
    }
    val phyVlabAccountModel = remember(profile.studentId, cacheStore, securityCoordinator, phyVlabSessionProtocol) {
        team.bjtuss.bjtuselfservice.shared.feature.phyvlab.PhyVlabAccountModel(
            profile.studentId, cacheStore, securityCoordinator.phyVlabVault(profile.studentId),
            signIn = phyVlabSessionProtocol::signInWith,
        ).also { phyVlabAccountHolder[0] = it }
    }
    val phyVlabLocalDataSource = remember(cacheStore) {
        CacheStorePhyVlabLocalDataSource(cacheStore)
    }
    val phyVlabModel = remember(
        phyVlabRepository,
        phyVlabSessionProtocol,
        phyVlabLocalDataSource,
        profile.studentId,
    ) {
        PhyVlabScreenModel(
            repository = phyVlabRepository,
            sessionProtocol = phyVlabSessionProtocol,
            localDataSource = phyVlabLocalDataSource,
            changeRecorder = phyvlabChangeRecorder(homeChangeFeed),
            accountScope = profile.studentId,
            filterStore = team.bjtuss.bjtuselfservice.shared.feature.assignment.AssignmentFilterStore(cacheStore, profile.studentId, "phyvlab"),
        )
    }
    val physicsLabModel = remember(profile.studentId, cacheStore, securityCoordinator) {
        team.bjtuss.bjtuselfservice.shared.feature.physicslab.PhysicsLabModel(
            profile.studentId, cacheStore, securityCoordinator.physicsLabVault(profile.studentId),
            team.bjtuss.bjtuselfservice.shared.feature.physicslab.PhysicsLabRemote(transport),
        )
    }
    val physicsDemoModel = remember(profile.studentId, cacheStore, securityCoordinator, transport) {
        team.bjtuss.bjtuselfservice.shared.feature.phydemo.PhysicsDemoModel(
            profile.studentId, cacheStore, securityCoordinator.physicsDemoVault(profile.studentId),
            team.bjtuss.bjtuselfservice.shared.feature.phydemo.PhysicsDemoRemote(transport),
        )
    }
    val homeStatusRepository = remember(profile.studentId, cacheStore) {
        DefaultHomeStatusRepository(
            accountScope = profile.studentId,
            local = CacheStoreHomeStatusLocalDataSource(cacheStore),
            remote = SchoolHomeStatusRemoteDataSource(transport),
        )
    }
    val homeModel = remember(homeStatusRepository) { HomeScreenModel(homeStatusRepository) }
    val citelModel = remember(profile.studentId, cacheStore, securityCoordinator, transport) {
        team.bjtuss.bjtuselfservice.shared.feature.citel.CitelModel(profile.studentId, cacheStore,
            securityCoordinator.citelVault(profile.studentId), team.bjtuss.bjtuselfservice.shared.feature.citel.CitelRemote(transport))
    }
    val session = remember(
        profile,
        gradeModel,
        courseScheduleModel,
        examScheduleModel,
        homeworkModel,
        coursewareModel,
        otherFunctionModel,
        classroomModel,
        classroomOccupancyModel,
        settingsModel,
        appPreferences,
        mailboxModel,
        phyVlabModel,
        phyVlabAccountModel,
        physicsDemoModel,
        homeModel,
        homeChangeFeed,
        homeworkFileGateway,
        coursewareDirectoryGateway,
        systemCalendarGateway,
        sessionRecovery,
    ) {
        AuthenticatedSession(
            profile = profile,
            entryLoggingIn = entryLoggingIn,
            gradeModel = gradeModel,
            courseScheduleModel = courseScheduleModel,
            examScheduleModel = examScheduleModel,
            homeworkModel = homeworkModel,
            coursewareModel = coursewareModel,
            otherFunctionModel = otherFunctionModel,
            classroomModel = classroomModel,
            classroomOccupancyModel = classroomOccupancyModel,
            settingsModel = settingsModel,
            loginSyncPreferences = appPreferences,
            mailboxModel = mailboxModel,
            phyVlabModel = phyVlabModel,
            physicsLabModel = physicsLabModel,
            physicsDemoModel = physicsDemoModel,
            phyVlabAccountModel = phyVlabAccountModel,
            citelModel = citelModel,
            aggregateFilterStore = team.bjtuss.bjtuselfservice.shared.feature.assignment.AggregateAssignmentFilterStore(cacheStore, profile.studentId),
            homeModel = homeModel,
            homeChangeFeed = homeChangeFeed,
            homeworkFileGateway = homeworkFileGateway,
            coursewareDirectoryGateway = coursewareDirectoryGateway,
            systemCalendarGateway = systemCalendarGateway,
            onLogout = { onLogout(profile.studentId) },
            reauthenticateSession = sessionRecovery::attempt,
            probeSession = {
                loginProtocol.checkSession() is SessionProbeResult.Active
            },
        )
    }
    // 登录完成只更新可观察状态，不换会话实例：M17 原生壳按会话实例装配一级入口，
    // 换实例等于重建整条玻璃 TabBar——各 tab 的返回栈被丢弃、所有 Compose 宿主重来。
    SideEffect { session.entryLoggingIn = entryLoggingIn }
    return session
}
