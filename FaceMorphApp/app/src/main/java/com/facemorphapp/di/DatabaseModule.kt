package com.facemorphapp.di

import android.content.Context
import androidx.room.Room
import com.facemorphapp.data.local.db.FaceMorphDatabase
import com.facemorphapp.data.local.db.MorphResultDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FaceMorphDatabase =
        Room.databaseBuilder(context, FaceMorphDatabase::class.java, FaceMorphDatabase.DATABASE_NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideMorphResultDao(db: FaceMorphDatabase): MorphResultDao = db.morphResultDao()
}
