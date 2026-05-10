package com.workoutparser

import com.workoutparser.model.WorkoutPlan
import java.io.File

class CsvWriter {

    fun write(plan: WorkoutPlan): String {
        val n = plan.workouts.size
        val totalCols = 4 * n

        fun buildRow(values: Map<Int, String>): String =
            Array(totalCols) { i -> values[i] ?: "" }.joinToString(";")

        val emptyRow = buildRow(emptyMap())

        // Row 1: empty
        // Row 2: rest interval in col 1
        // Row 3: empty
        // Row 4: workout titles — col 1+i*4
        // Row 5: headers
        // Rows 6+: exercise rows (padded to max workout length)

        val titleRow = buildRow(plan.workouts.mapIndexed { i, w -> 1 + i * 4 to w.title }.toMap())

        val headerValues = mutableMapOf<Int, String>()
        plan.workouts.forEachIndexed { i, _ ->
            headerValues[1 + i * 4] = if (i == 0)
                "Exercícios (qualquer dúvida, clique aqui)" else "Exercícios"
            headerValues[2 + i * 4] = "SxR"
            headerValues[3 + i * 4] = "Técnica Avançada"
        }
        val headerRow = buildRow(headerValues)

        val maxExercises = plan.workouts.maxOfOrNull { it.exercises.size } ?: 0
        val exerciseRows = (0 until maxExercises).map { rowIndex ->
            val values = mutableMapOf<Int, String>()
            plan.workouts.forEachIndexed { i, w ->
                val exercise = w.exercises.getOrNull(rowIndex)
                if (exercise != null) {
                    values[1 + i * 4] = exercise.name
                    values[2 + i * 4] = exercise.setsReps
                    values[3 + i * 4] = exercise.technique
                }
            }
            buildRow(values)
        }

        val intervalRow = buildRow(mapOf(1 to "Intervalo entre séries e exercícios: ${plan.restInterval}"))

        return (listOf(emptyRow, intervalRow, emptyRow, titleRow, headerRow) + exerciseRows)
            .joinToString("\r\n") + "\r\n"
    }

    fun writeToFile(plan: WorkoutPlan, file: File) {
        file.absoluteFile.parentFile.mkdirs()
        file.writeText(write(plan))
    }
}
