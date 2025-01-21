package team.bjtuss.bjtuselfservice.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import team.bjtuss.bjtuselfservice.entity.BaseEntity
import team.bjtuss.bjtuselfservice.entity.CourseEntity

@Dao
interface CourseEntityDao : BaseDao<CourseEntity> {
    @Insert
    override suspend fun insert(courseEntity: CourseEntity)

    @Update
    override suspend fun update(courseEntity: CourseEntity)

    @Delete
    override suspend fun delete(courseEntity: CourseEntity)

    @Query("select * from CourseEntity")
    override fun getAll(): Flow<List<CourseEntity>>

    @Query("delete from CourseEntity")
    override suspend fun deleteAll()

    @Query("select * from CourseEntity where isCurrentSemester = :isCurrentSemester")
    fun getCurrentSemesterCourseBySemester(isCurrentSemester: Boolean): Flow<List<CourseEntity>>

}