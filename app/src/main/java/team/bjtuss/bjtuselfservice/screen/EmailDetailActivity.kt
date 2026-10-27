package team.bjtuss.bjtuselfservice.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import team.bjtuss.bjtuselfservice.repository.DataStoreRepository
import team.bjtuss.bjtuselfservice.repository.EmailDetail
import team.bjtuss.bjtuselfservice.repository.EmailAttachment
import team.bjtuss.bjtuselfservice.repository.EmailRepository
import team.bjtuss.bjtuselfservice.ui.theme.AppTheme
import team.bjtuss.bjtuselfservice.utils.HtmlEmailRenderer
import team.bjtuss.bjtuselfservice.utils.SmartLinkAction
import team.bjtuss.bjtuselfservice.utils.SmartLinkifier
import team.bjtuss.bjtuselfservice.utils.SmartLinkType
import team.bjtuss.bjtuselfservice.ui.theme.Theme

class EmailDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mid = intent.getStringExtra(EXTRA_MID)
        if (mid.isNullOrBlank()) {
            finish()
            return
        }
        val fallbackSubject = intent.getStringExtra(EXTRA_SUBJECT).orEmpty()
        val fallbackFrom = intent.getStringExtra(EXTRA_FROM).orEmpty()

        setContent {
            val savedTheme by DataStoreRepository.getTheme()
                .collectAsState(initial = Theme.System.themeString)
            val dynamicColor by DataStoreRepository.getDynamicColorOption()
                .collectAsState(initial = false)
            val theme = when (savedTheme) {
                Theme.Light.themeString -> Theme.Light
                Theme.Dark.themeString -> Theme.Dark
                else -> Theme.System
            }
            AppTheme(currentTheme = theme, dynamicColor = dynamicColor) {
                EmailDetailScreen(
                    mid = mid,
                    fallbackSubject = fallbackSubject,
                    fallbackFrom = fallbackFrom,
                    onBack = { finish() },
                )
            }
        }
    }

    companion object {
        const val EXTRA_MID = "email_mid"
        const val EXTRA_SUBJECT = "email_subject"
        const val EXTRA_FROM = "email_from"
    }
}

private fun linkTitle(type: SmartLinkType): String = when (type) {
    SmartLinkType.EMAIL -> "邮箱"
    SmartLinkType.PHONE -> "电话号码"
    SmartLinkType.URL -> "链接"
    SmartLinkType.CODE -> "编号"
}

private fun linkPrimaryLabel(type: SmartLinkType): String = when (type) {
    SmartLinkType.EMAIL -> "发邮件"
    SmartLinkType.PHONE -> "拨号"
    SmartLinkType.URL -> "打开"
    SmartLinkType.CODE -> "复制"
}

private fun performLinkAction(context: Context, action: SmartLinkAction) {
    val intent = when (action.type) {
        SmartLinkType.EMAIL -> Intent(
            Intent.ACTION_SENDTO,
            Uri.parse("mailto:" + Uri.encode(action.value)),
        )
        SmartLinkType.PHONE -> Intent(
            Intent.ACTION_DIAL,
            Uri.parse("tel:" + Uri.encode(action.value.filter { it.isDigit() || it == '+' })),
        )
        SmartLinkType.URL -> Intent(Intent.ACTION_VIEW, Uri.parse(action.value))
        SmartLinkType.CODE -> null
    }
    if (intent == null) {
        copyLinkValue(context, action.value)
        return
    }
    runCatching { context.startActivity(intent) }
        .onFailure {
            copyLinkValue(context, action.value)
            Toast.makeText(context, "没有可用应用，内容已复制", Toast.LENGTH_SHORT).show()
        }
}

private fun copyLinkValue(context: Context, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("邮件内容", value))
    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
}

private sealed interface DetailState {
    data object Loading : DetailState
    data class Success(val detail: EmailDetail) : DetailState
    data class Error(val message: String) : DetailState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmailDetailScreen(
    mid: String,
    fallbackSubject: String,
    fallbackFrom: String,
    onBack: () -> Unit,
) {
    var state by remember { mutableStateOf<DetailState>(DetailState.Loading) }
    var retryKey by remember { mutableStateOf(0) }
    var linkAction by remember { mutableStateOf<SmartLinkAction?>(null) }
    var readRetry by remember { mutableIntStateOf(0) }
    var readSyncFailed by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(mid, retryKey) {
        state = DetailState.Loading
        state = runCatching { EmailRepository.loadMessage(mid) }
            .fold(
                onSuccess = { DetailState.Success(it) },
                onFailure = { DetailState.Error(it.message ?: "邮件读取失败") },
            )
    }

    val detailLoaded = state is DetailState.Success
    LaunchedEffect(mid, detailLoaded, readRetry) {
        readSyncFailed = false
        if (detailLoaded) {
            try {
                EmailRepository.markRead(mid)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                readSyncFailed = true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "邮件详情",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            color = MaterialTheme.colorScheme.background,
        ) {
            when (val current = state) {
                is DetailState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is DetailState.Error -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "邮件读取失败",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = current.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Button(
                            onClick = { retryKey++ },
                            modifier = Modifier.padding(top = 16.dp),
                        ) {
                            Text("重试")
                        }
                    }
                }

                is DetailState.Success -> {
                    val detail = current.detail
                    val bodyColor = MaterialTheme.colorScheme.onSurface
                    val linkColor = MaterialTheme.colorScheme.primary
                    val bodyArgb = bodyColor.toArgb()
                    val linkArgb = linkColor.toArgb()
                    val highlightArgb = MaterialTheme.colorScheme.primaryContainer
                        .copy(alpha = 0.35f)
                        .toArgb()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                    ) {
                        if (readSyncFailed) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("已读状态同步失败", color = MaterialTheme.colorScheme.error)
                                TextButton(onClick = { readRetry++ }) { Text("重试") }
                            }
                        }
                        Text(
                            text = detail.subject.ifBlank { fallbackSubject.ifBlank { "无主题" } },
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        )
                        Spacer(Modifier.padding(top = 12.dp))
                        Text(
                            text = detail.from.ifBlank { fallbackFrom },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 6.dp),
                        ) {
                            Text(
                                text = "收件人：${detail.to}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = detail.date,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                        if (detail.attachments.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "附件",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        text = "${detail.attachments.size} 个文件",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                detail.attachments.forEach { attachment ->
                                    EmailAttachmentItem(
                                        attachment = attachment,
                                        onDownload = {
                                            scope.launch {
                                                runCatching {
                                                    EmailRepository.downloadAttachment(context, mid, attachment)
                                                }.onSuccess {
                                                    Toast.makeText(context, "已添加下载任务", Toast.LENGTH_SHORT).show()
                                                }.onFailure {
                                                    if (it is CancellationException) throw it
                                                    Toast.makeText(context, "附件下载失败：${it.message}", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        },
                                    )
                                }
                                Text(
                                    text = "保存至「下载」文件夹",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            }
                        }
                        val bodyContent: Spanned = if (detail.html != null) {
                            HtmlEmailRenderer.renderForDisplay(detail.html)
                        } else {
                            SpannableString(detail.body.ifBlank { "暂无正文" })
                        }
                        AndroidView(
                            modifier = Modifier.fillMaxWidth(),
                            factory = { ctx ->
                                TextView(ctx).apply {
                                    setTextIsSelectable(true)
                                    linksClickable = true
                                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                                    setTextColor(bodyArgb)
                                    setLinkTextColor(linkArgb)
                                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                    setPadding(0, 0, 0, 0)
                                    setLineSpacing(0f, 1.25f)
                                    movementMethod = LinkMovementMethod.getInstance()
                                }
                            },
                            update = { textView ->
                                textView.setTextColor(bodyArgb)
                                textView.setLinkTextColor(linkArgb)
                                textView.text = SmartLinkifier.linkify(
                                    bodyContent,
                                    linkArgb,
                                    highlightArgb,
                                ) { action -> linkAction = action }
                            },
                        )
                    }
                }
            }
        }
    }

    linkAction?.let { action ->
        AlertDialog(
            onDismissRequest = { linkAction = null },
            title = { Text(linkTitle(action.type)) },
            text = { Text(action.value) },
            confirmButton = {
                TextButton(onClick = {
                    performLinkAction(context, action)
                    linkAction = null
                }) {
                    Text(linkPrimaryLabel(action.type))
                }
            },
            dismissButton = {
                if (action.type == SmartLinkType.CODE) {
                    TextButton(onClick = { linkAction = null }) { Text("关闭") }
                } else {
                    TextButton(onClick = {
                        copyLinkValue(context, action.value)
                        linkAction = null
                    }) { Text("复制") }
                }
            },
        )
    }
}

@Composable
private fun EmailAttachmentItem(attachment: EmailAttachment, onDownload: () -> Unit) {
    Card(
        onClick = onDownload,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.InsertDriveFile,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = attachment.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Outlined.Download,
                contentDescription = "下载附件",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
