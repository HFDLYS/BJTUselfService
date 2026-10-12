package team.bjtuss.bjtuselfservice.shared.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import team.bjtuss.bjtuselfservice.shared.credentialFieldKeyboardAvoidance
import team.bjtuss.bjtuselfservice.shared.platformLoginKeyboardAvoidance
import team.bjtuss.bjtuselfservice.shared.feature.scroll.desktopTouchScroll
import team.bjtuss.bjtuselfservice.shared.feature.shell.*

@Composable
internal fun IndependentAccountSettings(
    username: String, password: String, hasSavedPassword: Boolean, ready: Boolean, saving: Boolean,
    onUsername: (String) -> Unit, onPassword: (String) -> Unit, onSave: () -> Unit,
    message: String?, failed: Boolean = false, modifier: Modifier = Modifier,
    websiteUrl: String? = null,
    websiteRedirectUrl: String? = null,
    onClear: (() -> Unit)? = null,
    canClear: Boolean = hasSavedPassword,
) {
    val scroll = rememberScrollState()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val uriHandler = LocalUriHandler.current
    var confirmClear by remember { mutableStateOf(false) }
    TopScrollColumn(state = scroll, modifier = modifier.fillMaxSize().platformLoginKeyboardAvoidance(true).desktopTouchScroll(scroll),
        contentModifier = Modifier.padding(horizontal = 20.dp)
            .padding(top = 16.dp + LocalTopBarClearance.current, bottom = 24.dp + LocalBottomBarClearance.current),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        websiteUrl?.let { url ->
            Column {
                if (websiteRedirectUrl == null) {
                    Text("请填写此网站的账号和密码", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { uriHandler.openUri(url) }, contentPadding = PaddingValues(0.dp)) {
                        Text(url)
                    }
                } else {
                    Text("请填写此网站的账号和密码$url（重定向后为：", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { uriHandler.openUri(websiteRedirectUrl) }, contentPadding = PaddingValues(0.dp)) {
                        Text(websiteRedirectUrl)
                    }
                    Text("）", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        OutlinedTextField(username, onUsername, label = { Text("账号") }, singleLine = true, enabled = ready && !saving,
            modifier = Modifier.fillMaxWidth().credentialFieldKeyboardAvoidance())
        OutlinedTextField(password, onPassword, label = { Text(if (hasSavedPassword && password.isEmpty()) "已填写过密码" else "密码") }, singleLine = true, enabled = ready && !saving,
            placeholder = if (hasSavedPassword) ({ Text("已填写过密码") }) else null,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().credentialFieldKeyboardAvoidance())
        FilledTonalButton(onClick = { focus.clearFocus(force = true); keyboard?.hide(); onSave() },
            enabled = ready && !saving && username.isNotBlank() && (hasSavedPassword || password.isNotEmpty()),
            modifier = Modifier.fillMaxWidth()) { Text("保存账号") }
        if (onClear != null) {
            Button(
                onClick = { focus.clearFocus(force = true); keyboard?.hide(); confirmClear = true },
                enabled = ready && !saving && canClear,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("清除配置信息") }
        }
        if (failed) AppErrorBanner(message ?: "请检查账号密码和网络后重试。", title = "同步失败")
        else message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    }
    if (confirmClear && onClear != null) {
        ClearIndependentAccountDialog(
            onDismiss = { confirmClear = false },
            onClear = {
                confirmClear = false
                onClear()
            },
        )
    }
    if (saving) Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            AccountSyncDialogContent()
        }
    }
}

@Composable
internal fun ClearIndependentAccountDialog(onDismiss: () -> Unit, onClear: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("清除配置信息？") },
        text = { Text("将清除已保存的密码并关闭此功能，账号将保留。重新配置账号和密码后才能开启。") },
        confirmButton = {
            TextButton(onClick = onClear) { Text("清除", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
internal fun AccountSyncDialogContent() {
    Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        CircularProgressIndicator(Modifier.size(28.dp))
        Text("正在同步")
    }
}
