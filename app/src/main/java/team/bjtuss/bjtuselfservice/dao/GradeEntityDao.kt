package team.bjtuss.bjtuselfservice.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import team.bjtuss.bjtuselfservice.entity.GradeEntity

@Dao
interface GradeEntityDao : BaseDao<GradeEntity> {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    override suspend fun insert(gradeEntity: GradeEntity)

    @Update
    override suspend fun update(gradeEntity: GradeEntity)

    @Delete
    override suspend fun delete(gradeEntity: GradeEntity)

    @Query("select * from GradeEntity")
    override fun getAll(): Flow<List<GradeEntity>>

    @Query("delete from GradeEntity")
    override suspend fun deleteAll()
}








