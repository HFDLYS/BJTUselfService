package team.bjtuss.bjtuselfservice.component

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONArray
import org.json.JSONObject
import team.bjtuss.bjtuselfservice.MainApplication
import team.bjtuss.bjtuselfservice.entity.HomeworkEntity
import team.bjtuss.bjtuselfservice.repository.SmartCurriculumPlatformRepository
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URLEncoder

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
//            .header("Cookie", "JSESSIONID=$jsessionId")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("上传失败，状态码：${response.code}")
            }
            val responseBody = response.body?.string() ?: ""
//            Log.d("HomeworkUploader", "上传响应: $responseBody")
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
