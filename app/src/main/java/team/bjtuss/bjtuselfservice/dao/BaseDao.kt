package team.bjtuss.bjtuselfservice.dao

import kotlinx.coroutines.flow.Flow
import team.bjtuss.bjtuselfservice.entity.BaseEntity

interface BaseDao<T : BaseEntity> {
    suspend fun insert(entity: T)

    suspend fun delete(entity: T)

    suspend fun update(entity: T)

    fun getAll(): Flow<List<T>>

    suspend fun deleteAll()
}
