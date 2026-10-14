package team.bjtuss.bjtuselfservice.shared.feature.citel

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import team.bjtuss.bjtuselfservice.shared.domain.homework.HomeworkFileContent
import team.bjtuss.bjtuselfservice.shared.files.*

/** Fence length follows the content, so a testcase containing Markdown cannot close the block. */
internal fun citelCodeBlock(text: String): String {
    val fence = "`".repeat(maxOf(3, (Regex("`+").findAll(text).maxOfOrNull { it.value.length } ?: 0) + 1))
    return "$fence\n$text${if (text.endsWith('\n')) "" else "\n"}$fence"
}

/** Small HTML renderer; TeX and preformatted whitespace remain intact. Never execute page HTML. */
fun citelHtmlToMarkdown(html: String, baseUrl: String): String {
    fun safeUrl(element: Element, attr: String): String? = element.absUrl(attr).takeIf { value ->
        runCatching { val u = Url(value); u.protocol.name in setOf("http", "https") && u.user == null && u.password == null }.getOrDefault(false)
    }?.replace("(", "%28")?.replace(")", "%29")
    fun render(node: Node): String {
        if (node is TextNode) return node.getWholeText().replace(Regex("\\s+"), " ")
        if (node !is Element) return node.childNodes().joinToString("") { render(it) }
        val tag = node.tagName().lowercase()
        if (tag in setOf("script", "style", "iframe", "form", "input", "button")) return ""
        if (tag == "pre") return "\n\n${citelCodeBlock(node.wholeText())}\n\n"
        val body = node.childNodes().joinToString("") { render(it) }
        return when (tag) {
            "h1", "h2", "h3", "h4", "h5", "h6" -> "\n\n${"#".repeat(tag.last().digitToInt())} ${body.trim()}\n\n"
            "p", "div", "section" -> "\n\n${body.trim()}\n\n"
            "br" -> "\n"
            "b", "strong" -> "**$body**"
            "i", "em" -> "*$body*"
            "code" -> "`${body.trim()}`"
            "ul", "ol" -> "\n\n" + node.children().filter { it.tagName() == "li" }.mapIndexed { index, li ->
                val prefix = if (tag == "ol") "${index + 1}. " else "- "
                prefix + li.childNodes().joinToString("") { render(it) }.trim().replace("\n", "\n  ")
            }.joinToString("\n") + "\n\n"
            "a" -> safeUrl(node, "href")?.let { "[$body]($it)" } ?: body
            "img" -> safeUrl(node, "src")?.let { "![${node.attr("alt")}]($it)" }.orEmpty()
            "table" -> {
                val rows = node.select("tr").map { tr -> tr.children().filter { it.tagName() in setOf("td", "th") }
                    .map { render(it).trim().replace("|", "\\|").replace("\n", "<br>") } }.filter { it.isNotEmpty() }
                val width = rows.maxOfOrNull { it.size } ?: 0
                if (width == 0) "" else "\n\n" + rows.map { row -> "| " + (row + List(width - row.size) { "" }).joinToString(" | ") + " |" }
                    .toMutableList().also { it.add(1, "| " + List(width) { "---" }.joinToString(" | ") + " |") }.joinToString("\n") + "\n\n"
            }
            else -> body
        }
    }
    // Do not normalize the output globally: that would destroy blank lines in <pre>.
    return render(Ksoup.parse(html, baseUrl).body()).trim()
}

fun citelProblemMarkdown(task: CitelTask, cached: CitelCachedProblem): String = buildString {
    val problem = cached.problem
    appendLine("# ${problem.title}\n")
    appendLine("- 来源：CITEL")
    appendLine("- 课程：${task.courseName}")
    appendLine("- 模块：${task.id}")
    appendLine("- 抓取时间：${citelDateText(cached.fetchedAt)}（北京时间）")
    appendLine("- 截止：${task.dueTime?.let(::citelDateText) ?: "未提供"}")
    appendLine("- 评测：${task.submissionStatusText()}")
    if (problem.gradeLine.isNotBlank()) appendLine("- ${problem.gradeLine}")
    appendLine("- [原题](${task.url})")
    appendLine("\n## 题面\n")
    appendLine(citelHtmlToMarkdown(problem.statementHtml, task.url))
    appendLine("\n## 测试用例\n")
    appendLine(if (problem.withResults) "包含结果页实际提供的用例；非公开用例仅来自页面上的下载链接。" else "仅包含题目页公开用例；尚未取得结果页用例。")
    if (problem.cases.isEmpty()) appendLine("平台未提供可读取的测试用例。")
    problem.cases.forEachIndexed { index, test ->
        appendLine("\n### 用例 ${index + 1} · ${test.name}${if (!test.public) "（结果页）" else "（公开）"}\n")
        if (test.timeLimit.isNotBlank()) appendLine("- 时间限制：${test.timeLimit}")
        if (test.memoryLimit.isNotBlank()) appendLine("- 内存限制：${test.memoryLimit}")
        appendLine("\n输入：\n\n${test.input?.let(::citelCodeBlock) ?: "页面未提供下载链接。"}")
        appendLine("\n期望输出：\n\n${test.expected?.let(::citelCodeBlock) ?: "页面未提供下载链接。"}")
        test.actual?.let { appendLine("\n我的输出：\n\n${citelCodeBlock(it)}") }
        test.passed?.let { appendLine("\n- 是否通过：${if (it) "是" else "否"}") }
        if (test.result.isNotBlank()) appendLine("- 判题：${test.result}")
        if (test.timeUsed.isNotBlank()) appendLine("- 耗时：${test.timeUsed}")
    }
}

fun citelProblemFiles(task: CitelTask, cached: CitelCachedProblem): List<CoursewareDirectoryFile> = buildList {
    add(CoursewareDirectoryFile(emptyList(), HomeworkFileContent("problem.md", "text/markdown", citelProblemMarkdown(task, cached).encodeToByteArray())))
    cached.problem.cases.forEachIndexed { index, test ->
        test.input?.let { add(CoursewareDirectoryFile(listOf("tests"), HomeworkFileContent("${index + 1}.in", "text/plain", it.encodeToByteArray()))) }
        test.expected?.let { add(CoursewareDirectoryFile(listOf("tests"), HomeworkFileContent("${index + 1}.out", "text/plain", it.encodeToByteArray()))) }
    }
}

suspend fun exportCitelProblem(task: CitelTask, cached: CitelCachedProblem, gateway: CoursewareDirectoryGateway): HomeworkFileSaveResult {
    val opened = gateway.openDirectory(safeExportPathSegment("CITEL-${task.id}-${task.title}"))
    return when (opened) {
        CoursewareDirectoryOpenResult.Cancelled -> HomeworkFileSaveResult.Cancelled
        is CoursewareDirectoryOpenResult.Failed -> HomeworkFileSaveResult.Failed(opened.reason)
        is CoursewareDirectoryOpenResult.Opened -> {
            var committed = false
            try {
                for (file in citelProblemFiles(task, cached)) {
                    val result = opened.session.write(file)
                    if (result != HomeworkFileSaveResult.Saved) return result
                }
                opened.session.commit().also { committed = it == HomeworkFileSaveResult.Saved }
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) { HomeworkFileSaveResult.Failed(HomeworkFileGatewayFailure.IO)
            } finally { if (!committed) opened.session.abort() }
        }
    }
}
