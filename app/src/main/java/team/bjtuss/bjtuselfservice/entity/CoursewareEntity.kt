package team.bjtuss.bjtuselfservice.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer

@Entity(tableName = "courseware_courses")
@Serializable
data class CoursewareCourseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val fz_id: String? = "default value",
    val course_num: String? = "default value",
    val xq_code: String? = "default value",
    val name: String = "default value",
)



// 2. 定义节点实体，使用parentId表示树结构
//在子表中添加外键约束，指向父表的主键
@Entity(
    tableName = "courseware_nodes",
    foreignKeys = [
        ForeignKey(
            entity = CoursewareCourseEntity::class,
            parentColumns = ["id"],//父表主键
            childColumns = ["courseId"],//子表外键
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("parentId"), Index("courseId")]//加速查询
)
data class CoursewareNodeEntity(
    @PrimaryKey(autoGenerate = true)
    override var id: Int = 0,
    val courseId: Int,
    val parentId: Int?, // null 表示根节点
    @Embedded
    val res: ResEntity?,
    @Embedded
    val bag: BagEntity?

    // 可能的其他字段
) : BaseEntity()

// 3. 创建关系类用于查询
data class CoursewareNodeWithChildren(
    @Embedded val node: CoursewareNodeEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "parentId"
    )
    val children: List<CoursewareNodeEntity> = emptyList()
)

data class ResEntity(
    val rpId: String,
    val resId: Int,
    val rpName: String,
)

data class BagEntity(
    val badId: Int
)