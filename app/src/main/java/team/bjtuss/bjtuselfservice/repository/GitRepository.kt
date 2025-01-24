package team.bjtuss.bjtuselfservice.repository

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import team.bjtuss.bjtuselfservice.constant.ApiConstant
import java.io.IOException


data class GitHubRelease(
    val id: Long,
    @Json(name = "tag_name") val tagName: String,
    val name: String?,
    val body: String?,
    @Json(name = "html_url") val htmlUrl: String,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "published_at") val publishedAt: String,
    val assets: List<Asset>
)

data class Asset(
    val name: String,
    @Json(name = "browser_download_url") val downloadUrl: String,
    val size: Long
)

suspend fun fetchLatestRelease(): GitHubRelease? = withContext(Dispatchers.IO) {
    val request = Request.Builder()
        .url(ApiConstant.GITHUB_LATEST_URL)
        .build()
    val client = OkHttpClient()
    try {
        val response: Response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            println("Request failed: ${response.code}")
            return@withContext null
        }

        val responseBody = response.body?.string() ?: return@withContext null

        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        val jsonAdapter = moshi.adapter(GitHubRelease::class.java)
        return@withContext jsonAdapter.fromJson(responseBody)
    } catch (e: IOException) {
        println("Network error: ${e.message}")
        null
    }
}