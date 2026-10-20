package team.bjtuss.bjtuselfservice.shared.cache

/**
 * 各数据源最近一次“远端成功写入缓存”的时间（Unix 秒），按账号存在 cache_metadata。
 * 只在真正同步成功时写；读取方（AI Agent 命令行）据此提示缓存是否过旧。
 */
enum class SyncSource(val key: String) {
    COURSES("courses"),
    HOMEWORK("homework"),
    EXAMS("exams"),
    GRADES("grades"),
}

private fun syncKey(source: SyncSource) = "synced_at_v1.${source.key}"

fun CacheStore.markSynced(accountScope: String, source: SyncSource, epochSeconds: Long) {
    putMetadata(accountScope, syncKey(source), epochSeconds.toString())
}

fun CacheStore.syncedAt(accountScope: String, source: SyncSource): Long? =
    metadata(accountScope, syncKey(source))?.toLongOrNull()
