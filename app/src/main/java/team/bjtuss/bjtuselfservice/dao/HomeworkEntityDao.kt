package team.bjtuss.bjtuselfservice.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import team.bjtuss.bjtuselfservice.entity.HomeworkEntity

@Dao
interface HomeworkEntityDao : BaseDao<HomeworkEntity> {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    override suspend fun insert(homeworkEntity: HomeworkEntity)

    @Update
    override suspend fun update(homeworkEntity: HomeworkEntity)

    @Delete
    override suspend fun delete(homeworkEntity: HomeworkEntity)

    @Query("select * from HomeworkEntity")
    override fun getAll(): Flow<List<HomeworkEntity>>

    @Query("delete from GradeEntity")
    override suspend fun deleteAll()
}