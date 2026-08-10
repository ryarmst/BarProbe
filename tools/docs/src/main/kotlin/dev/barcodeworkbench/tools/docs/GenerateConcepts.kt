package dev.barcodeworkbench.tools.docs

import java.io.File

/**
 * Parses the Learn Markdown articles into the app's typed `Concepts.kt`.
 *
 * Usage: GenerateConcepts <docsLearnDir> <outputKotlinFile>
 *
 * Invoked by :feature:learn before compilation, so a malformed article fails the app
 * build rather than shipping. The generated file is never committed.
 */
fun main(args: Array<String>) {
    require(args.size == 2) { "usage: GenerateConcepts <docsLearnDir> <outputKotlinFile>" }
    val docsDir = File(args[0])
    val outputFile = File(args[1])

    val mdFiles = docsDir.listFiles { f -> f.isFile && f.extension == "md" }
        ?.sortedBy { it.name }
        ?: error("no Markdown files under ${docsDir.absolutePath}")
    require(mdFiles.isNotEmpty()) { "no .md articles found in ${docsDir.absolutePath}" }

    val articles = mdFiles.map { MarkdownParser.parse(it.name, it.readText()) }

    val ids = articles.map { it.id }
    require(ids.toSet().size == ids.size) { "duplicate article ids: $ids" }

    outputFile.parentFile.mkdirs()
    outputFile.writeText(ConceptsEmitter.emit(articles))
    System.err.println("generated ${outputFile.name} from ${articles.size} articles")
}
