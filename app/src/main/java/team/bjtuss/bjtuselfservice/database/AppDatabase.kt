package team.bjtuss.bjtuselfservice.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import team.bjtuss.bjtuselfservice.MainApplication
import team.bjtuss.bjtuselfservice.dao.CourseEntityDao
import team.bjtuss.bjtuselfservice.dao.ExamScheduleEntityDao
import team.bjtuss.bjtuselfservice.dao.GradeEntityDao
import team.bjtuss.bjtuselfservice.dao.HomeworkEntityDao
import team.bjtuss.bjtuselfservice.entity.CourseEntity
import team.bjtuss.bjtuselfservice.entity.ExamScheduleEntity
import team.bjtuss.bjtuselfservice.entity.GradeEntity
import team.bjtuss.bjtuselfservice.entity.HomeworkEntity

@Database(
    entities = [
        GradeEntity::class,
        CourseEntity::class,
        ExamScheduleEntity::class,
        HomeworkEntity::class,
    ],

    version = 2
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gradeEntityDao(): GradeEntityDao
    abstract fun courseEntityDao(): CourseEntityDao
    abstract fun examScheduleEntityDao(): ExamScheduleEntityDao
    abstract fun homeworkEntityDao(): HomeworkEntityDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        //        fun getInstance(context: Context): AppDatabase {
//            return INSTANCE ?: synchronized(this) {
//                val instance = Room.databaseBuilder(
//                    context.applicationContext,
//                    AppDatabase::class.java,
//                    "todolist_database"
//                )
////                    .addMigrations(MIGRATION_4_5)
////                    .fallbackToDestructiveMigration() // 可选：在没有提供迁移策略时使用
//                    .build()
//                INSTANCE = instance
//                instance
//            }
//        }
        fun getInstance(): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    MainApplication.appContext.applicationContext,
                    AppDatabase::class.java,
                    "bjtuselfservice_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration() // 可选：在没有提供迁移策略时使用
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. 获取表名。Room 默认使用实体类的名称（HomeworkEntity）
        // 但通常会转换为下划线命名，这里我们假设它就是 HomeworkEntity
        // 建议查看 Room 自动生成的代码或文档以确认精确的表名。
        val tableName = "HomeworkEntity"

        // 2. 编写 SQL 语句，添加新的非空列 scoreId，并设置默认值为 0。
        val addColumnSql = """
            ALTER TABLE $tableName 
            ADD COLUMN scoreId INTEGER NOT NULL DEFAULT 0
        """.trimIndent()

        db.execSQL(addColumnSql)
    }
}
