package team.bjtuss.bjtuselfservice.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
    entities = [GradeEntity::class, CourseEntity::class, ExamScheduleEntity::class, HomeworkEntity::class],
    version = 1
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
//                    .addMigrations(MIGRATION_4_5)
                    .fallbackToDestructiveMigration() // 可选：在没有提供迁移策略时使用
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
