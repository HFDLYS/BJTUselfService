package team.bjtuss.bjtuselfservice.shared.feature.citel

import com.fleeksoft.ksoup.Ksoup
import io.ktor.http.Url
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import team.bjtuss.bjtuselfservice.shared.cache.CacheStore

@Serializable
data class CitelProblemCase(
    val number: Int,
    val name: String,
    val inputUrl: String? = null,
    val expectedUrl: String? = null,
    val actualUrl: String? = null,
    val timeLimit: String = "",
    val memoryLimit: String = "",
    val input: String? = null,
    val expected: String? = null,
    val actual: String? = null,
    val passed: Boolean? = null,
    val result: String = "",
    val timeUsed: String = "",
    val public: Boolean = true,
)

@Serializable
data class CitelProblem(
    val title: String,
    val gradeLine: String,
    val statementHtml: String,
    val publicCases: List<CitelProblemCase> = emptyList(),
    val resultUrl: String? = null,
    val cases: List<CitelProblemCase> = publicCases,
    val withResults: Boolean = false,
)

@Serializable
data class CitelCachedProblem(val username: String, val moduleId: Int, val fetchedAt: Long, val problem: CitelProblem)

private val problemJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
fun CacheStore.citelProblem(scope: String, username: String, moduleId: Int): CitelCachedProblem? = runCatching {
    problemJson.decodeFromString<CitelCachedProblem>(metadata(scope, "citel.problem.$moduleId").orEmpty())
}.getOrNull()?.takeIf { it.username == username && it.moduleId == moduleId }

fun CacheStore.saveCitelProblem(scope: String, value: CitelCachedProblem) =
    putMetadata(scope, "citel.problem.${value.moduleId}", problemJson.encodeToString(value))

/** Only use an I/O link actually found in this cell, with the expected type. */
internal fun citelIoLink(cell: com.fleeksoft.ksoup.nodes.Element?, type: String): String? {
    val links = cell?.select("a[href]")?.map { it.absUrl("href") }?.filter { value ->
        isCitelUrl(value) && runCatching {
            val url = Url(value)
            url.encodedPath == "/mlsv3/mod/programming/testcase/download_io.php" &&
                url.parameters["type"] == type && url.parameters["a"]?.toIntOrNull() != null &&
                url.parameters["test"]?.toIntOrNull() != null
        }.getOrDefault(false)
    }.orEmpty()
    return links.firstOrNull { Url(it).parameters["download"] == "0" } ?: links.firstOrNull()
}

internal fun sameCitelInput(first: String?, second: String?): Boolean {
    if (first == null || second == null) return false
    val a = Url(first); val b = Url(second)
    return listOf("a", "test", "type").all { a.parameters[it] == b.parameters[it] }
}

fun parseCitelProblem(html: String, pageUrl: String = "$CITEL_BASE/mod/programming/view.php"): CitelProblem {
    val doc = Ksoup.parse(html, pageUrl)
    doc.outputSettings().prettyPrint(false)
    val title = doc.selectFirst("h1.name")?.text()?.takeIf { it.isNotBlank() }
        ?: throw CitelFailure("CITEL 题目标题缺失，请刷新后重试。")
    val statement = doc.selectFirst("#description > .no-overflow")
        ?: throw CitelFailure("CITEL 题面格式发生变化。")
    val cases = doc.select("#pub-testcase-table > tbody > tr:not(.emptyrow), #pub-testcase-table table > tbody > tr:not(.emptyrow)").mapIndexedNotNull { index, row ->
        val cells = row.children().filter { it.tagName() == "td" }
        if (cells.size != 5) return@mapIndexedNotNull null
        val number = Regex("\\d+").find(cells[0].text())?.value?.toIntOrNull() ?: index + 1
        CitelProblemCase(number, cells[0].text(), citelIoLink(cells[1], "in"), citelIoLink(cells[2], "out"),
            timeLimit = cells[3].text(), memoryLimit = cells[4].text())
    }
    val resultUrl = doc.select("a[href]").map { it.absUrl("href") }.firstOrNull { value ->
        isCitelUrl(value) && runCatching {
            val url = Url(value)
            url.encodedPath == "/mlsv3/mod/programming/result.php" && url.parameters["a"]?.toIntOrNull() != null
        }.getOrDefault(false)
    }
    return CitelProblem(title, doc.selectFirst("div.grade")?.text().orEmpty(), statement.html(), cases, resultUrl)
}
