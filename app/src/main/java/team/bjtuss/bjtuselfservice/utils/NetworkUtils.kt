package team.bjtuss.bjtuselfservice.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

object NetworkUtils {

    private const val userAgent =
        "User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0',";

    suspend fun get(client: OkHttpClient, url: String): String {
        var request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .build()

        return withContext(Dispatchers.IO) {
            val response = client.newCall(request).execute()

            response.use {
                if (!response.isSuccessful) {
                    throw IOException("Unexpected code $response")
                }
                response.body?.string() ?: throw IOException("Empty response body")
            }


        }
    }

}