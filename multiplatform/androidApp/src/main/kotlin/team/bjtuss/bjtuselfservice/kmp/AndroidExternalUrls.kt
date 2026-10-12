package team.bjtuss.bjtuselfservice.kmp

import android.app.Activity
import android.content.Intent
import android.net.Uri

/**
 * 用系统浏览器打开 HTTPS 链接（GitHub、校园网、物理在线、校历公众号文章）。
 * 校历公众号文章不交给微信：第三方唤起的 mp.weixin.qq.com 会被判成 invalid source。
 * 完美校园不经过这里：从 App 唤起微信打开加密 URL Link 会被「一人一链」拦截，
 * 校园卡弹窗改为打开完美校园 App、二维码和复制链接。
 */
internal fun openHttpsUrl(activity: Activity, url: String) {
    if (!url.startsWith("https://")) return
    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}
