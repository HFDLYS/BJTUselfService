package team.bjtuss.bjtuselfservice.shared.feature.home

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

private const val CAMPUS_APP_PACKAGE = "com.newcapec.mobile.ncp"

/** Android 11+ 需要宿主 Manifest 在 `<queries>` 里声明 [CAMPUS_APP_PACKAGE] 才能取到启动 Intent。 */
@Composable
internal actual fun rememberCampusAppLauncher(): (() -> Boolean)? {
    val context = LocalContext.current
    return remember(context) {
        {
            val intent = context.packageManager.getLaunchIntentForPackage(CAMPUS_APP_PACKAGE)
            intent != null && runCatching {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
        }
    }
}
