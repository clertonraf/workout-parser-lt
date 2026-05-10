package com.workoutparser

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.types.choice
import java.io.File

/**
 * Core logic for the CLI — separated from the Clikt wiring to make it testable.
 */
class WorkoutParserCli {

    private val parser = XlsxParser()
    private val writer = CsvWriter()
    private val jsonWriter = JsonWriter()

    fun run(input: String, output: String?, format: String = "csv") {
        val inputFile = File(input)
        when {
            inputFile.isDirectory -> processDirectory(inputFile, output?.let { File(it) }, format)
            inputFile.isFile -> processSingleFile(inputFile, output?.let { File(it) }, format)
            else -> error("Input path does not exist: $input")
        }
    }

    private fun processSingleFile(file: File, outputFile: File?, format: String) {
        val plan = parser.parse(file)
        val ext = if (format == "json") ".json" else ".csv"
        val target = outputFile ?: File(file.parentFile, file.nameWithoutExtension + ext)
        if (format == "json") jsonWriter.writeToFile(plan, target) else writer.writeToFile(plan, target)
    }

    private fun processDirectory(dir: File, outputDir: File?, format: String) {
        val ext = if (format == "json") ".json" else ".csv"
        val xlsxFiles = dir.walk()
            .maxDepth(1)
            .filter { it.isFile && it.extension.lowercase() == "xlsx" }
            .toList()
        for (file in xlsxFiles) {
            val plan = parser.parse(file)
            val target = if (outputDir != null) {
                File(outputDir, file.nameWithoutExtension + ext)
            } else {
                File(dir, file.nameWithoutExtension + ext)
            }
            if (format == "json") jsonWriter.writeToFile(plan, target) else writer.writeToFile(plan, target)
        }
    }
}

/**
 * Clikt-based CLI entry point.
 */
class WorkoutParserCommand : CliktCommand(name = "workout-parser") {
    private val input: String by option("--input", "-i", help = "Input .xlsx file or directory").required()
    private val output: String? by option("--output", "-o", help = "Output file or directory (optional)")
    private val format: String by option("--format", "-f", help = "Output format")
        .choice("csv", "json")
        .default("csv")

    override fun run() {
        WorkoutParserCli().run(input, output, format)
    }
}

fun main(args: Array<String>) = WorkoutParserCommand().main(args)
