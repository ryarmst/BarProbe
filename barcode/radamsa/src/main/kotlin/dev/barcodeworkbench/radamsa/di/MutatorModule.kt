package dev.barcodeworkbench.radamsa.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.barcodeworkbench.barcode.engine.Mutator
import dev.barcodeworkbench.radamsa.RadamsaMutator
import javax.inject.Singleton

/**
 * The single place the concrete mutation engine is named. Feature modules depend
 * on [Mutator] only, so replacing radamsa would be a change to this binding alone.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class MutatorModule {

    @Binds
    @Singleton
    abstract fun bindMutator(impl: RadamsaMutator): Mutator
}
