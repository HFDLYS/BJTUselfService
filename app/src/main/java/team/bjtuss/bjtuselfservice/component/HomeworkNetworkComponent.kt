package team.bjtuss.bjtuselfservice.component

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import team.bjtuss.bjtuselfservice.MainApplication
import team.bjtuss.bjtuselfservice.api.RequestApi
import team.bjtuss.bjtuselfservice.api.RequestKotlin
import team.bjtuss.bjtuselfservice.entity.HomeworkEntity
import team.bjtuss.bjtuselfservice.repository.SmartCurriculumPlatformRepository
import team.bjtuss.bjtuselfservice.utils.DownloadUtil
import team.bjtuss.bjtuselfservice.utils.KotlinUtils
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit


class HomeworkUploader(val homeworkEntity: HomeworkEntity) {
    private val client = SmartCurriculumPlatformRepository.client
    val context = MainApplication.appContext

    // Convert content Uri to temporary file
    private suspend fun uriToTempFile(uri: Uri, fileName: String): File =
        withContext(Dispatchers.IO) {
            val tempFile = File(context.cacheDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(tempFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            return@withContext tempFile
        }

    // Upload a single file and return the response as JSON
    private suspend fun uploadFile(file: File): JSONObject = withContext(Dispatchers.IO) {

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                file.name,
                file.asRequestBody("application/octet-stream".toMediaType())
            )
            .build()

        val request = Request.Builder()
            .url("http://123.121.147.7:88/ve/back/rp/common/rpUpload.shtml")

            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("上传失败，状态码：${response.code}")
            }
            val responseBody = response.body?.string() ?: ""
            return@withContext JSONObject(responseBody)
        }
    }

    // Main function to upload homework files
    suspend fun uploadHomework(uris: List<Uri>, content: String = "Android上传"): String =
        withContext(Dispatchers.IO) {
            // Step 1: Upload files
            val fileInfoList = mutableListOf<JSONObject>()

            for (uri in uris) {
                val fileName = getFileName(context, uri) ?: "file_${System.currentTimeMillis()}"
                val tempFile = uriToTempFile(uri, fileName)

                try {
                    val uploadResponse = uploadFile(tempFile)
//                    Log.d("HomeworkUploader", "文件上传成功: ${uploadResponse.toString()}")

                    val fileInfo = JSONObject().apply {
                        put("fileNameNoExt", uploadResponse.getString("fileNameNoExt"))
                        put("fileExtName", uploadResponse.getString("fileExtName"))
                        put("fileSize", uploadResponse.getString("fileSize"))
                        put("visitName", uploadResponse.getString("visitName"))
                        put("pid", "")
                        put("ftype", "insert")
                    }

                    fileInfoList.add(fileInfo)
                } catch (e: Exception) {
                    Log.e("HomeworkUploader", "文件上传失败", e)
                    throw e
                } finally {
                    // Clean up temp file
                    tempFile.delete()
                }
            }

            // Step 2: Submit homework with file list
            val fileListJson = JSONArray(fileInfoList).toString()

            val formBodyBuilder = FormBody.Builder()
                .add("content", URLEncoder.encode(content, "UTF-8"))
                .add("groupName", "")
                .add("groupId", "")
                .add("courseId", homeworkEntity.courseId.toString())
                .add("contentType", homeworkEntity.homeworkType.toString())
                .add("fz", "0")
                .add("jxrl_id", "")
                .add("fileList", fileListJson)
                .add("upId", homeworkEntity.upId.toString())
                .add("return_num", "")
                .add("isTeacher", "0")

            val submitRequest = Request.Builder()
                .url("http://123.121.147.7:88/ve/back/course/courseWorkInfo.shtml?method=sendStuHomeWorks")
//                .header("Cookie", "JSESSIONID=$jsessionId")
                .post(formBodyBuilder.build())
                .build()

            client.newCall(submitRequest).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
//                Log.d("HomeworkUploader", "提交响应状态码: ${response.code}")
//                Log.d("HomeworkUploader", "提交响应内容: $responseBody")
                return@withContext responseBody
            }
        }

    // Utility function to get filename from URI
    private fun getFileName(context: Context, uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val displayNameIndex = it.getColumnIndex("_display_name")
                if (displayNameIndex != -1) {
                    return it.getString(displayNameIndex)
                }
            }
        }
        return uri.lastPathSegment
    }
}




suspend fun downloadHomeworkFile(
    homework: HomeworkEntity,
    onProgress: (Float) -> Unit = {},
    onSuccess: (String) -> Unit = {},
    onError: (Exception) -> Unit = {}
) = withContext(Dispatchers.IO) {
    try {
        // Show initial progress
        withContext(Dispatchers.Main) {
            onProgress(0.1f)
        }

        // Create OkHttp client with timeout settings
        val client = SmartCurriculumPlatformRepository.client.newBuilder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        // Build URL for getting homework content
        val courseWorkUrl = "http://123.121.147.7:88/ve/back/course/courseWorkInfo.shtml"
            .toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("method", "piGaiDiv")
            ?.addQueryParameter("upId", homework.upId.toString())
            ?.addQueryParameter("id", homework.idSnId.toString())
            ?.addQueryParameter("score", homework.score)
            ?.addQueryParameter("uLevel", "1")
            ?.addQueryParameter("type", "1")
            ?.addQueryParameter("username", "null")
            ?.addQueryParameter("userId", homework.userId.toString())
            ?.build() ?: throw IllegalStateException("Failed to build URL")

        // Update progress
        withContext(Dispatchers.Main) {
            onProgress(0.2f)
        }

        // Create request for homework page with retry mechanism
        val courseWorkRequest = Request.Builder()
            .url(courseWorkUrl)
            .header("User-Agent", "Mozilla/5.0")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6")
            .build()

        // Execute the request with retry
        var retries = 0
        var response: Response? = null
        var exception: Exception? = null

        while (retries < 3 && response == null) {
            try {
                response = client.newCall(courseWorkRequest).execute()
                if (!response.isSuccessful) {
                    throw IOException("Failed to fetch homework info: ${response.code}")
                }
            } catch (e: Exception) {
                exception = e
                retries++
                delay(1000L * retries) // Exponential backoff
            }
        }

        if (response == null) {
            throw exception ?: IOException("Failed to fetch homework after retries")
        }

        // Update progress
        withContext(Dispatchers.Main) {
            onProgress(0.4f)
        }

        val responseBody = response.body?.string() ?: ""

        // Check for server error
        if (responseBody.contains("系统发生了未处理的异常")) {
            throw Exception("服务器返回错误，请检查参数和登录状态")
        }

        // Parse HTML
        val document = Jsoup.parse(responseBody)
        val homeworkContents = document.select("div.homeworkContent")

        if (homeworkContents.isEmpty()) {
            throw Exception("未找到作业内容")
        }

        // Update progress
        withContext(Dispatchers.Main) {
            onProgress(0.6f)
        }

        // Find file download info
        var fileDownloaded = false

        for (item in homeworkContents) {
            val onClickAttribute = item.attr("onclick")
            if (onClickAttribute.isNotEmpty()) {
                // Parse the onclick attribute with improved regex
                val regex = """\('([^']*)',\s*'([^']*)',\s*'([^']*)'\)""".toRegex()
                val matchResult = regex.find(onClickAttribute)

                if (matchResult != null) {
                    val (path, filename, id) = matchResult.destructured

                    // Build URL for file download
                    val downloadUrl = "http://123.121.147.7:88/ve//downloadZyFj.shtml"
                        .toHttpUrlOrNull()
                        ?.newBuilder()
                        ?.addQueryParameter("path", path)
                        ?.addQueryParameter("filename", filename)
                        ?.addQueryParameter("id", id)
                        ?.build()
                        ?.toString() ?: throw IllegalStateException("Failed to build download URL")

                    // Get file extension
                    val fileExtension = filename.substringAfterLast('.', "pdf")

                    // Update progress
                    withContext(Dispatchers.Main) {
                        onProgress(0.8f)
                    }

                    // Download the file
                    DownloadUtil.downloadFile(
                        downloadUrl,
                        filename,
                        KotlinUtils.getCookieByUrl(downloadUrl),
                        fileExtension
                    )

                    fileDownloaded = true

                    // Notify success
                    withContext(Dispatchers.Main) {
                        onProgress(1.0f)
                        onSuccess("作业 '$filename' 下载成功")
                    }
                }
            }
        }

        if (!fileDownloaded) {
            throw Exception("未找到可下载的作业文件")
        }

    } catch (e: Exception) {
        Log.e("HomeworkDownloader", "Download failed", e)
        withContext(Dispatchers.Main) {
            onError(e)
        }
        throw e
    }
}


suspend fun getHomeworkGrade(
    homework: HomeworkEntity
): String = withContext(Dispatchers.IO) {
    val BASE_URL = "http://123.121.147.7:88/ve/back/course/courseWorkInfo.shtml"
    val METHOD = "piGaiDiv"



    val url = "$BASE_URL?method=$METHOD&upId=${homework.upId}&id=${homework.idSnId}&uLevel=1"


    try {
        // 1. 网络请求 (对应 Python 的 self.session.get(url, headers=self.headers))
        val responseText = RequestKotlin.get(url, SmartCurriculumPlatformRepository.headersBuilder.build()).use {
            if (!it.isSuccessful) {
                throw IOException("网络请求失败，状态码: ${it.code}")
            }
            it.body?.string() ?: throw IOException("响应体为空")
        }

        // 2. 保存 HTML 文件 (对应 Python 的 with open(...) f.write(...))
        // 注意：在 Android 环境中，文件路径需要处理权限
        // File("$dirForHtml/get_homework_grade_response.html").writeText(responseText)
        // 实际使用时，请使用 Android 存储 API 代替 Java File API

        // 3. HTML 解析 (Jsoup 对应 Beautiful Soup)
        val document = Jsoup.parse(responseText)

        // 4. 查找元素
        // Jsoup 的 getElementById() 是通过 ID 查找的最快方式
        val oldScoreElement = document.getElementById("oldScore")

        // 5. 提取值 (对应 Python 的 element['value'])
        return@withContext if (oldScoreElement != null) {
            // 使用 .attr("value") 来获取属性值
            val scoreValue = oldScoreElement.attr("value")
            println("成功找到 id='oldScore' 元素，其 value 属性值为: $scoreValue")
            scoreValue
        } else {
            println("未找到 id='oldScore' 的元素。")
            "N/A"
        }

    } catch (e: IOException) {
        // 捕获网络连接或 IO 错误
        println("网络请求失败或文件操作错误: ${e.message}")
        return@withContext "Error: ${e.message}"
    } catch (e: Exception) {
        // 捕获其他运行时异常，如解析错误
        println("处理响应时发生错误: ${e.message}")
        return@withContext "Error: ${e.message}"
    }
}

