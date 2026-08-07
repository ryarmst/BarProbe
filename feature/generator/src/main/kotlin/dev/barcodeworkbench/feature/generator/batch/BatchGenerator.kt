package dev.barcodeworkbench.feature.generator.batch

import dev.barcodeworkbench.barcode.engine.BarcodeEncoder
import dev.barcodeworkbench.barcode.engine.EncodeOptions
import dev.barcodeworkbench.barcode.engine.EncodeRequest
import dev.barcodeworkbench.barcode.engine.EncodeResult
import dev.barcodeworkbench.barcode.render.ExportFormat
import dev.barcodeworkbench.barcode.render.RenderSpec
import dev.barcodeworkbench.barcode.render.SheetItem
import dev.barcodeworkbench.barcode.render.SheetLayout
import dev.barcodeworkbench.barcode.render.SymbolExporter
import dev.barcodeworkbench.barcode.render.PdfSymbolRenderer
import dev.barcodeworkbench.core.model.EscapeCodec
import dev.barcodeworkbench.core.model.InputMode
import dev.barcodeworkbench.core.model.ModuleMatrix
import dev.barcodeworkbench.core.model.Payload
import dev.barcodeworkbench.core.model.PayloadValidator
import dev.barcodeworkbench.core.model.SymbologyId
import dev.barcodeworkbench.core.model.SymbologyRegistry
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Outcome for one wordlist row. */
sealed interface BatchItemResult {
    val entry: WordlistEntry

    data class Encoded(
        override val entry: WordlistEntry,
        val symbologyId: SymbologyId,
        val matrix: ModuleMatrix,
    ) : BatchItemResult

    data class Rejected(
        override val entry: WordlistEntry,
        val reason: String,
    ) : BatchItemResult
}

data class BatchPreview(
    val results: List<BatchItemResult>,
    val skippedLines: List<Pair<Int, String>>,
) {
    val encoded: List<BatchItemResult.Encoded> get() = results.filterIsInstance<BatchItemResult.Encoded>()
    val rejected: List<BatchItemResult.Rejected> get() = results.filterIsInstance<BatchItemResult.Rejected>()
    val hasAnything: Boolean get() = encoded.isNotEmpty()
}

/**
 * Turns a wordlist into symbols and packages them.
 *
 * Encoding happens once up front so the user sees exactly which rows will fail and
 * why *before* anything is written. Discovering on row 900 of 1000 that a payload
 * was invalid, after a file has already been created, is the failure mode this
 * ordering avoids.
 */
@Singleton
class BatchGenerator @Inject constructor(
    private val encoder: BarcodeEncoder,
    private val exporter: SymbolExporter,
    private val pdfRenderer: PdfSymbolRenderer,
) {

    /**
     * Encodes every row, reporting progress.
     *
     * Cancellation is honoured between rows, so a large batch can be abandoned
     * without waiting for it to finish.
     */
    suspend fun preview(
        parsed: WordlistParseResult,
        defaultSymbology: SymbologyId,
        inputMode: InputMode = InputMode.UNICODE,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): BatchPreview {
        val results = mutableListOf<BatchItemResult>()
        parsed.entries.forEachIndexed { index, entry ->
            currentCoroutineContext().ensureActive()
            results += encodeOne(entry, entry.symbologyId ?: defaultSymbology, inputMode)
            onProgress(index + 1, parsed.entries.size)
        }
        return BatchPreview(results, parsed.skippedLines)
    }

    private fun encodeOne(
        entry: WordlistEntry,
        symbologyId: SymbologyId,
        inputMode: InputMode,
    ): BatchItemResult {
        val spec = SymbologyRegistry.find(symbologyId)
            ?: return BatchItemResult.Rejected(entry, "Unknown symbology $symbologyId")

        val effectiveMode = if (spec.supportsGs1 && entry.payload.startsWith("[")) {
            InputMode.GS1
        } else {
            inputMode
        }

        val validation = PayloadValidator.validate(spec, entry.payload, effectiveMode)
        if (!validation.isValid) {
            return BatchItemResult.Rejected(
                entry,
                validation.firstMessage ?: "Invalid payload",
            )
        }

        val result = encoder.encode(
            EncodeRequest(
                symbology = symbologyId,
                payload = Payload(
                    bytes = entry.payload.toByteArray(Charsets.UTF_8),
                    mode = effectiveMode,
                    escapesEnabled = EscapeCodec.containsEscapes(entry.payload),
                ),
                options = EncodeOptions(dotty = symbologyId == SymbologyId.DOTCODE),
            ),
        )
        return when (result) {
            is EncodeResult.Success ->
                BatchItemResult.Encoded(entry, symbologyId, result.matrix)
            is EncodeResult.Failure ->
                BatchItemResult.Rejected(entry, result.message)
        }
    }

    /**
     * Writes each symbol as its own file inside a ZIP.
     *
     * Filenames are prefixed with the source line number, zero-padded, so the
     * archive sorts in wordlist order rather than lexicographically by payload.
     */
    suspend fun writeZip(
        encoded: List<BatchItemResult.Encoded>,
        format: ExportFormat,
        spec: RenderSpec,
        out: OutputStream,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ) {
        val width = encoded.size.toString().length
        ZipOutputStream(out).use { zip ->
            val usedNames = mutableSetOf<String>()
            encoded.forEachIndexed { index, item ->
                currentCoroutineContext().ensureActive()

                val prefix = item.entry.lineNumber.toString().padStart(width, '0')
                val base = exporter.suggestFileName(
                    symbologyName = SymbologyRegistry[item.symbologyId].displayName,
                    payloadHint = item.entry.label ?: item.entry.payload,
                    format = format,
                )
                var name = "$prefix-$base"
                // Distinct payloads can normalise to the same safe filename, which
                // would otherwise silently drop entries from the archive.
                var suffix = 2
                while (!usedNames.add(name)) {
                    name = "$prefix-${suffix}-$base"
                    suffix++
                }

                zip.putNextEntry(ZipEntry(name))
                val bytes = ByteArrayOutputStream().also {
                    exporter.write(item.matrix, spec, format, it)
                }.toByteArray()
                zip.write(bytes)
                zip.closeEntry()
                onProgress(index + 1, encoded.size)
            }
        }
    }

    /** Writes a printable contact sheet with a caption under each symbol. */
    suspend fun writePdfSheet(
        encoded: List<BatchItemResult.Encoded>,
        spec: RenderSpec,
        layout: SheetLayout,
        out: OutputStream,
    ) {
        currentCoroutineContext().ensureActive()
        val items = encoded.map { item ->
            SheetItem(
                matrix = item.matrix,
                caption = item.entry.label ?: item.entry.payload,
            )
        }
        pdfRenderer.renderSheet(items, spec, layout, out)
    }
}
