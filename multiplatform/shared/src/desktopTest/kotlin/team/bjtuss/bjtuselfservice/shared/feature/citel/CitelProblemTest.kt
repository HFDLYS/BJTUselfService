package team.bjtuss.bjtuselfservice.shared.feature.citel

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.runBlocking
import kotlin.test.*
import team.bjtuss.bjtuselfservice.shared.auth.Credentials
import team.bjtuss.bjtuselfservice.shared.cache.CacheStore
import team.bjtuss.bjtuselfservice.shared.cache.db.CacheDatabaseSql
import team.bjtuss.bjtuselfservice.shared.files.*
import team.bjtuss.bjtuselfservice.shared.network.*

class CitelProblemTest {
    private val task = CitelTask(99001, 99, "测试课程", "测试题目", "$CITEL_BASE/mod/programming/view.php?id=99001", true)
    private fun link(test: Int, type: String, submit: Boolean = false) =
        "$CITEL_BASE/mod/programming/testcase/download_io.php?a=99002&test=$test&type=$type" + if (submit) "&submit=99003" else ""
    private fun cell(test: Int, type: String, submit: Boolean = false) =
        "<td><table><tr><td>truncated preview</td></tr></table><a class='showasplaintext' href='${link(test, type, submit)}'>plain</a></td>"
    private fun view(statement: String = "<h4>样例</h4><pre>  1\n\n2</pre><p>公式：\$x^2\$</p>") = """
        <div id="region-main"><h1 class="name">测试题目</h1><div class="grade">Grade: 10 / Input:console</div>
        <div id="description"><div class="no-overflow">$statement</div></div>
        <a href="$CITEL_BASE/mod/programming/result.php?a=99002">result</a>
        <div id="pub-testcase-table"><table><tbody><tr><td>Test Case 1</td>${cell(99004, "in")}${cell(99004, "out")}<td>1 s</td><td>32 MB</td></tr>
        <tr class="emptyrow"><td>2</td><td></td><td></td><td></td><td></td></tr></tbody></table></div></div>
    """.trimIndent()
    private fun result() = """
        <div id="region-main">There are 2 test cases. Your program has passed 1 of them and failed in 1 of them.
        <table id="test-result-detail-table"><tbody>${listOf(99004,99005).mapIndexed { index, test ->
            "<tr><td>${index+1}</td><td>1</td><td>1 s</td><td>32 MB</td>${cell(test,"in")}${cell(test,"out")}${cell(test,"out",true)}<td></td><td>0.02</td><td>8</td><td>Secure test</td><td>${if(index==0) "yes" else "no"}</td><td>${if(index==0) "AC" else "WA"}</td></tr>"
        }.joinToString("")}<tr class="emptyrow"><td>30</td></tr></tbody></table></div>
    """

    @Test fun parsesBothStatementStylesAndIgnoresEmptyRows() {
        val problem = parseCitelProblem(view(), task.url)
        assertEquals(1, problem.publicCases.size)
        assertEquals(link(99004, "in"), problem.publicCases.single().inputUrl)
        assertTrue("\$x^2\$" in citelHtmlToMarkdown(problem.statementHtml, task.url))
        assertTrue("  1\n\n2" in citelHtmlToMarkdown(problem.statementHtml, task.url))
        val alternate = parseCitelProblem(view("<div>第一行<br>第二行</div>"), task.url)
        assertTrue("第一行\n第二行" in citelHtmlToMarkdown(alternate.statementHtml, task.url))
    }

    @Test fun nestedTablesDoNotShiftColumnsOrHideSecureCases() {
        val parsed = parseCitelProgrammingTestResult(result(), CitelProgrammingResult("WA", false, emptyList(), false, resultUrl = "$CITEL_BASE/mod/programming/result.php?a=99002"))
        assertEquals(2, parsed.testCases.size)
        assertEquals(link(99005, "out", true), parsed.testCases[1].actualUrl)
        assertFalse(parsed.testCases[1].passed!!)
        assertEquals("0.02", parsed.testCases[1].timeUsed)
        assertEquals("Secure test", parsed.testCases[1].caseType)
    }

    @Test fun caseIdentityIgnoresDownloadFlagAndQueryOrder() {
        assertTrue(sameCitelInput(link(99004, "in") + "&download=0", link(99004, "in") + "&download=1"))
        assertFalse(sameCitelInput(link(99004, "in"), link(99005, "in")))
    }

    @Test fun rejectsMissingStatementAndUntrustedIoLinks() {
        assertFailsWith<CitelFailure> { parseCitelProblem("<h1 class=name>fixture</h1>") }
        val parsed = parseCitelProblem(view().replace("https://citel.bjtu.edu.cn", "https://untrusted.invalid"), task.url)
        assertNull(parsed.resultUrl)
        assertNull(parsed.publicCases.single().inputUrl)
    }

    @Test fun fetchDownloadsFullPlainTextAndSubmittedNonPublicCases() = runBlocking {
        val requests = mutableListOf<SchoolHttpRequest>()
        val remote = CitelRemote(object : SchoolHttpTransport {
            override fun clearSession() = Unit
            override suspend fun execute(request: SchoolHttpRequest): SchoolHttpResponse {
                requests += request
                assertEquals(SchoolHttpMethod.GET, request.method)
                return when {
                    request.url == task.url -> response(request.url, view())
                    request.url.contains("result.php") -> response(request.url, result())
                    else -> response(request.url, "line1\r\nline2\r\n", "text/plain;charset=UTF-8")
                }
            }
        })
        val full = remote.problem(Credentials("fixture", "fixture"), task.copy(status = "WA"), true)
        assertEquals(2, full.cases.size)
        assertEquals("line1\nline2\n", full.cases[1].input)
        assertFalse(full.cases[1].public)
        assertNotNull(full.cases[1].actual)
        assertEquals(8, requests.size) // view + result + 2 * 3 I/O
        assertTrue(full.withResults)
    }

    @Test fun newProblemOnlyReadsPublicCases() = runBlocking {
        var calls = 0
        val remote = CitelRemote(object : SchoolHttpTransport {
            override fun clearSession() = Unit
            override suspend fun execute(request: SchoolHttpRequest): SchoolHttpResponse {
                calls++
                assertFalse(request.url.contains("result.php"))
                return response(request.url, if (request.url == task.url) view() else "", if (request.url == task.url) "text/html" else "text/plain")
            }
        })
        val full = remote.problem(Credentials("fixture", "fixture"), task, true)
        assertEquals(3, calls)
        assertEquals("", full.cases.single().input)
        assertFalse(full.withResults)
    }

    @Test fun expiredSessionRecoversOnceAndNeverWritesAssignment() = runBlocking {
        var viewCount = 0
        var loginCount = 0
        val remote = CitelRemote(object : SchoolHttpTransport {
            override fun clearSession() = Unit
            override suspend fun execute(request: SchoolHttpRequest): SchoolHttpResponse = when {
                request.method == SchoolHttpMethod.POST -> {
                    loginCount++
                    assertEquals("$CITEL_BASE/login/index.php", request.url)
                    response("$CITEL_BASE/my/", "<div id=region-main>logged in</div>")
                }
                request.url.contains("login/index.php") -> response(request.url,
                    "<form action='$CITEL_BASE/login/index.php'><input type=password name=password><input name=logintoken value=fixture></form>")
                request.url == task.url -> {
                    viewCount++
                    if (viewCount == 1) response("$CITEL_BASE/login/index.php", "<input type=password name=password>") else response(task.url, view())
                }
                else -> response(request.url, "fixture\r\n", "text/plain")
            }
        })
        assertEquals("fixture\n", remote.problem(Credentials("fixture", "fixture"), task, false).cases.single().input)
        assertEquals(1, loginCount)
        assertEquals(2, viewCount)
    }

    @Test fun rejectsHtmlIoAndServerError() = runBlocking {
        for (code in listOf(200, 500)) {
            val remote = CitelRemote(object : SchoolHttpTransport {
            override fun clearSession() = Unit
                override suspend fun execute(request: SchoolHttpRequest) = if (request.url == task.url) response(task.url, view())
                    else SchoolHttpResponse(code, request.url, mapOf("Content-Type" to listOf("text/html")), "<html>error</html>".encodeToByteArray())
            })
            assertFailsWith<CitelFailure> { remote.problem(Credentials("fixture", "fixture"), task, false) }
        }
    }

    @Test fun markdownHandlesLinksListsTablesImagesAndSafeFences() {
        val md = citelHtmlToMarkdown("<h2>标题</h2><p><b>粗体</b> <i>斜体</i><a href='https://example.invalid/page'>链接</a><img src='javascript:bad' alt='bad'></p><ol><li>一</li><li>二</li></ol><table><tr><th>A</th><th>B</th></tr><tr><td>1</td><td>2</td></tr></table><script>ignored</script>", task.url)
        assertTrue("## 标题" in md)
        assertTrue("**粗体**" in md)
        assertTrue("1. 一" in md)
        assertTrue("| --- | --- |" in md)
        assertFalse("javascript" in md)
        assertFalse("ignored" in md)
        assertTrue(citelCodeBlock("```\nfixture").startsWith("````\n"))
    }

    @Test fun cacheIsAccountScopedAndExportPreservesExactBytes() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CacheDatabaseSql.Schema.create(driver)
        val cache = CacheStore(driver)
        try {
            val full = CitelCachedProblem("fixture", task.id, 0, parseCitelProblem(view(), task.url).copy(cases = listOf(
                CitelProblemCase(1, "case", input = "1\n\n", expected = "2\n", actual = "3\n", passed = false))))
            cache.saveCitelProblem("scope", full)
            assertEquals(full, cache.citelProblem("scope", "fixture", task.id))
            assertNull(cache.citelProblem("scope", "another-user", task.id))
            val files = citelProblemFiles(task, full)
            assertEquals(listOf("problem.md", "1.in", "1.out"), files.map { it.content.fileName })
            assertContentEquals("1\n\n".encodeToByteArray(), files[1].content.bytes)
            assertTrue("我的输出" in files[0].content.bytes.decodeToString())
        } finally { cache.close() }
    }

    @Test fun directoryFailureAbortsOnlyNewSession() = runBlocking {
        var aborted = false
        val gateway = object : CoursewareDirectoryGateway {
            override val isDirectoryExportAvailable = true
            override suspend fun openDirectory(directoryName: String) = CoursewareDirectoryOpenResult.Opened(object : CoursewareDirectoryWriteSession {
                override suspend fun write(file: CoursewareDirectoryFile) = HomeworkFileSaveResult.Failed(HomeworkFileGatewayFailure.IO)
                override suspend fun commit(): HomeworkFileSaveResult = error("must not commit")
                override suspend fun abort() { aborted = true }
            })
        }
        assertIs<HomeworkFileSaveResult.Failed>(exportCitelProblem(task, CitelCachedProblem("fixture", task.id, 0, parseCitelProblem(view())), gateway))
        assertTrue(aborted)
    }
    @Test fun modelOnlyFetchesFullTextOnExplicitActionAndKeepsOldCacheOnFailure() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CacheDatabaseSql.Schema.create(driver)
        val cache = CacheStore(driver)
        var problemCalls = 0
        var fail = false
        val model = CitelModel("scope", cache, null, object : CitelDataSource {
            override suspend fun fetch(credentials: Credentials) = listOf(task)
            override suspend fun problem(credentials: Credentials, task: CitelTask, withResults: Boolean): CitelProblem {
                problemCalls++
                if (fail) throw CitelFailure("fixture failure")
                return parseCitelProblem(view(), task.url)
            }
        })
        try {
            model.configure("fixture", "fixture", false, true)
            model.refresh()
            model.showTask(task)
            assertEquals(0, problemCalls)
            model.fetchProblem()
            val cached = model.state.value.problems.getValue(task.id)
            fail = true
            model.fetchProblem()
            assertEquals(cached, model.state.value.problems[task.id])
            assertEquals("fixture failure", model.state.value.problemMessage)
            assertFalse(model.state.value.problemBusy)
            model.configure("another", "fixture", false, false)
            assertTrue(model.state.value.problems.isEmpty())
        } finally { cache.close() }
    }

    private fun response(url: String, html: String, type: String = "text/html") =
        SchoolHttpResponse(200, url, mapOf("Content-Type" to listOf(type)), html.encodeToByteArray())
}
