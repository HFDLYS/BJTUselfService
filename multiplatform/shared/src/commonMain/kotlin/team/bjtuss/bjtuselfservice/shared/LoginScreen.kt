package team.bjtuss.bjtuselfservice.shared

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import org.jetbrains.compose.resources.decodeToImageBitmap
import team.bjtuss.bjtuselfservice.shared.auth.AuthenticationResult
import team.bjtuss.bjtuselfservice.shared.auth.AutomaticLoginCoordinator
import team.bjtuss.bjtuselfservice.shared.auth.AutomaticLoginResult
import team.bjtuss.bjtuselfservice.shared.auth.CaptchaChallenge
import team.bjtuss.bjtuselfservice.shared.auth.CaptchaRecognizer
import team.bjtuss.bjtuselfservice.shared.auth.ChallengeResult
import team.bjtuss.bjtuselfservice.shared.auth.Credentials
import team.bjtuss.bjtuselfservice.shared.auth.LoginEvent
import team.bjtuss.bjtuselfservice.shared.auth.LoginFailure
import team.bjtuss.bjtuselfservice.shared.auth.LoginState
import team.bjtuss.bjtuselfservice.shared.auth.SchoolLoginProtocol
import team.bjtuss.bjtuselfservice.shared.auth.SchoolSessionRecovery
import team.bjtuss.bjtuselfservice.shared.auth.StudentProfile
import team.bjtuss.bjtuselfservice.shared.auth.reduceLoginState
import team.bjtuss.bjtuselfservice.shared.cache.CacheOpenState
import team.bjtuss.bjtuselfservice.shared.cache.AppPreferences
import team.bjtuss.bjtuselfservice.shared.cache.CacheStoreHandle
import team.bjtuss.bjtuselfservice.shared.data.grade.CacheStoreGradeLocalDataSource
import team.bjtuss.bjtuselfservice.shared.data.grade.DefaultGradeRepository
import team.bjtuss.bjtuselfservice.shared.data.grade.SchoolGradeRemoteDataSource
import team.bjtuss.bjtuselfservice.shared.data.grade.SchoolTrainingProgramRemoteDataSource
import team.bjtuss.bjtuselfservice.shared.data.course.CacheStoreCourseScheduleLocalDataSource
import team.bjtuss.bjtuselfservice.shared.data.course.DefaultCourseScheduleRepository
import team.bjtuss.bjtuselfservice.shared.data.course.SchoolCourseScheduleRemoteDataSource
import team.bjtuss.bjtuselfservice.shared.data.exam.CacheStoreExamScheduleLocalDataSource
import team.bjtuss.bjtuselfservice.shared.data.exam.DefaultExamScheduleRepository
import team.bjtuss.bjtuselfservice.shared.data.exam.SchoolExamScheduleRemoteDataSource
import team.bjtuss.bjtuselfservice.shared.data.homework.CacheStoreHomeworkLocalDataSource
import team.bjtuss.bjtuselfservice.shared.data.homework.DefaultHomeworkRepository
import team.bjtuss.bjtuselfservice.shared.data.homework.SchoolHomeworkRemoteDataSource
import team.bjtuss.bjtuselfservice.shared.data.courseware.CacheStoreCoursewareLocalDataSource
import team.bjtuss.bjtuselfservice.shared.data.courseware.DefaultCoursewareRepository
import team.bjtuss.bjtuselfservice.shared.data.courseware.SchoolCoursewareRemoteDataSource
import team.bjtuss.bjtuselfservice.shared.data.otherfunction.DefaultOtherFunctionRepository
import team.bjtuss.bjtuselfservice.shared.data.otherfunction.SchoolOtherFunctionRemoteDataSource
import team.bjtuss.bjtuselfservice.shared.data.phyvlab.CacheStorePhyVlabLocalDataSource
import team.bjtuss.bjtuselfservice.shared.data.phyvlab.DefaultPhyVlabRepository
import team.bjtuss.bjtuselfservice.shared.data.phyvlab.PhyVlabSessionProtocol
import team.bjtuss.bjtuselfservice.shared.data.phyvlab.SchoolPhyVlabRemoteDataSource
import team.bjtuss.bjtuselfservice.shared.data.classroom.DefaultClassroomRepository
import team.bjtuss.bjtuselfservice.shared.data.classroom.SchoolClassroomRemoteDataSource
import team.bjtuss.bjtuselfservice.shared.data.classroomoccupancy.DefaultClassroomOccupancyRepository
import team.bjtuss.bjtuselfservice.shared.data.classroomoccupancy.SchoolClassroomOccupancyRemoteDataSource
import team.bjtuss.bjtuselfservice.shared.data.home.CacheStoreHomeStatusLocalDataSource
import team.bjtuss.bjtuselfservice.shared.data.home.DefaultHomeStatusRepository
import team.bjtuss.bjtuselfservice.shared.data.home.SchoolHomeStatusRemoteDataSource
import team.bjtuss.bjtuselfservice.shared.data.home.CacheStoreHomeChangeFeedRepository
import team.bjtuss.bjtuselfservice.shared.data.home.courseChangeRecorder
import team.bjtuss.bjtuselfservice.shared.data.home.examChangeRecorder
import team.bjtuss.bjtuselfservice.shared.data.home.gradeChangeRecorder
import team.bjtuss.bjtuselfservice.shared.data.home.homeworkChangeRecorder
import team.bjtuss.bjtuselfservice.shared.feature.course.CourseScheduleScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.exam.ExamScheduleScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.shell.AuthenticatedAppShell
import team.bjtuss.bjtuselfservice.shared.feature.scroll.desktopTouchScroll
import team.bjtuss.bjtuselfservice.shared.feature.grade.GradeScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.homework.HomeworkScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.courseware.CoursewareScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.otherfunction.OtherFunctionScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.phyvlab.PhyVlabScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.classroom.ClassroomScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.classroomoccupancy.ClassroomOccupancyScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.settings.SettingsScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.mailbox.MailboxScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.home.HomeScreenModel
import team.bjtuss.bjtuselfservice.shared.feature.shell.AppCommandBus
import team.bjtuss.bjtuselfservice.shared.feature.shell.AppleSheetOrAlert
import team.bjtuss.bjtuselfservice.shared.feature.shell.rememberAuthenticatedSession
import team.bjtuss.bjtuselfservice.shared.files.HomeworkFileGateway
import team.bjtuss.bjtuselfservice.shared.files.CoursewareDirectoryGateway
import team.bjtuss.bjtuselfservice.shared.network.createSchoolHttpTransport
import team.bjtuss.bjtuselfservice.shared.update.AppUpdateChecker
import team.bjtuss.bjtuselfservice.shared.security.AccountSecurityCoordinator
import team.bjtuss.bjtuselfservice.shared.security.AccountSecurityStore
import team.bjtuss.bjtuselfservice.shared.security.CredentialRestoreResult
import team.bjtuss.bjtuselfservice.shared.calendar.SystemCalendarGateway
import team.bjtuss.bjtuselfservice.shared.webview.clearSchoolWebViewData

@Composable
fun LoginRoute(
    platform: PlatformInfo,
    windowClass: WindowClass,
    accountSecurityStore: AccountSecurityStore,
    cacheStoreHandle: CacheStoreHandle,
    homeworkFileGateway: HomeworkFileGateway,
    coursewareDirectoryGateway: CoursewareDirectoryGateway,
    systemCalendarGateway: SystemCalendarGateway,
    appCommandBus: AppCommandBus?,
    appPreferences: AppPreferences,
    onPreferencesChanged: (AppPreferences) -> Boolean,
    captchaRecognizer: CaptchaRecognizer,
    nativeNavigationEnabled: Boolean,
    nativeTabBarEnabled: Boolean = false,
    onOpenNativeRoute: (String) -> Unit,
    onOpenExternalUrl: (String) -> Unit,
    onAuthenticatedSessionChanged: (AuthenticatedSession?) -> Unit,
) {
    val transport = remember {
        lazy(LazyThreadSafetyMode.NONE) { createSchoolHttpTransport() }
    }
    val protocol = remember {
        lazy(LazyThreadSafetyMode.NONE) {
            SchoolLoginProtocol(transport.value)
        }
    }
    val securityCoordinator = remember(accountSecurityStore) {
        AccountSecurityCoordinator(accountSecurityStore)
    }
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<LoginState>(LoginState.SignedOut) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var captchaAnswer by remember { mutableStateOf("") }
    var rememberCredentials by remember {
        mutableStateOf(securityCoordinator.canStoreCredentials)
    }
    var storageReady by remember { mutableStateOf(false) }
    var logoutCleanupPending by remember { mutableStateOf(false) }
    var storageMessage by remember { mutableStateOf<String?>(null) }
    var postLoginStorageFailure by remember { mutableStateOf<String?>(null) }
    var automationMessage by remember { mutableStateOf<String?>(null) }
    var manualDialogChallenge by remember { mutableStateOf<CaptchaChallenge?>(null) }
    var manualDialogAttempts by remember { mutableStateOf(0) }
    var manualDialogMessage by remember { mutableStateOf<String?>(null) }
    // 静默入场状态：账号之前登录过时，自动登录期间直接用缓存档案渲染主界面。
    var autoEntryProfile by remember { mutableStateOf<StudentProfile?>(null) }
    var silentAutoLogin by remember { mutableStateOf(false) }
    var autoLoginFailureAttempts by remember { mutableStateOf<Int?>(null) }
    val cacheStore = cacheStoreHandle.store

    suspend fun finishLogin(
        profile: StudentProfile,
        credentials: Credentials,
        persistCredentials: Boolean,
    ): LoginState {
        val linking = LoginState.LinkingAcademicSystem(profile)
        state = linking
        if (!protocol.value.linkAcademicSystem()) {
            return LoginState.Failed(LoginFailure.ACADEMIC_LINK_FAILED, canRetry = true)
        }
        val cacheReady = runCatching {
            cacheStore.claimLegacyAccountData(profile.studentId)
        }.isSuccess
        // 缓存档案快照：下次冷启动在自动登录完成前先用它渲染主界面。
        runCatching { cacheStore.saveCachedProfile(profile) }
        val stored = if (credentials.isValid) {
            securityCoordinator.persistAfterLogin(
                credentials = credentials,
                rememberCredentials = persistCredentials,
            )
        } else {
            true
        }
        postLoginStorageFailure = if (!stored && persistCredentials && credentials.isValid) {
            if (platform.family == PlatformFamily.IOS) {
                "当前 iOS 安装包无法访问系统 Keychain，登录信息没有保存。请使用已签名的设备或模拟器运行；应用不会改用明文存储。"
            } else {
                "登录成功，但系统安全存储操作失败，登录信息没有保存。"
            }
        } else {
            null
        }
        storageMessage = when {
            !cacheReady -> "登录成功，但本地缓存初始化失败。"
            !stored -> "登录成功，但系统安全存储操作失败。"
            persistCredentials && credentials.isValid -> "登录信息已保存到系统安全存储。"
            else -> null
        }
        return LoginState.SignedIn(profile)
    }

    suspend fun requestManualChallenge(
        credentials: Credentials,
        persistCredentials: Boolean,
        messageOnSuccess: String? = null,
        failureWithoutCaptcha: LoginFailure = LoginFailure.MALFORMED_RESPONSE,
    ): LoginState {
        val previousChallenge = manualDialogChallenge
        val result = try {
            protocol.value.requestCaptchaChallenge(credentials.username)
        } catch (_: Exception) {
            ChallengeResult.Failed(LoginFailure.NETWORK)
        }
        return when (result) {
            is ChallengeResult.SessionActive -> {
                manualDialogChallenge = null
                manualDialogMessage = null
                finishLogin(result.profile, credentials, persistCredentials)
            }
            is ChallengeResult.Ready if !result.challenge.requiresCaptcha -> {
                // 统一身份认证当前不要求验证码：没有可手动输入的内容，回到登录页就地说明失败原因。
                captchaAnswer = ""
                manualDialogChallenge = null
                manualDialogMessage = null
                LoginState.Failed(failureWithoutCaptcha, canRetry = true)
            }
            is ChallengeResult.Ready -> {
                captchaAnswer = ""
                manualDialogChallenge = result.challenge
                manualDialogMessage = messageOnSuccess
                LoginState.AwaitingCaptcha(result.challenge)
            }
            is ChallengeResult.Failed -> {
                manualDialogMessage = when (result.reason) {
                    LoginFailure.NETWORK -> "无法获取新的验证码，请检查网络后重试。"
                    else -> loginFailureMessage(result.reason)
                }
                previousChallenge?.let(LoginState::AwaitingCaptcha)
                    ?: LoginState.Failed(result.reason, canRetry = true)
            }
        }
    }

    suspend fun performAutomaticLogin(
        credentials: Credentials,
        persistCredentials: Boolean,
    ): LoginState {
        manualDialogChallenge = null
        manualDialogAttempts = 0
        manualDialogMessage = null
        captchaAnswer = ""
        state = LoginState.CheckingSession
        automationMessage = "正在检查登录，正在自动登录"
        val automaticResult = try {
            AutomaticLoginCoordinator(
                gateway = protocol.value,
                captchaRecognizer = captchaRecognizer,
            ).login(credentials) { attempt, maximum ->
                automationMessage = "正在自动登录（第 $attempt/$maximum 次）"
            }
        } catch (_: Exception) {
            null
        }
        val nextState = when (automaticResult) {
            is AutomaticLoginResult.SessionActive -> finishLogin(
                profile = automaticResult.profile,
                credentials = credentials,
                persistCredentials = persistCredentials,
            )
            is AutomaticLoginResult.Authenticated -> finishLogin(
                profile = automaticResult.profile,
                credentials = credentials,
                persistCredentials = persistCredentials,
            )
            is AutomaticLoginResult.ManualRequired if automaticResult.reason == LoginFailure.ACCOUNT_REJECTED &&
                !silentAutoLogin -> LoginState.Failed(LoginFailure.ACCOUNT_REJECTED, canRetry = true)
            is AutomaticLoginResult.ManualRequired -> {
                if (silentAutoLogin) {
                    // 静默入场时不打断主界面；改弹引导弹窗，由用户确认后回登录页。
                    autoLoginFailureAttempts = automaticResult.attempts
                    LoginState.SignedOut
                } else {
                    manualDialogAttempts = automaticResult.attempts
                    // 自动提交过的 challenge 可能已失效；进入手动恢复前始终再取一张新图。
                    // 只有“尚未提交、仅识别失败”的 challenge 可在刷新网络失败时临时保底。
                    manualDialogChallenge = automaticResult.challenge.takeIf {
                        automaticResult.reason == LoginFailure.CAPTCHA_RECOGNITION_FAILED
                    }
                    requestManualChallenge(
                        credentials = credentials,
                        persistCredentials = persistCredentials,
                        failureWithoutCaptcha = automaticResult.reason,
                    )
                }
            }
            null -> if (silentAutoLogin) {
                autoLoginFailureAttempts = 0
                LoginState.SignedOut
            } else {
                LoginState.Failed(LoginFailure.NETWORK, canRetry = true)
            }
        }
        // 静默入场时主界面可见：若用户在登录途中已退出，保留退出状态，不要用登录结果覆盖。
        val appliedState = if (state is LoginState.SignedOut && nextState !is LoginState.SignedOut) {
            LoginState.SignedOut
        } else {
            nextState
        }
        if (appliedState is LoginState.SignedIn) {
            silentAutoLogin = false
            autoEntryProfile = null
        }
        automationMessage = null
        state = appliedState
        return appliedState
    }

    fun startAutomaticLogin() {
        if (
            logoutCleanupPending ||
            state is LoginState.CheckingSession ||
            state is LoginState.SubmittingCredentials ||
            state is LoginState.LinkingAcademicSystem
        ) {
            return
        }
        val credentials = Credentials(username.trim(), password)
        if (!credentials.isValid) {
            state = LoginState.Failed(LoginFailure.INVALID_CREDENTIALS, canRetry = true)
            return
        }
        dismissPlatformKeyboard()
        state = LoginState.CheckingSession
        automationMessage = "正在检查登录，正在自动登录"
        scope.launch {
            performAutomaticLogin(credentials, rememberCredentials)
        }
    }

    LaunchedEffect(securityCoordinator, captchaRecognizer) {
        val restoredCredentials = when (val restored = securityCoordinator.restore()) {
            CredentialRestoreResult.Empty,
            CredentialRestoreResult.Unavailable,
            -> null
            CredentialRestoreResult.Failed -> {
                storageMessage = "无法读取已保存的登录信息，已恢复为手动登录。"
                null
            }
            is CredentialRestoreResult.Restored -> {
                username = restored.credentials.username
                password = restored.credentials.password
                rememberCredentials = true
                restored.credentials
            }
        }
        when (cacheStoreHandle.state) {
            CacheOpenState.RECOVERED_AFTER_RESET -> {
                storageMessage = "本地缓存损坏，已安全重建。"
            }
            CacheOpenState.MIGRATED_TO_ENCRYPTED -> {
                storageMessage = "本地缓存已升级为加密格式，请重新同步离线数据和设置。"
            }
            CacheOpenState.OPENED -> Unit
        }
        storageReady = true

        if (restoredCredentials != null) {
            // 这个账号之前登录过：跳过登录页直接进主界面，顶栏先显示“登录中”。
            autoEntryProfile = runCatching {
                cacheStore.cachedProfile(restoredCredentials.username)
            }.getOrNull()
            silentAutoLogin = autoEntryProfile != null
            performAutomaticLogin(restoredCredentials, persistCredentials = true)
        }
    }

    fun submitManualCaptcha(challenge: CaptchaChallenge) {
        val credentials = Credentials(username.trim(), password)
        if (!credentials.isValid || captchaAnswer.isBlank()) {
            manualDialogMessage = "请输入当前验证码答案。"
            return
        }
        manualDialogMessage = null
        state = LoginState.SubmittingCredentials
        scope.launch {
            state = try {
                when (val result = protocol.value.authenticateMis(credentials, challenge, captchaAnswer.trim())) {
                    is AuthenticationResult.Failed -> requestManualChallenge(
                        credentials = credentials,
                        persistCredentials = rememberCredentials,
                        messageOnSuccess = when (result.reason) {
                            LoginFailure.CAPTCHA_REJECTED ->
                                "验证码未通过，已为你更换一张，请重新输入。"
                            else -> loginFailureMessage(result.reason)
                        },
                        failureWithoutCaptcha = result.reason,
                    )
                    is AuthenticationResult.Success -> {
                        manualDialogChallenge = null
                        manualDialogAttempts = 0
                        manualDialogMessage = null
                        finishLogin(
                            profile = result.profile,
                            credentials = credentials,
                            persistCredentials = rememberCredentials,
                        )
                    }
                }
            } catch (_: Exception) {
                requestManualChallenge(
                    credentials = credentials,
                    persistCredentials = rememberCredentials,
                    messageOnSuccess = "网络连接失败，已为你更换一张验证码，请重试。",
                )
            }
        }
    }

    fun refreshManualChallenge() {
        val credentials = Credentials(username.trim(), password)
        if (!credentials.isValid) {
            manualDialogMessage = "账号或密码已失效，请返回重新填写。"
            return
        }
        manualDialogMessage = null
        state = LoginState.CheckingSession
        scope.launch {
            state = requestManualChallenge(
                credentials = credentials,
                persistCredentials = rememberCredentials,
            )
        }
    }

    fun logout(accountScope: String, fullWipe: Boolean = false) {
        if (logoutCleanupPending) return
        logoutCleanupPending = true
        val currentProtocol = if (protocol.isInitialized()) protocol.value else null
        username = ""
        password = ""
        captchaAnswer = ""
        rememberCredentials = securityCoordinator.canStoreCredentials
        automationMessage = null
        manualDialogChallenge = null
        manualDialogAttempts = 0
        manualDialogMessage = null
        autoEntryProfile = null
        silentAutoLogin = false
        autoLoginFailureAttempts = null
        storageMessage = null
        postLoginStorageFailure = null
        state = reduceLoginState(state, LoginEvent.Logout)
        // 退出后桌面小组件不能继续展示上一个账号的课表。
        team.bjtuss.bjtuselfservice.shared.feature.widget.writeCourseWidgetSnapshot(null)
        scope.launch {
            // CAS 请求需要当前 Cookie，但不应阻塞本机敏感数据清理；并发执行，
            // 最后再汇总服务端结果供用户查看。
            try {
                val serverLogout = async {
                    runCatching { currentProtocol?.logout() ?: true }.getOrDefault(false)
                }
                val webDataCleared = runCatching { clearSchoolWebViewData() }.getOrDefault(false)
                val secureCleared = runCatching {
                    if (fullWipe) securityCoordinator.purge() else securityCoordinator.clear()
                }.getOrDefault(false)
                val cacheCleared = accountScope.isBlank() || runCatching {
                    cacheStore.clearAccount(accountScope)
                }.isSuccess
                val serverSessionCleared = serverLogout.await()
                val failures = buildList {
                    if (!serverSessionCleared) add("学校服务端会话")
                    if (!webDataCleared) add("网页会话")
                    if (!secureCleared) add("系统安全存储")
                    if (!cacheCleared) add("本地缓存")
                }
                storageMessage = failures.takeIf(List<String>::isNotEmpty)
                    ?.joinToString(separator = "、", prefix = "会话已退出，但", postfix = "清除失败。")
            } finally {
                logoutCleanupPending = false
            }
        }
    }

    // 凭据恢复完成前不渲染任何内容：否则静默入场时登录页会先闪一帧。
    if (!storageReady) return

    // 静默入场且自动登录失败：在主界面上弹引导弹窗，确认后回到登录页。
    val failureAttempts = autoLoginFailureAttempts
    if (failureAttempts != null && autoEntryProfile != null) {
        fun returnToLoginAfterAutomaticFailure() {
            autoLoginFailureAttempts = null
            autoEntryProfile = null
            silentAutoLogin = false
            state = LoginState.SignedOut
        }
        AppleSheetOrAlert(
            onDismissRequest = ::returnToLoginAfterAutomaticFailure,
            title = "自动登录失败",
            confirmLabel = "返回登录页",
            onConfirm = ::returnToLoginAfterAutomaticFailure,
        ) {
            if (failureAttempts > 0) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "已自动尝试登录 $failureAttempts 次仍未成功。密码可能已修改，或验证码识别连续失败。" +
                            "请回到登录页确认账号和密码后重新登录。",
                    )
                    Text(
                        "如果你最近更新过版本，别担心，这是安全保护机制，请返回登录页，重新点击一次登录即可。",
                        fontWeight = FontWeight.Bold,
                    )
                }
            } else {
                Text("自动登录时网络连接失败。请回到登录页检查网络后重新登录。")
            }
        }
    }

    val signedIn = state as? LoginState.SignedIn
    // 静默入场：自动登录完成前，用缓存档案先把主界面渲染出来。
    val shellProfile = signedIn?.profile ?: autoEntryProfile
    if (shellProfile != null && protocol.isInitialized()) {
        val authenticatedSession = rememberAuthenticatedSession(
            profile = shellProfile,
            entryLoggingIn = silentAutoLogin && signedIn == null,
            transport = transport.value,
            loginProtocol = protocol.value,
            cacheStore = cacheStore,
            platform = platform,
            appPreferences = appPreferences,
            onPreferencesChanged = onPreferencesChanged,
            securityCoordinator = securityCoordinator,
            captchaRecognizer = captchaRecognizer,
            username = username,
            password = password,
            homeworkFileGateway = homeworkFileGateway,
            coursewareDirectoryGateway = coursewareDirectoryGateway,
            systemCalendarGateway = systemCalendarGateway,
            onLogout = { accountScope -> logout(accountScope) },
            onPurgeLogout = { accountScope -> logout(accountScope, fullWipe = true) },
        )
        SideEffect {
            onAuthenticatedSessionChanged(authenticatedSession)
        }
        DisposableEffect(onAuthenticatedSessionChanged) {
            onDispose { onAuthenticatedSessionChanged(null) }
        }
        if (nativeTabBarEnabled) {
            // M17 原生 tab 壳：一级入口各由宿主的一个独立 Compose 目的地承载。
            // 宿主只保留登录页与会话来源，这里不再渲染共享壳层，否则两份壳层并存
            // （双份聚合同步副作用 + 自绘底栏与系统玻璃 TabBar 叠在一起）。
            Spacer(modifier = Modifier.fillMaxSize())
        } else {
            AuthenticatedAppShell(
                session = authenticatedSession,
                platform = platform,
                windowClass = windowClass,
                appCommandBus = appCommandBus,
                nativeNavigationEnabled = nativeNavigationEnabled,
                onOpenNativeRoute = onOpenNativeRoute,
                onOpenExternalUrl = onOpenExternalUrl,
            )
        }
        postLoginStorageFailure?.let { message ->
            AppleSheetOrAlert(
                onDismissRequest = { postLoginStorageFailure = null },
                title = "登录信息未保存",
                confirmLabel = "知道了",
                onConfirm = { postLoginStorageFailure = null },
            ) {
                Text(message)
            }
        }
        return
    }

    val loginContent = @Composable {
        LoginScreen(
            platform = platform,
            windowClass = windowClass,
            state = state,
            username = username,
            password = password,
            canRememberCredentials = securityCoordinator.canStoreCredentials,
            rememberCredentials = rememberCredentials,
            storageReady = storageReady && !logoutCleanupPending,
            storageMessage = storageMessage,
            automationMessage = automationMessage,
            onUsernameChange = { username = it },
            onPasswordChange = { password = it },
            onRememberCredentialsChange = { enabled ->
                rememberCredentials = enabled
                scope.launch {
                    if (!securityCoordinator.setRememberCredentials(enabled)) {
                        storageMessage = if (enabled) {
                            "无法更新系统安全存储设置。"
                        } else {
                            "已取消保存，但系统安全存储清除失败。"
                        }
                    }
                }
            },
            onLogin = ::startAutomaticLogin,
        )
    }

    fun dismissFallbackChallenge() {
        // Treat abandoning a partially authenticated CAS challenge exactly like
        // logout: block another login until server, WebView, and vault cleanup ends.
        logout(accountScope = "")
    }

    val fallbackChallenge = manualDialogChallenge
    if (fallbackChallenge != null) {
        AppleSheetOrAlert(
            onDismissRequest = ::dismissFallbackChallenge,
            title = "请输入验证码",
            confirmLabel = if (state is LoginState.SubmittingCredentials) "正在登录…" else "继续登录",
            confirmEnabled = captchaAnswer.isNotBlank() &&
                state !is LoginState.SubmittingCredentials &&
                state !is LoginState.CheckingSession,
            onConfirm = { submitManualCaptcha(fallbackChallenge) },
            dismissLabel = "修改账号和密码",
            dismissEnabled = state !is LoginState.SubmittingCredentials &&
                state !is LoginState.CheckingSession,
            needsFullHeight = true,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "自动登录已尝试 ${manualDialogAttempts.coerceAtLeast(1)} 次，仍未成功。" +
                        "请根据图片输入本次验证码。这大概率是因为你的账号密码输入有误。请返回检查。" +
                        "如果你认为无误，可以手动输入验证码尝试登录。",
                )
                manualDialogMessage?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                CaptchaBlock(
                    challenge = fallbackChallenge,
                    loading = state is LoginState.CheckingSession,
                    answer = captchaAnswer,
                    enabled = state !is LoginState.SubmittingCredentials &&
                        state !is LoginState.CheckingSession,
                    onAnswerChange = { captchaAnswer = it },
                    onRefresh = ::refreshManualChallenge,
                    onSubmit = { submitManualCaptcha(fallbackChallenge) },
                )
            }
        }
    }

    if (platform.family == PlatformFamily.MacOS) {
        // macOS：右键输入框用 AWT JPopupMenu 渲染原生样式菜单（剪切/拷贝/粘贴/全选）。
        ProvideNativeTextContextMenu { loginContent() }
    } else {
        loginContent()
    }
}
@Composable
fun LoginScreen(
    platform: PlatformInfo,
    windowClass: WindowClass,
    state: LoginState,
    username: String,
    password: String,
    canRememberCredentials: Boolean,
    rememberCredentials: Boolean,
    storageReady: Boolean,
    storageMessage: String?,
    automationMessage: String?,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onRememberCredentialsChange: (Boolean) -> Unit,
    onLogin: () -> Unit,
) {
    val scroll = rememberScrollState()
    val focusManager = LocalFocusManager.current
    val busy = !storageReady ||
        state is LoginState.CheckingSession ||
        state is LoginState.SubmittingCredentials ||
        state is LoginState.LinkingAcademicSystem
    val dismissKeyboardModifier = Modifier.pointerInput(Unit) {
        detectTapGestures {
            focusManager.clearFocus(force = true)
            dismissPlatformKeyboard()
        }
    }
    LaunchedEffect(busy) {
        if (busy) {
            // 登录开始后不再保留输入阶段由键盘/BringIntoView 推动的滚动偏移。
            // iOS 的原生 UITextField 位于 Compose 滚动层中；若加载态仍允许回弹，
            // 上拉时会移动互操作层并短暂露出黑色合成底层，看起来像假键盘。
            focusManager.clearFocus(force = true)
            dismissPlatformKeyboard()
            scroll.scrollTo(0)
        }
    }
    if (windowClass == WindowClass.Expanded) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .platformLoginKeyboardAvoidance(enabled = !busy)
                .then(dismissKeyboardModifier)
                .padding(32.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LoginIntroduction(platform, windowClass, Modifier.weight(2f))
            LoginCard(
                compact = false,
                state = state,
                username = username,
                password = password,
                canRememberCredentials = canRememberCredentials,
                rememberCredentials = rememberCredentials,
                storageReady = storageReady,
                storageMessage = storageMessage,
                automationMessage = automationMessage,
                onUsernameChange = onUsernameChange,
                onPasswordChange = onPasswordChange,
                onRememberCredentialsChange = onRememberCredentialsChange,
                onLogin = onLogin,
                modifier = Modifier
                    .weight(3f)
                    .heightIn(max = 760.dp)
                    .verticalScroll(scroll, enabled = !busy)
                    .desktopTouchScroll(scroll, enabled = !busy),
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .platformLoginKeyboardAvoidance(enabled = !busy)
                .then(dismissKeyboardModifier)
                .verticalScroll(scroll, enabled = !busy)
                .desktopTouchScroll(scroll, enabled = !busy)
                // iOS 宿主已全屏延伸到状态栏后方；这里补回状态栏内边距，
                // 保持与此前“宿主位于状态栏下方”一致的居中布局。Android 维持原状。
                .then(
                    if (platform.family == PlatformFamily.IOS) {
                        Modifier.statusBarsPadding()
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            LoginIntroduction(platform, windowClass, Modifier.fillMaxWidth())
            Spacer(Modifier.height(20.dp))
            LoginCard(
                compact = true,
                state = state,
                username = username,
                password = password,
                canRememberCredentials = canRememberCredentials,
                rememberCredentials = rememberCredentials,
                storageReady = storageReady,
                storageMessage = storageMessage,
                automationMessage = automationMessage,
                onUsernameChange = onUsernameChange,
                onPasswordChange = onPasswordChange,
                onRememberCredentialsChange = onRememberCredentialsChange,
                onLogin = onLogin,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun LoginIntroduction(
    platform: PlatformInfo,
    windowClass: WindowClass,
    modifier: Modifier,
) {
    val compact = windowClass != WindowClass.Expanded
    Column(
        modifier = modifier.padding(
            horizontal = if (compact) 4.dp else 8.dp,
            vertical = if (compact) 8.dp else 16.dp,
        ),
    ) {
        Text(
            "交大自由行",
            fontSize = if (compact) 34.sp else 44.sp,
            lineHeight = if (compact) 40.sp else 52.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "KMP Refreshed",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 自绘的小眼睛图标（不引入 material-icons 依赖）。[visible] 为 true 表示眼睛睁开
 * （密码可见），false 表示带斜线的闭眼（密码隐藏）。
 */
@Composable
private fun PasswordVisibilityIcon(visible: Boolean, tint: Color) {
    Canvas(modifier = Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.08f, cap = StrokeCap.Round)
        // 眼睛外轮廓（杏仁形）
        drawOval(
            color = tint,
            topLeft = Offset(w * 0.08f, h * 0.22f),
            size = Size(w * 0.84f, h * 0.56f),
            style = stroke,
        )
        // 瞳孔
        drawCircle(
            color = tint,
            radius = w * 0.13f,
            center = Offset(w / 2f, h / 2f),
        )
        if (!visible) {
            // 隐藏：画一条左上到右下的斜线
            drawLine(
                color = tint,
                start = Offset(w * 0.16f, h * 0.16f),
                end = Offset(w * 0.84f, h * 0.84f),
                strokeWidth = w * 0.09f,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun LoginCard(
    compact: Boolean,
    state: LoginState,
    username: String,
    password: String,
    canRememberCredentials: Boolean,
    rememberCredentials: Boolean,
    storageReady: Boolean,
    storageMessage: String?,
    automationMessage: String?,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onRememberCredentialsChange: (Boolean) -> Unit,
    onLogin: () -> Unit,
    modifier: Modifier,
) {
    val busy = !storageReady ||
        state is LoginState.CheckingSession ||
        state is LoginState.SubmittingCredentials ||
        state is LoginState.LinkingAcademicSystem
    val canLogin = username.isNotBlank() && password.isNotBlank() && !busy

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(if (compact) 24.dp else 28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(if (compact) 18.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 16.dp),
        ) {
            Text("登录", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "输入学号和密码，验证码将自动识别。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (automationMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ProgressRow(
                        message = automationMessage,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }

            storageMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            PlatformCredentialFields(
                username = username,
                password = password,
                enabled = !busy,
                onUsernameChange = onUsernameChange,
                onPasswordChange = onPasswordChange,
                onPasswordImeAction = {
                    dismissPlatformKeyboard()
                    if (canLogin) onLogin()
                },
                modifier = Modifier.fillMaxWidth(),
            )

            if (canRememberCredentials) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(
                            value = rememberCredentials,
                            enabled = !busy,
                            role = Role.Checkbox,
                            onValueChange = onRememberCredentialsChange,
                        )
                        .semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = rememberCredentials,
                        onCheckedChange = null,
                        enabled = !busy,
                    )
                    Text(
                        "在此设备上安全保存登录信息",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Button(
                onClick = onLogin,
                enabled = canLogin,
                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
            ) {
                Text(if (busy) "正在登录…" else "登录")
            }

            when (state) {
                LoginState.CheckingSession -> Unit
                LoginState.SubmittingCredentials -> Unit
                is LoginState.LinkingAcademicSystem -> ProgressRow("MIS 已通过，正在连接教务系统…")
                is LoginState.Failed -> ErrorMessage(state.reason)
                is LoginState.SignedIn -> Unit
                else -> Unit
            }
        }
    }
}

/** Android 以及原生 macOS helper 不可用时的 Compose 回退。 */
@Composable
internal fun ComposeCredentialFields(
    username: String,
    password: String,
    enabled: Boolean,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasswordImeAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var passwordVisible by remember { mutableStateOf(false) }
    val passwordFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedTextField(
            value = username,
            onValueChange = onUsernameChange,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().credentialFieldKeyboardAvoidance(),
            label = { Text("学号") },
            singleLine = true,
            keyboardOptions = usernameKeyboardOptions().copy(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(
                onNext = { passwordFocusRequester.requestFocus() },
            ),
        )
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().focusRequester(passwordFocusRequester).credentialFieldKeyboardAvoidance(),
            label = { Text("密码") },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = if (showsPasswordVisibilityToggle) {
                {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        PasswordVisibilityIcon(
                            visible = passwordVisible,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                null
            },
            keyboardOptions = passwordKeyboardOptions().copy(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(
                onGo = {
                    focusManager.clearFocus(force = true)
                    onPasswordImeAction()
                },
            ),
        )
    }
}

@Composable
private fun CaptchaBlock(
    challenge: CaptchaChallenge?,
    loading: Boolean,
    answer: String,
    enabled: Boolean,
    onAnswerChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onSubmit: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val bitmap = remember(challenge?.imageBytes) {
        try {
            challenge?.imageBytes?.decodeToImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                // 验证码本体约 130×42；区域常驻占位，加载完成前显示进度而非空白。
                modifier = Modifier
                    .width(150.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    when {
                        loading -> CircularProgressIndicator(
                            modifier = Modifier.width(22.dp).height(22.dp),
                            strokeWidth = 2.dp,
                        )
                        bitmap != null -> Image(
                            bitmap = bitmap,
                            contentDescription = "验证码图片",
                            modifier = Modifier.fillMaxSize().padding(4.dp),
                            // Fit 保留像素比例，避免 FillBounds 拉糊。
                            contentScale = ContentScale.Fit,
                        )
                        else -> Text("点击刷新获取", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            FilledTonalButton(
                onClick = {
                    focusManager.clearFocus(force = true)
                    dismissPlatformKeyboard()
                    onRefresh()
                },
                enabled = enabled,
            ) {
                Text("换一张")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val dark = isSystemInDarkTheme()
            val fieldContainer = if (dark) Color(0xFF23262C) else Color(0xFFF1F3F8)
            val fieldText = if (dark) Color(0xFFF1F1F4) else Color(0xFF1A1B1F)
            val fieldOutline = if (dark) Color(0xFF8E939D) else Color(0xFF7A818C)
            OutlinedTextField(
                value = answer,
                onValueChange = onAnswerChange,
                enabled = enabled,
                modifier = Modifier.weight(1f).height(56.dp),
                placeholder = { Text("验证码答案") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = fieldText,
                    unfocusedTextColor = fieldText,
                    disabledTextColor = fieldText.copy(alpha = 0.55f),
                    focusedContainerColor = fieldContainer,
                    unfocusedContainerColor = fieldContainer,
                    disabledContainerColor = fieldContainer.copy(alpha = 0.55f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = fieldOutline,
                    disabledBorderColor = fieldOutline.copy(alpha = 0.55f),
                    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    cursorColor = MaterialTheme.colorScheme.primary,
                ),
                keyboardOptions = KeyboardOptions(
                    // 旧 CAS 是算式验证码；统一身份认证的验证码含字母。
                    keyboardType = if (challenge?.autoRecognizable == false) KeyboardType.Ascii else KeyboardType.Number,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(
                    onGo = {
                        focusManager.clearFocus(force = true)
                        dismissPlatformKeyboard()
                        if (enabled && answer.isNotBlank()) onSubmit()
                    },
                ),
            )
        }
    }
}

@Composable
private fun ProgressRow(message: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp)
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ErrorMessage(reason: LoginFailure) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            loginFailureMessage(reason),
            modifier = Modifier.padding(14.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun loginFailureMessage(reason: LoginFailure): String = when (reason) {
    LoginFailure.INVALID_CREDENTIALS -> "请完整填写学号和密码。"
    LoginFailure.ACCOUNT_REJECTED -> "学号或密码错误，请检查后重试。"
    LoginFailure.CAPTCHA_REJECTED -> "登录未通过，请手动输入当前验证码。"
    LoginFailure.CAPTCHA_RECOGNITION_FAILED -> "验证码自动识别未通过，请手动输入当前验证码。"
    LoginFailure.SESSION_EXPIRED -> "检测到旧会话，但缺少可恢复资料。请清除会话后重试。"
    LoginFailure.MALFORMED_RESPONSE -> "学校页面结构已变化，暂时无法继续登录。"
    LoginFailure.NETWORK -> "无法连接学校登录服务，请检查网络后重试。"
    LoginFailure.ACADEMIC_LINK_FAILED -> "MIS 已登录，但教务系统连接失败。"
}
