package com.dayflow.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TaskEntity::class,
        TaskRescheduleEntity::class,
        HabitEntity::class,
        HabitScheduleEntity::class,
        HabitLogEntity::class,
        RestDayEntity::class,
        ReflectionEntity::class,
        FocusSessionEntity::class,
        PeriodDayEntity::class,
        CycleSymptomEntity::class,
        PrayerLogEntity::class,
        GoalEntity::class,
        GoalMilestoneEntity::class,
        GoalTaskEntity::class,
        GoalHabitEntity::class,
        SleepLogEntity::class,
    ],
    version = 8,
    exportSchema = true,
)
abstract class DayFlowDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun habitDao(): HabitDao
    abstract fun restDayDao(): RestDayDao
    abstract fun reflectionDao(): ReflectionDao
    abstract fun focusDao(): FocusDao
    abstract fun cycleDao(): CycleDao
    abstract fun prayerDao(): PrayerDao
    abstract fun goalDao(): GoalDao
    abstract fun sleepDao(): SleepDao
    abstract fun backupDao(): BackupDao

    companion object {
        fun build(context: Context): DayFlowDatabase =
            Room.databaseBuilder(context, DayFlowDatabase::class.java, "dayflow.db")
                .addMigrations(*MIGRATIONS)
                .build()

        /** Version 2 adds morning reflections; existing tasks and habits are kept. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `reflections` (`date` INTEGER NOT NULL, `mood` TEXT, `energy` INTEGER, " +
                        "`gratitude` TEXT NOT NULL, `intention` TEXT NOT NULL, `notes` TEXT NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`date`))",
                )
            }
        }

        /** Version 3 adds focus sessions. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `focus_sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`startedAt` INTEGER NOT NULL, `endedAt` INTEGER NOT NULL, `plannedSeconds` INTEGER NOT NULL, " +
                        "`actualSeconds` INTEGER NOT NULL, `completed` INTEGER NOT NULL, `interruptions` INTEGER NOT NULL, " +
                        "`taskId` INTEGER, `habitId` INTEGER, `label` TEXT NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_focus_sessions_startedAt` ON `focus_sessions` (`startedAt`)")
            }
        }

        /** Version 4 lets a reflection carry a photo. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `reflections` ADD COLUMN `photoPath` TEXT")
            }
        }

        /** Version 5 adds cycle tracking. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `period_days` (`date` INTEGER NOT NULL, `flow` TEXT NOT NULL, PRIMARY KEY(`date`))")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `cycle_symptoms` (`date` INTEGER NOT NULL, `symptoms` TEXT NOT NULL, " +
                        "`note` TEXT NOT NULL, PRIMARY KEY(`date`))",
                )
            }
        }

        /** Version 6 adds the five daily prayers tracker; nothing existing is changed. */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `prayer_logs` (`date` INTEGER NOT NULL, `prayer` TEXT NOT NULL, " +
                        "`status` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`date`, `prayer`))",
                )
            }
        }

        /** Version 7 adds goals. Tasks and habits are linked through new tables, so nothing existing is changed. */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `goals` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
                        "`description` TEXT NOT NULL, `targetDate` INTEGER, `status` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `goal_milestones` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`goalId` INTEGER NOT NULL, `title` TEXT NOT NULL, `done` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`goalId`) REFERENCES `goals`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_goal_milestones_goalId` ON `goal_milestones` (`goalId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `goal_tasks` (`taskId` INTEGER NOT NULL, `goalId` INTEGER NOT NULL, PRIMARY KEY(`taskId`), " +
                        "FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                        "FOREIGN KEY(`goalId`) REFERENCES `goals`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_goal_tasks_goalId` ON `goal_tasks` (`goalId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `goal_habits` (`habitId` INTEGER NOT NULL, `goalId` INTEGER NOT NULL, PRIMARY KEY(`habitId`), " +
                        "FOREIGN KEY(`habitId`) REFERENCES `habits`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                        "FOREIGN KEY(`goalId`) REFERENCES `goals`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_goal_habits_goalId` ON `goal_habits` (`goalId`)")
            }
        }

        /** Version 8 adds the sleep tracker; nothing existing is changed. */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sleep_logs` (`date` INTEGER NOT NULL, `bedMinutes` INTEGER NOT NULL, " +
                        "`wakeMinutes` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`date`))",
                )
            }
        }

        /** Every upgrade step, oldest first. The migration tests run all of them. */
        val MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
            MIGRATION_7_8,
        )
    }
}
