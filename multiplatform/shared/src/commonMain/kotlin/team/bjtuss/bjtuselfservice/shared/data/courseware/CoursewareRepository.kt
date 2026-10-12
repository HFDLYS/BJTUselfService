package team.bjtuss.bjtuselfservice.shared.data.courseware

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import team.bjtuss.bjtuselfservice.shared.cache.CacheStore
import team.bjtuss.bjtuselfservice.shared.domain.courseware.CoursewareNode
import team.bjtuss.bjtuselfservice.shared.domain.courseware.CoursewareCourse
import team.bjtuss.bjtuselfservice.shared.domain.courseware.CoursewareSnapshot
import team.bjtuss.bjtuselfservice.shared.domain.courseware.CoursewareNodeKind
import team.bjtuss.bjtuselfservice.shared.domain.courseware.findCoursewareNode
import team.bjtuss.bjtuselfservice.shared.domain.homework.HomeworkFileContent

private const val COURSEWARE_CACHE_KEY = "courseware_tree_v1"

enum class CoursewareSyncFailure {
    NETWORK,
    SESSION_EXPIRED,
    MALFORMED_RESPONSE,
    SECURE_CHANNEL_UNAVAILABLE,
    CACHE,
    /** 老师未开放学生下载（`stu_download` != "2"），服务器会对下载链接返回 500。 */
    DOWNLOAD_NOT_ALLOWED,
}

sealed interface CoursewareRefreshResult {
    data class Success(val snapshot: CoursewareSnapshot) : CoursewareRefreshResult
    data class Failure(
        val snapshot: CoursewareSnapshot,
        val reason: CoursewareSyncFailure,
    ) : CoursewareRefreshResult
}

sealed interface CoursewareOperationResult<out T> {
    data class Success<T>(val value: T) : CoursewareOperationResult<T>
    data class Failure(val reason: CoursewareSyncFailure) : CoursewareOperationResult<Nothing>
}

interface CoursewareLocalDataSource {
    fun load(accountScope: String): CoursewareSnapshot
    fun replace(accountScope: String, snapshot: CoursewareSnapshot)
}

class CacheStoreCoursewareLocalDataSource(
    private val cacheStore: CacheStore,
) : CoursewareLocalDataSource {
    override fun load(accountScope: String): CoursewareSnapshot {
        val encoded = cacheStore.metadata(accountScope, COURSEWARE_CACHE_KEY)
            ?: return CoursewareSnapshot(emptyList())
        return decodeCoursewareSnapshot(encoded)
            ?: throw IllegalStateException("Invalid courseware cache")
    }

    override fun replace(accountScope: String, snapshot: CoursewareSnapshot) {
        cacheStore.putMetadata(accountScope, COURSEWARE_CACHE_KEY, encodeCoursewareSnapshot(snapshot))
    }
}

interface CoursewareRepository {
    fun load(): CoursewareSnapshot
    suspend fun refresh(): CoursewareRefreshResult
    suspend fun loadCourse(
        snapshot: CoursewareSnapshot,
        courseId: Int,
    ): CoursewareOperationResult<CoursewareSnapshot> =
        CoursewareOperationResult.Failure(CoursewareSyncFailure.MALFORMED_RESPONSE)

    /**
     * 并发拉取多门课的顶层目录，合并后一次落库。
     * 用于同步后预填各课「N 个顶层项目」，避免点选才加载。
     */
    suspend fun loadCoursesConcurrently(
        snapshot: CoursewareSnapshot,
        courseIds: List<Int>,
        concurrency: Int = 3,
    ): CoursewareOperationResult<CoursewareSnapshot> =
        CoursewareOperationResult.Failure(CoursewareSyncFailure.MALFORMED_RESPONSE)

    suspend fun loadFolder(
        snapshot: CoursewareSnapshot,
        courseId: Int,
        folderKey: String,
    ): CoursewareOperationResult<CoursewareSnapshot> =
        CoursewareOperationResult.Failure(CoursewareSyncFailure.MALFORMED_RESPONSE)

    /**
     * 后台把尚未展开的文件夹补齐。调用方不持有操作锁；仓库内部 2 路并发、一次落库。
     * 没有未加载文件夹时原样返回，不写缓存。
     */
    suspend fun loadUnloadedFolders(
        snapshot: CoursewareSnapshot,
        concurrency: Int = 2,
    ): CoursewareOperationResult<CoursewareSnapshot> =
        CoursewareOperationResult.Success(snapshot)

    suspend fun downloadResource(node: CoursewareNode): CoursewareOperationResult<HomeworkFileContent>
    suspend fun downloadTeachingCalendar(course: CoursewareCourse): CoursewareOperationResult<HomeworkFileContent>
}

class DefaultCoursewareRepository(
    accountScope: String,
    private val local: CoursewareLocalDataSource,
    private val remote: CoursewareRemoteDataSource,
) : CoursewareRepository {
    private val accountScope = accountScope.trim().also {
        require(it.isNotEmpty()) { "accountScope cannot be blank" }
    }
    private val persistMutex = Mutex()

    override fun load(): CoursewareSnapshot = local.load(accountScope)

    override suspend fun refresh(): CoursewareRefreshResult {
        val fallback = runCatching(::load).getOrElse { CoursewareSnapshot(emptyList()) }
        val remoteSnapshot = try {
            remote.fetchSnapshot()
        } catch (error: CancellationException) {
            throw error
        } catch (error: CoursewareRemoteException) {
            return CoursewareRefreshResult.Failure(fallback, error.reason.toSyncFailure())
        } catch (_: Exception) {
            return CoursewareRefreshResult.Failure(fallback, CoursewareSyncFailure.NETWORK)
        }
        return persistMutex.withLock {
            val latest = runCatching(::load).getOrElse { fallback }
            if (remoteSnapshot.courses.isEmpty() && latest.courses.isNotEmpty()) {
                CoursewareRefreshResult.Failure(latest, CoursewareSyncFailure.NETWORK)
            } else {
                persistCatalog(remoteSnapshot.mergeCachedChildren(latest), fallback = latest)
            }
        }
    }

    override suspend fun loadCourse(
        snapshot: CoursewareSnapshot,
        courseId: Int,
    ): CoursewareOperationResult<CoursewareSnapshot> {
        val course = snapshot.courses.firstOrNull { it.id == courseId }
            ?: return CoursewareOperationResult.Failure(CoursewareSyncFailure.MALFORMED_RESPONSE)
        val children = try {
            remote.fetchChildren(course, parentId = 0)
        } catch (error: CancellationException) {
            throw error
        } catch (error: CoursewareRemoteException) {
            return CoursewareOperationResult.Failure(error.reason.toSyncFailure())
        } catch (_: Exception) {
            return CoursewareOperationResult.Failure(CoursewareSyncFailure.NETWORK)
        }
        if (children.hasDuplicateKeys()) {
            return CoursewareOperationResult.Failure(CoursewareSyncFailure.MALFORMED_RESPONSE)
        }
        return persistMerging { latest ->
            val current = latest.courses.firstOrNull { it.id == courseId } ?: return@persistMerging latest
            val merged = children.mergePreservingLoadedFolders(current.children)
            latest.copy(
                courses = latest.courses.map { candidate ->
                    if (candidate.id == courseId) {
                        candidate.copy(children = merged, childrenLoaded = true)
                    } else {
                        candidate
                    }
                },
            )
        }
    }

    override suspend fun loadCoursesConcurrently(
        snapshot: CoursewareSnapshot,
        courseIds: List<Int>,
        concurrency: Int,
    ): CoursewareOperationResult<CoursewareSnapshot> {
        val targets = courseIds
            .distinct()
            .mapNotNull { id -> snapshot.courses.firstOrNull { it.id == id } }
        if (targets.isEmpty()) return CoursewareOperationResult.Success(snapshot)
        val limit = concurrency.coerceIn(1, 6)
        val semaphore = Semaphore(limit)
        // 单课失败跳过，不拖垮整批。合法空列表会写入，把老师删光的课收成 0。
        val fetched: List<Pair<Int, List<CoursewareNode>>> = coroutineScope {
            targets.map { course ->
                async {
                    semaphore.withPermit {
                        try {
                            val children = remote.fetchChildren(course, parentId = 0)
                            if (children.hasDuplicateKeys()) null else course.id to children
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
            }.awaitAll().filterNotNull()
        }
        if (fetched.isEmpty()) {
            return CoursewareOperationResult.Failure(CoursewareSyncFailure.NETWORK)
        }
        val byId = fetched.toMap()
        return persistMerging { latest ->
            latest.copy(
                courses = latest.courses.map { course ->
                    val children = byId[course.id] ?: return@map course
                    course.copy(
                        children = children.mergePreservingLoadedFolders(course.children),
                        childrenLoaded = true,
                    )
                },
            )
        }
    }

    override suspend fun loadFolder(
        snapshot: CoursewareSnapshot,
        courseId: Int,
        folderKey: String,
    ): CoursewareOperationResult<CoursewareSnapshot> {
        val course = snapshot.courses.firstOrNull { it.id == courseId }
            ?: return CoursewareOperationResult.Failure(CoursewareSyncFailure.MALFORMED_RESPONSE)
        if (!course.childrenLoaded) {
            return CoursewareOperationResult.Failure(CoursewareSyncFailure.MALFORMED_RESPONSE)
        }
        val folder = findCoursewareNode(course.children, folderKey)
            ?.takeIf { it.kind == CoursewareNodeKind.FOLDER }
            ?: return CoursewareOperationResult.Failure(CoursewareSyncFailure.MALFORMED_RESPONSE)
        if (folder.childrenLoaded) return CoursewareOperationResult.Success(snapshot)

        val children = try {
            remote.fetchChildren(course, folder.id)
        } catch (error: CancellationException) {
            throw error
        } catch (error: CoursewareRemoteException) {
            return CoursewareOperationResult.Failure(error.reason.toSyncFailure())
        } catch (_: Exception) {
            return CoursewareOperationResult.Failure(CoursewareSyncFailure.NETWORK)
        }
        if (children.hasDuplicateKeys()) {
            return CoursewareOperationResult.Failure(CoursewareSyncFailure.MALFORMED_RESPONSE)
        }
        return persistMerging { latest ->
            val currentCourse = latest.courses.firstOrNull { it.id == courseId } ?: return@persistMerging latest
            val currentFolder = findCoursewareNode(currentCourse.children, folderKey)
                ?.takeIf { it.kind == CoursewareNodeKind.FOLDER }
                ?: return@persistMerging latest
            val existingKeys = currentCourse.children.keysOutside(currentFolder.stableKey)
            if (children.any { it.stableKey in existingKeys }) return@persistMerging latest
            val updatedChildren = currentCourse.children.replaceNode(folderKey) {
                it.copy(
                    children = children.mergePreservingLoadedFolders(it.children),
                    childrenLoaded = true,
                )
            } ?: return@persistMerging latest
            latest.copy(
                courses = latest.courses.map { candidate ->
                    if (candidate.id == courseId) candidate.copy(children = updatedChildren) else candidate
                },
            )
        }
    }

    override suspend fun loadUnloadedFolders(
        snapshot: CoursewareSnapshot,
        concurrency: Int,
    ): CoursewareOperationResult<CoursewareSnapshot> {
        var last = snapshot
        var changed = false
        val limit = concurrency.coerceIn(1, 6)
        repeat(COURSEWARE_FOLDER_PREFETCH_DEPTH) {
            val latest = runCatching(::load).getOrElse { last }
            val targets = latest.unloadedFolders()
            if (targets.isEmpty()) {
                return CoursewareOperationResult.Success(if (changed) latest else snapshot)
            }
            val fetched = coroutineScope {
                val semaphore = Semaphore(limit)
                targets.map { target ->
                    async {
                        semaphore.withPermit {
                            val course = latest.courses.firstOrNull { it.id == target.courseId }
                                ?: return@withPermit null
                            try {
                                val children = remote.fetchChildren(course, target.folderId)
                                if (children.hasDuplicateKeys()) null else target to children
                            } catch (error: CancellationException) {
                                throw error
                            } catch (_: Exception) {
                                null
                            }
                        }
                    }
                }.awaitAll().filterNotNull()
            }
            if (fetched.isEmpty()) {
                return CoursewareOperationResult.Success(if (changed) latest else snapshot)
            }
            when (val persisted = persistMerging { current -> current.applyFolderFetches(fetched) }) {
                is CoursewareOperationResult.Failure -> return persisted
                is CoursewareOperationResult.Success -> {
                    changed = true
                    last = persisted.value
                }
            }
        }
        return CoursewareOperationResult.Success(last)
    }

    private suspend fun persistMerging(
        transform: (CoursewareSnapshot) -> CoursewareSnapshot,
    ): CoursewareOperationResult<CoursewareSnapshot> = persistMutex.withLock {
        val latest = runCatching(::load).getOrElse { CoursewareSnapshot(emptyList()) }
        val updated = transform(latest)
        if (updated == latest) {
            CoursewareOperationResult.Success(latest)
        } else {
            persistSnapshot(updated)
        }
    }

    private fun persistCatalog(
        snapshot: CoursewareSnapshot,
        fallback: CoursewareSnapshot,
    ): CoursewareRefreshResult = try {
        local.replace(accountScope, snapshot)
        CoursewareRefreshResult.Success(local.load(accountScope))
    } catch (_: Exception) {
        CoursewareRefreshResult.Failure(
            snapshot = runCatching(::load).getOrElse { fallback },
            reason = CoursewareSyncFailure.CACHE,
        )
    }

    private fun persistSnapshot(
        snapshot: CoursewareSnapshot,
    ): CoursewareOperationResult<CoursewareSnapshot> = try {
        local.replace(accountScope, snapshot)
        CoursewareOperationResult.Success(local.load(accountScope))
    } catch (_: Exception) {
        CoursewareOperationResult.Failure(CoursewareSyncFailure.CACHE)
    }

    override suspend fun downloadResource(
        node: CoursewareNode,
    ): CoursewareOperationResult<HomeworkFileContent> = try {
        CoursewareOperationResult.Success(remote.downloadResource(node))
    } catch (error: CancellationException) {
        throw error
    } catch (error: CoursewareRemoteException) {
        CoursewareOperationResult.Failure(error.reason.toSyncFailure())
    } catch (_: Exception) {
        CoursewareOperationResult.Failure(CoursewareSyncFailure.NETWORK)
    }

    override suspend fun downloadTeachingCalendar(
        course: CoursewareCourse,
    ): CoursewareOperationResult<HomeworkFileContent> = try {
        CoursewareOperationResult.Success(remote.downloadTeachingCalendar(course))
    } catch (error: CancellationException) {
        throw error
    } catch (error: CoursewareRemoteException) {
        CoursewareOperationResult.Failure(error.reason.toSyncFailure())
    } catch (_: Exception) {
        CoursewareOperationResult.Failure(CoursewareSyncFailure.NETWORK)
    }
}

private fun CoursewareSnapshot.mergeCachedChildren(cached: CoursewareSnapshot): CoursewareSnapshot {
    val cachedById = cached.courses.associateBy(CoursewareCourse::id)
    return copy(
        courses = courses.map { catalogCourse ->
            val cachedCourse = cachedById[catalogCourse.id]
                ?.takeIf {
                    it.courseNumber == catalogCourse.courseNumber &&
                        it.groupId == catalogCourse.groupId &&
                        it.semesterCode == catalogCourse.semesterCode
                }
            if (cachedCourse == null) {
                catalogCourse
            } else {
                catalogCourse.copy(
                    children = cachedCourse.children,
                    childrenLoaded = cachedCourse.childrenLoaded,
                )
            }
        },
    )
}

private fun List<CoursewareNode>.replaceNode(
    stableKey: String,
    transform: (CoursewareNode) -> CoursewareNode,
): List<CoursewareNode>? {
    var found = false
    fun replaceLevel(nodes: List<CoursewareNode>): List<CoursewareNode> = nodes.map { node ->
        when {
            node.stableKey == stableKey -> {
                found = true
                transform(node)
            }
            found -> node
            else -> node.copy(children = replaceLevel(node.children))
        }
    }
    val updated = replaceLevel(this)
    return updated.takeIf { found }
}

private fun List<CoursewareNode>.allNodeKeys(): Set<String> = buildSet {
    fun addLevel(nodes: List<CoursewareNode>) {
        nodes.forEach { node ->
            add(node.stableKey)
            addLevel(node.children)
        }
    }
    addLevel(this@allNodeKeys)
}

private fun List<CoursewareNode>.keysOutside(folderKey: String): Set<String> {
    val folder = findCoursewareNode(this, folderKey) ?: return allNodeKeys()
    return allNodeKeys() - folder.stableKey - folder.children.allNodeKeys()
}

private fun List<CoursewareNode>.hasDuplicateKeys(): Boolean {
    val seen = mutableSetOf<String>()
    fun visit(nodes: List<CoursewareNode>): Boolean = nodes.any { node ->
        !seen.add(node.stableKey) || visit(node.children)
    }
    return visit(this)
}

private fun List<CoursewareNode>.mergePreservingLoadedFolders(
    previous: List<CoursewareNode>,
): List<CoursewareNode> {
    val previousByKey = previous.associateBy(CoursewareNode::stableKey)
    return map { incoming ->
        val old = previousByKey[incoming.stableKey] ?: return@map incoming
        if (!incoming.isFolder || !old.isFolder) return@map incoming
        when {
            incoming.childrenLoaded -> incoming.copy(
                children = incoming.children.mergePreservingLoadedFolders(old.children),
            )
            // 刷新时远端顶层文件夹总是未加载。先把旧子项留在界面上，
            // 但必须标成未加载，让后台预取去拉老师刚更新的内容。
            old.children.isNotEmpty() && incoming.children.isEmpty() -> incoming.copy(
                children = old.children,
                childrenLoaded = false,
            )
            else -> incoming
        }
    }
}

private data class UnloadedCoursewareFolder(
    val courseId: Int,
    val folderKey: String,
    val folderId: Int,
)

private fun CoursewareSnapshot.applyFolderFetches(
    fetched: List<Pair<UnloadedCoursewareFolder, List<CoursewareNode>>>,
): CoursewareSnapshot {
    var courses = this.courses
    for ((target, children) in fetched) {
        val course = courses.firstOrNull { it.id == target.courseId } ?: continue
        val folder = findCoursewareNode(course.children, target.folderKey)
            ?.takeIf { it.kind == CoursewareNodeKind.FOLDER }
            ?: continue
        val existingKeys = course.children.keysOutside(folder.stableKey)
        if (children.any { it.stableKey in existingKeys }) continue
        val updatedChildren = course.children.replaceNode(target.folderKey) {
            it.copy(
                children = children.mergePreservingLoadedFolders(it.children),
                childrenLoaded = true,
            )
        } ?: continue
        courses = courses.map { candidate ->
            if (candidate.id == course.id) candidate.copy(children = updatedChildren) else candidate
        }
    }
    return copy(courses = courses)
}

private fun CoursewareSnapshot.unloadedFolders(): List<UnloadedCoursewareFolder> = buildList {
    courses.filter { it.childrenLoaded }.forEach { course ->
        fun visit(nodes: List<CoursewareNode>) {
            nodes.forEach { node ->
                if (!node.isFolder) return@forEach
                if (!node.childrenLoaded) {
                    add(
                        UnloadedCoursewareFolder(
                            courseId = course.id,
                            folderKey = node.stableKey,
                            folderId = node.id,
                        ),
                    )
                } else {
                    visit(node.children)
                }
            }
        }
        visit(course.children)
    }
}

private fun CoursewareRemoteFailure.toSyncFailure(): CoursewareSyncFailure = when (this) {
    CoursewareRemoteFailure.NETWORK -> CoursewareSyncFailure.NETWORK
    CoursewareRemoteFailure.SESSION_EXPIRED -> CoursewareSyncFailure.SESSION_EXPIRED
    CoursewareRemoteFailure.MALFORMED_RESPONSE -> CoursewareSyncFailure.MALFORMED_RESPONSE
    CoursewareRemoteFailure.SECURE_CHANNEL_UNAVAILABLE -> CoursewareSyncFailure.SECURE_CHANNEL_UNAVAILABLE
}

private const val COURSEWARE_FOLDER_PREFETCH_DEPTH = 6
