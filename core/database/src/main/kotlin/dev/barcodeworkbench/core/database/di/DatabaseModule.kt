package dev.barcodeworkbench.core.database.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.barcodeworkbench.core.database.WorkbenchDatabase
import dev.barcodeworkbench.core.database.dao.EntryDao
import dev.barcodeworkbench.core.database.dao.ConfigEntryDao
import dev.barcodeworkbench.core.database.dao.LibraryDao
import dev.barcodeworkbench.core.database.migration.ALL_MIGRATIONS
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): WorkbenchDatabase =
        Room.databaseBuilder(context, WorkbenchDatabase::class.java, WorkbenchDatabase.NAME)
            .addMigrations(*ALL_MIGRATIONS)
            // Destructive migration is deliberately never enabled: saved codes are
            // user data, and a silent table drop is not an acceptable upgrade path.
            // A missing migration should fail loudly here rather than wipe a library.
            .build()

    @Provides
    fun provideLibraryDao(database: WorkbenchDatabase): LibraryDao = database.libraryDao()

    @Provides
    fun provideEntryDao(database: WorkbenchDatabase): EntryDao = database.entryDao()

    @Provides
    fun provideConfigEntryDao(database: WorkbenchDatabase): ConfigEntryDao =
        database.configEntryDao()
}
