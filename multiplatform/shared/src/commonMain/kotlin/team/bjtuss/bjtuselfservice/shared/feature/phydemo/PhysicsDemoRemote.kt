package team.bjtuss.bjtuselfservice.shared.feature.phydemo

import com.fleeksoft.ksoup.Ksoup
import team.bjtuss.bjtuselfservice.shared.auth.Credentials
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpMethod
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpRequest
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpTransport

/** Read-only integration. The login POST is the only write request. */
class PhysicsDemoRemote(private val transport: SchoolHttpTransport) {
    suspend fun fetch(credentials: Credentials): List<PhysicsDemoBooking> {
        transport.clearPhysicsDemoSession()
        suspend fun request(method: SchoolHttpMethod, path: String, fields: Map<String,String> = emptyMap()): String {
            require(path in listOf("login", "appointment/"))
            val response = transport.executeWithoutRedirects(SchoolHttpRequest(method, "$DEMO_ORIGIN$DEMO_PREFIX$path",
                headers = mapOf("Cache-Control" to "no-cache", "Referer" to "$DEMO_ORIGIN${DEMO_PREFIX}login"), formFields = fields))
            if (response.statusCode in 300..399) {
                val target = response.header("Location").orEmpty()
                if (method == SchoolHttpMethod.POST && (target.startsWith(DEMO_PREFIX) || target.startsWith("$DEMO_ORIGIN$DEMO_PREFIX"))) return ""
                throw PhysicsDemoFailure("演示实验登录失效，请检查独立账号和密码。")
            }
            if (response.statusCode !in 200..299) throw PhysicsDemoFailure("演示实验系统暂时不可用。")
            return response.bodyText()
        }
        val doc = Ksoup.parse(request(SchoolHttpMethod.GET, "login"))
        val form = doc.selectFirst("form") ?: throw PhysicsDemoFailure("未找到演示实验登录表单。")
        if (form.attr("action") != "${DEMO_PREFIX}login" || !form.attr("method").equals("post", ignoreCase = true) || form.selectFirst("input[name=username]") == null ||
            form.selectFirst("input[name=password]") == null) throw PhysicsDemoFailure("演示实验登录表单发生变化。")
        val fields = form.select("input[type=hidden][name]").associate { it.attr("name") to it.attr("value") }.toMutableMap()
        fields["username"] = credentials.username
        fields["password"] = credentials.password
        request(SchoolHttpMethod.POST, "login", fields)
        return parsePhysicsDemoBookings(request(SchoolHttpMethod.GET, "appointment/"))
    }
}
