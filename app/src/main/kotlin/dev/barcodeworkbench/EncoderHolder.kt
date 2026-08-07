package dev.barcodeworkbench

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.barcodeworkbench.barcode.engine.BarcodeEncoder
import javax.inject.Inject

/**
 * Supplies the encoder to screens that render symbols from stored data but have no
 * ViewModel of their own needing it.
 *
 * A thin holder rather than a Hilt entry point inside the feature module, which keeps
 * the feature depending on the interface alone.
 */
@HiltViewModel
class EncoderHolder @Inject constructor(
    val encoder: BarcodeEncoder,
) : ViewModel()
