package com.facemorphapp.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [MorphResultEntity::class],
    version = 1,
    exportSchema = false
)
abstract class FaceMorphDatabase : RoomDatabase() {
    abstract fun morphResultDao(): MorphResultDao

    companion object {
        const val DATABASE_NAME = "face_morph.db"
    }
}
