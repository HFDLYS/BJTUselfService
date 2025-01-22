package team.bjtuss.bjtuselfservice.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import team.bjtuss.bjtuselfservice.entity.ExamScheduleEntity

@Dao
interface ExamScheduleEntityDao : BaseDao<ExamScheduleEntity>{
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    override suspend fun insert(examScheduleEntity: ExamScheduleEntity)

    @Update
    override suspend fun update(examScheduleEntity: ExamScheduleEntity)

    @Delete
    override suspend fun delete(examScheduleEntity: ExamScheduleEntity)

    @Query("select * from ExamScheduleEntity")
    override fun getAll(): Flow<List<ExamScheduleEntity>>

    @Query("delete from ExamScheduleEntity")
    override suspend fun deleteAll()

}