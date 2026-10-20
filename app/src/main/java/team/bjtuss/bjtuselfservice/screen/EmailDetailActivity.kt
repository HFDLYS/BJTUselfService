package team.bjtuss.bjtuselfservice.screen

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import team.bjtuss.bjtuselfservice.repository.EmailDetail
import team.bjtuss.bjtuselfservice.repository.EmailRepository
import team.bjtuss.bjtuselfservice.ui.theme.AppTheme
import team.bjtuss.bjtuselfservice.utils.HtmlEmailRenderer
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
            AppTheme(currentTheme = Theme.System, dynamicColor = false) {
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

    LaunchedEffect(mid, retryKey) {
        state = DetailState.Loading
        state = runCatching { EmailRepository.loadMessage(mid) }
            .fold(
                onSuccess = { DetailState.Success(it) },
                onFailure = { DetailState.Error(it.message ?: "邮件读取失败") },
            )
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
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                    ) {
                        Text(
                            text = detail.subject.ifBlank { fallbackSubject.ifBlank { "(无主题)" } },
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        )
                        Spacer(Modifier.padding(top = 12.dp))
                        Text(
                            text = detail.from.ifBlank { fallbackFrom },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            Text(
                                text = "发送时间：" + detail.date,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = detail.to,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                        if (detail.attachments.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "附件",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                detail.attachments.forEach { name ->
                                    AssistChip(onClick = {}, label = { Text(name) })
                                }
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            }
                        }
                        if (detail.html != null) {
                            AndroidView(
                                modifier = Modifier.fillMaxWidth(),
                                factory = { ctx ->
                                    TextView(ctx).apply {
                                        setTextIsSelectable(true)
                                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                                        setPadding(0, 0, 0, 0)
                                        movementMethod = LinkMovementMethod.getInstance()
                                    }
                                },
                                update = { textView ->
                                    textView.text = HtmlEmailRenderer.renderForDisplay(detail.html)
                                },
                            )
                        } else {
                            SelectionContainer {
                                Text(
                                    text = detail.body.ifBlank { "(无正文)" },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
