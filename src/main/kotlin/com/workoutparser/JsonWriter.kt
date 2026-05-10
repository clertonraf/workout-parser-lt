package com.workoutparser

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.workoutparser.model.Exercise
import com.workoutparser.model.Workout
import com.workoutparser.model.WorkoutPlan
import java.io.File

/** Pretty-printer: 4-space indent, expanded arrays, colon without leading space. */
private class FourSpacePrettyPrinter : DefaultPrettyPrinter() {
    init {
        indentObjectsWith(DefaultIndenter("    ", "\n"))
        indentArraysWith(DefaultIndenter("    ", "\n"))
    }
    override fun writeObjectFieldValueSeparator(g: JsonGenerator) = g.writeRaw(": ")
    override fun createInstance(): DefaultPrettyPrinter = FourSpacePrettyPrinter()
}

class JsonWriter {

    private val mapper = jacksonObjectMapper()
        .setDefaultPrettyPrinter(FourSpacePrettyPrinter())
        .enable(SerializationFeature.INDENT_OUTPUT)

    private val intervalRegex = Regex("""(\d+) a (\d+) minutos?""")

    fun write(plan: WorkoutPlan): String = mapper.writeValueAsString(toDto(plan))

    fun writeToFile(plan: WorkoutPlan, file: File) {
        file.absoluteFile.parentFile?.mkdirs()
        mapper.writeValue(file, toDto(plan))
    }

    internal fun parseRestInterval(interval: String): List<Int> {
        val match = intervalRegex.find(interval) ?: return listOf(0, 0)
        val (min, max) = match.destructured
        return listOf(min.toInt() * 60, max.toInt() * 60)
    }

    internal fun parseTitle(title: String): Pair<String, List<String>> {
        val colonIdx = title.indexOf(": ")
        if (colonIdx < 0) return Pair(title, emptyList())
        val name = title.substring(0, colonIdx)
        val parts = title.substring(colonIdx + 2).split(", ")
        return Pair(name, parts)
    }

    internal fun parseSetsReps(sxr: String): Pair<Int, Any> {
        val parts = sxr.split("x", ignoreCase = true)
        if (parts.size < 2) return Pair(0, 0)
        val sets = parts[0].toIntOrNull() ?: 0
        val reps: Any = parts[1].toIntOrNull() ?: parts[1]
        return Pair(sets, reps)
    }

    private fun toDto(plan: WorkoutPlan) = WorkoutPlanJson(
        restInterval = parseRestInterval(plan.restInterval),
        workouts = plan.workouts.map { toWorkoutJson(it) }
    )

    private fun toWorkoutJson(workout: Workout): WorkoutJson {
        val (name, bodyParts) = parseTitle(workout.title)
        return WorkoutJson(
            name = name,
            bodyParts = bodyParts,
            exercises = workout.exercises.map { toExerciseJson(it) }
        )
    }

    private fun toExerciseJson(exercise: Exercise): ExerciseJson {
        val (sets, reps) = parseSetsReps(exercise.setsReps)
        return ExerciseJson(
            exercise = exercise.name,
            advancedTechnique = exercise.technique,
            sets = sets,
            reps = reps
        )
    }
}

private data class WorkoutPlanJson(
    @JsonProperty("rest_interval") val restInterval: List<Int>,
    @JsonProperty("workouts") val workouts: List<WorkoutJson>
)

private data class WorkoutJson(
    @JsonProperty("name") val name: String,
    @JsonProperty("body_parts") val bodyParts: List<String>,
    @JsonProperty("exercises") val exercises: List<ExerciseJson>
)

private data class ExerciseJson(
    @JsonProperty("exercise") val exercise: String,
    @JsonProperty("advanced_technique") val advancedTechnique: String,
    @JsonProperty("sets") val sets: Int,
    @JsonProperty("reps") val reps: Any
)
