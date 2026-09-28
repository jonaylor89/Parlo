package com.parlo.app.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SessionEntity::class, TurnEntity::class, VocabEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class ParloDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun vocabDao(): VocabDao

    companion object {
        fun build(context: Context): ParloDatabase =
            Room.databaseBuilder(context, ParloDatabase::class.java, "parlo.db")
                .build()
    }
}
