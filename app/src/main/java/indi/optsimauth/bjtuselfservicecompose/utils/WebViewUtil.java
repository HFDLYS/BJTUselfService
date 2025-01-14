package indi.optsimauth.bjtuselfservicecompose.utils;

import android.webkit.CookieManager;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

import java.util.List;

//public class WebViewUtil {
//
//    /**
//     * 将从OkHttpClient获取的Cookies同步到WebView中。
//     *
//     * @param webViewUrl   要加载的WebView中的URL。
//     * @param okHttpClient 使用的OkHttpClient实例。
//     */
//    public static void syncCookiesFromOkHttpToWebView(String webViewUrl, OkHttpClient okHttpClient) {
//        HttpUrl url = HttpUrl.parse(webViewUrl);
//        if (url == null) {
//            throw new IllegalArgumentException("Invalid URL.");
//        }
//
//        List<Cookie> cookies = getCookiesForUrl(okHttpClient, url);
//        syncCookiesToWebView(webViewUrl, cookies);
//    }
//
//    /**
//     * 从OkHttpClient的CookieJar获取指定URL的Cookies。
//     *
//     * @param client OkHttpClient实例。
//     * @param url    指定的URL。
//     * @return 指定URL的Cookies列表。
//     */
//    private static List<Cookie> getCookiesForUrl(OkHttpClient client, HttpUrl url) {
//        CookieJar cookieJar = client.cookieJar();
//        return cookieJar.loadForRequest(url);
//    }
//
//    /**
//     * 将指定的Cookies设置到WebView的CookieManager中。
//     *
//     * @param url     要设置Cookies的URL。
//     * @param cookies 要设置的Cookies列表。
//     */
//    private static void syncCookiesToWebView(String url, List<Cookie> cookies) {
//        CookieManager cookieManager = CookieManager.getInstance();
//        for (Cookie cookie : cookies) {
//            String cookieString = cookie.name() + "=" + cookie.value() + "; domain=" + cookie.domain();
//            cookieManager.setCookie(url, cookieString);
//        }
//        // 确保Cookies立即生效
//        CookieManager.getInstance().flush();
//    }
//
//    public static List<Cookie> getCookies(String webViewUrl, OkHttpClient okHttpClient) {
//        HttpUrl url = HttpUrl.parse(webViewUrl);
//        if (url == null) {
//            throw new IllegalArgumentException("Invalid URL.");
//        }
//
//        return getCookiesForUrl(okHttpClient, url);
//    }
//}

