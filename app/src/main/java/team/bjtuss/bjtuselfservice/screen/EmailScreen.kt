package team.bjtuss.bjtuselfservice.screen

import android.content.Intent
import android.net.Uri
import android.net.http.SslError
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import team.bjtuss.bjtuselfservice.StudentAccountManager

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import team.bjtuss.bjtuselfservice.RouteManager

@Composable
fun EmailScreen() {
    val context = LocalContext.current
    val studentAccountManager = StudentAccountManager.getInstance()


    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                // WebView Settings
                settings.apply {
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    allowUniversalAccessFromFileURLs = true
                    allowFileAccess = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                // WebView Client
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest
                    ): Boolean {
                        val url = request.url.toString()
                        view.loadUrl(url)
                        return true
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                    }

                    override fun onReceivedSslError(
                        view: WebView?,
                        handler: SslErrorHandler?,
                        error: SslError?
                    ) {
                        super.onReceivedSslError(view, handler, error)
                    }
                }

                WebViewUtil.syncCookiesFromOkHttpToWebView(
                    "https://mis.bjtu.edu.cn/",
                    studentAccountManager.client
                )

                loadUrl("https://mis.bjtu.edu.cn/module/module/26/")
            }
        },
    )
}



object WebViewUtil {
    fun syncCookiesFromOkHttpToWebView(url: String, client: OkHttpClient) {
        val cookieHandler = client.cookieJar
        val cookies = cookieHandler.loadForRequest(url.toHttpUrl())

        cookies.forEach { cookie ->
            CookieManager.getInstance().setCookie(url, cookie.toString())
        }
        CookieManager.getInstance().flush()
    }
}

@Composable
fun WebViewScreen() {
    val context = LocalContext.current
    val studentAccountManager = StudentAccountManager.getInstance()


    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                // WebView Settings
                settings.apply {
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    allowUniversalAccessFromFileURLs = true
                    allowFileAccess = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }

                // WebView Client
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest
                    ): Boolean {
                        val url = request.url.toString()
                        return when {
                            // 如果是mis.bjtu.edu.cn域名，保持在WebView内打开
                            url.contains("mis.bjtu.edu.cn") -> {
                                view.loadUrl(url)
                                true
                            }
                            // 其他链接在外部浏览器打开
                            else -> {
                                try {
                                    val intent =
                                        Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                            // 添加浏览器启动标志
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    context.startActivity(intent)
                                    true
                                } catch (e: Exception) {
                                    // 处理无法打开浏览器的情况
                                    android.util.Log.e(
                                        "WebView",
                                        "Cannot open browser: ${e.message}"
                                    )
                                    false
                                }
                            }
                        }
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        WebViewUtil.syncCookiesFromOkHttpToWebView(
                            url ?: "https://mis.bjtu.edu.cn/",
                            studentAccountManager.client
                        )
                    }
                }

                // WebView Chrome Client for error handling
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(message: android.webkit.ConsoleMessage?): Boolean {
                        message?.apply {
                            android.util.Log.e(
                                "WebView",
                                "Console error: ${message()} (${sourceId()}:${lineNumber()})"
                            )
                        }
                        return true
                    }
                }

                // Sync cookies
                WebViewUtil.syncCookiesFromOkHttpToWebView(
                    "https://mis.bjtu.edu.cn/",
                    studentAccountManager.client
                )

                // Load URL
                loadUrl("https://mis.bjtu.edu.cn/module/module/26/")
            }
        }
    )
}
