package dev.barcodeworkbench.zint.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.barcodeworkbench.barcode.engine.BarcodeEncoder
import dev.barcodeworkbench.zint.ZintEncoder
import javax.inject.Singleton

/**
 * The single place the concrete encode engine is named.
 *
 * Feature modules depend on [BarcodeEncoder] only, so replacing libzint would be
 * a change to this binding and nothing else.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class EncoderModule {

    @Binds
    @Singleton
    abstract fun bindBarcodeEncoder(impl: ZintEncoder): BarcodeEncoder
}
