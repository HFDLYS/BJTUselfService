package team.bjtuss.bjtuselfservice.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import team.bjtuss.bjtuselfservice.StudentAccountManager
import team.bjtuss.bjtuselfservice.repository.SmartCurriculumPlatformRepository
import team.bjtuss.bjtuselfservice.repository.SmartCurriculumPlatformRepository.client

interface RequestApi {
    suspend fun get(url: String): Response
    suspend fun get(url: String, headers: Map<String, String>): Response
    suspend fun get(url: String, headers: Headers): Response


    // POST with form data Map
    suspend fun post(url: String, headers: Map<String, String>, data: Map<String, String>): Response
    suspend fun post(url: String, headers: Headers, data: Map<String, String>): Response
}

object RequestKotlin : RequestApi {
    val client: OkHttpClient = SmartCurriculumPlatformRepository.client

    private suspend fun executeGetRequest(url: String, headers: Headers): Response = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .headers(headers)
            .get()
            .build()

        client.newCall(request).execute()
    }

    private suspend fun executePostRequest(url: String, headers: Headers, postBody: RequestBody): Response = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .headers(headers)
            .post(postBody)
            .build()

        client.newCall(request).execute()
    }

    override suspend fun get(url: String): Response {
        return executeGetRequest(url, Headers.Builder().build())
    }

    override suspend fun get(url: String, headers: Map<String, String>): Response {
        val okHttpHeaders = mapToOkHttpHeaders(headers)
        return executeGetRequest(url, okHttpHeaders)
    }

    override suspend fun get(url: String, headers: Headers): Response {
        return executeGetRequest(url, headers)
    }






    override suspend fun post(url: String, headers: Map<String, String>, data: Map<String, String>): Response {
        val okHttpHeaders = mapToOkHttpHeaders(headers)
        val requestBody = mapToFormBody(data)
        return executePostRequest(url, okHttpHeaders, requestBody)
    }

    override suspend fun post(url: String, headers: Headers, data: Map<String, String>): Response {
        val requestBody = mapToFormBody(data)
        return executePostRequest(url, headers, requestBody)
    }

    private fun mapToOkHttpHeaders(headers: Map<String, String>): Headers {
        val headersBuilder = Headers.Builder()
        headers.forEach { (name, value) ->
            headersBuilder.add(name, value)
        }
        return headersBuilder.build()
    }

    private fun mapToFormBody(data: Map<String, String>): RequestBody {
        val formBuilder = FormBody.Builder()
        data.forEach { (key, value) ->
            formBuilder.add(key, value)
        }
        return formBuilder.build()
    }
}