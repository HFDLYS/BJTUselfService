package team.bjtuss.bjtuselfservice.repository

import com.squareup.moshi.Moshi
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** Uses the same updateMessageInfos protocol as the school's Coremail web client. */
internal class CoremailReadStatus(
    private val client: OkHttpClient,
    private val endpoint: HttpUrl = "https://mail.bjtu.edu.cn/coremail/s/json".toHttpUrl(),
) {
    fun unreadCount(sid: String): Int {
        val url = endpoint.resolve("/coremail/XT/jsp/mail.jsp")!!.newBuilder()
            .addQueryParameter("sid", sid)
            .addQueryParameter("func", "getAllFolders")
            .addQueryParameter("stats", "true")
            .addQueryParameter("threads", "false")
            .build()
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("无法查询未读邮件数量")
            val result = Moshi.Builder().build().adapter(Map::class.java)
                .fromJson(response.body?.string() ?: throw IOException("邮箱统计响应为空"))
            if (result?.get("code") != "S_OK") throw IOException("邮箱统计查询失败")
            val inbox = (result["var"] as? List<*>)?.filterIsInstance<Map<*, *>>()
                ?.firstOrNull { (it["id"] as? Number)?.toInt() == 1 }
            val count = ((inbox?.get("stats") as? Map<*, *>)?.get("unreadMessageCount") as? Number)?.toInt()
            return count?.takeIf { it >= 0 } ?: throw IOException("缺少收件箱未读统计")
        }
    }

    fun markRead(sid: String, mid: String) {
        require(sid.isNotBlank() && mid.isNotBlank())
        val moshi = Moshi.Builder().build()
        val id = moshi.adapter(String::class.java).toJson(mid)
        val body = """{"ids":[$id],"attrs":{"flags":{"read":true}}}"""
            .toRequestBody("text/x-json; charset=utf-8".toMediaType())
        val url = endpoint.newBuilder()
            .addQueryParameter("sid", sid)
            .addQueryParameter("func", "mbox:updateMessageInfos")
            .build()
        client.newCall(Request.Builder().url(url).header("Accept", "text/x-json").post(body).build())
            .execute().use { response ->
                if (!response.isSuccessful) throw IOException("邮件已读状态同步失败：HTTP ${response.code}")
                val result = moshi.adapter(Map::class.java)
                    .fromJson(response.body?.string() ?: throw IOException("邮件已读状态响应为空"))
                if (result?.get("code") != "S_OK") throw IOException("邮件已读状态同步失败，请重试")
            }
    }
}
