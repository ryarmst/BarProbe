package dev.barcodeworkbench.core.database.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.barcodeworkbench.core.database.RoomCodeRepository
import dev.barcodeworkbench.core.model.CodeRepository
import javax.inject.Singleton

/**
 * The one place the storage implementation is named. Features depend on
 * [CodeRepository] only, so swapping storage is a change to this binding.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindCodeRepository(impl: RoomCodeRepository): CodeRepository
}
