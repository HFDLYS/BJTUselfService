package team.bjtuss.bjtuselfservice.shared.data.home

import team.bjtuss.bjtuselfservice.shared.domain.home.HomeStatus
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpMethod
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpRequest
import team.bjtuss.bjtuselfservice.shared.network.SchoolHttpTransport
import team.bjtuss.bjtuselfservice.shared.network.looksLikeSessionExpired

private const val STATUS_URL = "https://mis.bjtu.edu.cn/osys_ajax_wrap/"

class HomeStatusRemoteException(val reason: HomeStatusFailure) : Exception("Home status request failed: $reason")

class SchoolHomeStatusRemoteDataSource(
    private val transport: SchoolHttpTransport,
) : HomeStatusRemoteDataSource {
    override suspend fun fetch(): HomeStatus {
        val response = transport.execute(SchoolHttpRequest(SchoolHttpMethod.GET, STATUS_URL))
        if (response.looksLikeSessionExpired() || "cas.bjtu.edu.cn" in response.finalUrl || "sso.bjtu.edu.cn" in response.finalUrl) {
            throw HomeStatusRemoteException(HomeStatusFailure.SESSION_EXPIRED)
        }
        if (response.statusCode !in 200..299 || !response.finalUrl.startsWith("https://mis.bjtu.edu.cn/")) {
            throw HomeStatusRemoteException(HomeStatusFailure.NETWORK)
        }
        return when (val parsed = parseHomeStatusJson(response.bodyText())) {
            is HomeStatusParseResult.Success -> parsed.status
            is HomeStatusParseResult.Failure -> throw HomeStatusRemoteException(HomeStatusFailure.PARSE)
        }
    }
}
