package com.facemorphapp.di

import com.facemorphapp.data.repository.FaceMorphRepositoryImpl
import com.facemorphapp.data.repository.SettingsRepositoryImpl
import com.facemorphapp.data.repository.TargetFaceRepositoryImpl
import com.facemorphapp.domain.repository.FaceMorphRepository
import com.facemorphapp.domain.repository.SettingsRepository
import com.facemorphapp.domain.repository.TargetFaceRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindFaceMorphRepository(impl: FaceMorphRepositoryImpl): FaceMorphRepository

    @Binds
    @Singleton
    abstract fun bindTargetFaceRepository(impl: TargetFaceRepositoryImpl): TargetFaceRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
}
