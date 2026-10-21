package team.bjtuss.bjtuselfservice.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.LocalDate

/**
 * 教室占用缓存按自然日保存。冷启动先展示当天的上一份数据，
 * 即使距离上次刷新超过一小时也不会清空；跨天后自然失效。
 */
object ClassroomOccupancyCache {
    private const val PREFS = "classroom_occupancy_cache"
    private const val KEY_DATE = "cache_date"
    private const val KEY_MAP = "cache_map"
    private val gson = Gson()

    fun loadToday(context: Context): Map<String, List<Int>> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_DATE, null) != LocalDate.now().toString()) {
            return emptyMap()
        }
        val raw = prefs.getString(KEY_MAP, null) ?: return emptyMap()
        return runCatching {
            gson.fromJson<Map<String, List<Int>>>(
                raw,
                object : TypeToken<Map<String, List<Int>>>() {}.type,
            )
        }.getOrDefault(emptyMap())
    }

    fun saveToday(context: Context, map: Map<String, List<Int>>) {
        if (map.isEmpty()) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DATE, LocalDate.now().toString())
            .putString(KEY_MAP, gson.toJson(map))
            .apply()
    }
}
