package dev.barcodeworkbench.barcode.reader.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.barcodeworkbench.barcode.engine.BarcodeDecoder
import dev.barcodeworkbench.barcode.engine.CameraFrameDecoder
import dev.barcodeworkbench.barcode.reader.ZxingCppDecoder
import dev.barcodeworkbench.barcode.reader.ZxingCppFrameDecoder
import javax.inject.Singleton

/**
 * The single place the concrete decode engine is named. Swapping engines is a
 * change to this binding and nothing else.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ReaderModule {

    @Binds
    @Singleton
    abstract fun bindBarcodeDecoder(impl: ZxingCppDecoder): BarcodeDecoder

    @Binds
    @Singleton
    abstract fun bindCameraFrameDecoder(impl: ZxingCppFrameDecoder): CameraFrameDecoder
}
