package team.bjtuss.bjtuselfservice.utils

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.Request

import team.bjtuss.bjtuselfservice.repository.SmartCurriculumPlatformRepository
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 比较两个对象的所有属性，返回值不相同的属性及其值
 * @param obj1 第一个对象
 * @param obj2 第二个对象
 * @return 包含不同属性的Map，key为属性名，value为包含两个对象该属性值的Pair
 */
object KotlinUtils {
    val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    val SECRET_KEY = "新海天是天!"

    @JvmStatic
    fun getDifferentFields(obj1: Any?, obj2: Any?): Map<String, Pair<Any?, Any?>> {
        // 参数校验
        requireNotNull(obj1) { "第一个比较对象不能为null" }
        requireNotNull(obj2) { "第二个比较对象不能为null" }
        require(obj1::class == obj2::class) { "两个对象必须是同一个类" }

        return obj1::class.java.declaredFields
            .asSequence()
            .onEach { it.isAccessible = true }
            .filter { field ->
                val value1 = field.get(obj1)
                val value2 = field.get(obj2)
                !isEquals(value1, value2)
            }
            .associate { field ->
                field.name to Pair(
                    field.get(obj1),
                    field.get(obj2)
                )
            }
    }

    private fun isEquals(value1: Any?, value2: Any?): Boolean = when {
        value1 == null && value2 == null -> true
        value1 == null || value2 == null -> false
        else -> value1 == value2
    }

    fun encryptStudentId(studentId: String, key: String): String {
        // 准备密钥
        val keyBytes = key.toByteArray(Charsets.UTF_8).copyOf(32) // 确保密钥为32字节
        val secretKey = SecretKeySpec(keyBytes, "AES")

        // 生成随机IV
        val iv = ByteArray(16)
        java.security.SecureRandom().nextBytes(iv)

        // 初始化加密器
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, IvParameterSpec(iv))

        // 加密数据
        val encryptedBytes = cipher.doFinal(studentId.toByteArray(Charsets.UTF_8))

        // 使用URL安全的Base64编码
        val ivBase64 = Base64.getUrlEncoder().encodeToString(iv)
        val ctBase64 = Base64.getUrlEncoder().encodeToString(encryptedBytes)

        return "$ivBase64:$ctBase64"
    }

    fun getCookieOfClient(): String {
        var cookies = ""
        SmartCurriculumPlatformRepository.client.cookieJar.loadForRequest(
            Request.Builder().url("https://aa.bjtu.edu.cn").build().url
        )
            .forEach {
                cookies += "${it.name}=${it.value};"
            }
        return cookies
    }

    fun getCookieByUrl(url: String): String {
        var cookies = ""
        SmartCurriculumPlatformRepository.client.cookieJar.loadForRequest(
            Request.Builder().url(url).build().url
        )
            .forEach {
                cookies += "${it.name}=${it.value};"
            }
        return cookies
    }

    fun getJSESSIONIDOfHomeworkToDownload(): String {
        var cookies = ""

        SmartCurriculumPlatformRepository.client.cookieJar.loadForRequest(
            Request.Builder().url("http://123.121.147.7:88/ve//downloadZyFj.shtml").build().url
        ).forEach {
            cookies += "${it.name}=${it.value};"
        }
        return cookies
    }
}