package team.bjtuss.bjtuselfservice.dao


import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import team.bjtuss.bjtuselfservice.entity.CourseEntity
import team.bjtuss.bjtuselfservice.entity.CoursewareCourseEntity
import team.bjtuss.bjtuselfservice.entity.CoursewareNodeEntity
import team.bjtuss.bjtuselfservice.entity.CoursewareNodeWithChildren

@Dao
interface CoursewareDao : BaseDao<CoursewareNodeEntity> {
    // 插入课程
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourse(course: CoursewareCourseEntity): Long

    @Query("SELECT * FROM courseware_nodes")
    suspend fun getAllNodes(): List<CoursewareNodeEntity>

    // 批量插入节点
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNodes(nodes: List<CoursewareNodeEntity>): List<Long>

    // 根据课程ID获取所有根节点
    @Query("SELECT * FROM courseware_nodes WHERE courseId = :courseId AND parentId IS NULL")
    suspend fun getRootNodesByCourseId(courseId: Int): List<CoursewareNodeEntity>

    // 根据课程ID获取所有节点（平铺结构）
    @Query("SELECT * FROM courseware_nodes WHERE courseId = :courseId")
    suspend fun getAllNodesByCourseId(courseId: Int): List<CoursewareNodeEntity>

    // 根据节点ID获取节点
    @Query("SELECT * FROM courseware_nodes WHERE id = :nodeId")
    suspend fun getNodeById(nodeId: Int): CoursewareNodeEntity?

    // 根据课程ID获取课程信息
    @Query("SELECT * FROM courseware_courses WHERE id = :courseId")
    suspend fun getCourseById(courseId: Int): CoursewareCourseEntity?

    // 删除课程及其所有节点（级联删除）
    @Delete
    suspend fun deleteCourse(course: CoursewareCourseEntity)

    @Query("DELETE FROM courseware_nodes")
    override suspend fun deleteAll()

    @Query("select * from courseware_nodes")
    override fun getAll(): Flow<List<CoursewareNodeEntity>>

    @Delete
    override suspend fun delete(entity: CoursewareNodeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    override suspend fun insert(entity: CoursewareNodeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<CoursewareNodeEntity>)

    @Update
    override suspend fun update(entity: CoursewareNodeEntity)


}