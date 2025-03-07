package team.bjtuss.bjtuselfservice.utils

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

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
}