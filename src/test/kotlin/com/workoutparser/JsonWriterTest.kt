package com.workoutparser

import com.fasterxml.jackson.databind.ObjectMapper
import com.workoutparser.model.Exercise
import com.workoutparser.model.Workout
import com.workoutparser.model.WorkoutPlan
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class JsonWriterTest {

    private val writer = JsonWriter()
    private val mapper = ObjectMapper()

    // ── parseRestInterval ──────────────────────────────────────────────────

    @Test
    fun `parseRestInterval converts minutes to seconds`() {
        assertEquals(listOf(60, 120), writer.parseRestInterval("1 a 2 minutos"))
        assertEquals(listOf(120, 180), writer.parseRestInterval("2 a 3 minutos"))
    }

    @Test
    fun `parseRestInterval returns zeros when format unrecognized`() {
        assertEquals(listOf(0, 0), writer.parseRestInterval(""))
        assertEquals(listOf(0, 0), writer.parseRestInterval("unknown"))
    }

    // ── parseTitle ─────────────────────────────────────────────────────────

    @Test
    fun `parseTitle splits name and multiple body parts`() {
        val (name, parts) = writer.parseTitle("Treino A: Dorsal, Abdômen")
        assertEquals("Treino A", name)
        assertEquals(listOf("Dorsal", "Abdômen"), parts)
    }

    @Test
    fun `parseTitle splits name and single body part`() {
        val (name, parts) = writer.parseTitle("Treino B: Peitoral")
        assertEquals("Treino B", name)
        assertEquals(listOf("Peitoral"), parts)
    }

    @Test
    fun `parseTitle returns empty body parts when no colon`() {
        val (name, parts) = writer.parseTitle("Treino A")
        assertEquals("Treino A", name)
        assertEquals(emptyList<String>(), parts)
    }

    // ── parseSetsReps ──────────────────────────────────────────────────────

    @Test
    fun `parseSetsReps returns integers for numeric reps`() {
        val (sets, reps) = writer.parseSetsReps("4x10")
        assertEquals(4, sets)
        assertEquals(10, reps)
    }

    @Test
    fun `parseSetsReps returns string for non-numeric reps`() {
        val (sets, reps) = writer.parseSetsReps("4xF")
        assertEquals(4, sets)
        assertEquals("F", reps)
    }

    @Test
    fun `parseSetsReps returns zeros when format is missing separator`() {
        val (sets, reps) = writer.parseSetsReps("invalid")
        assertEquals(0, sets)
        assertEquals(0, reps)
    }

    // ── write ──────────────────────────────────────────────────────────────

    @Test
    fun `write produces valid JSON with correct structure`() {
        val plan = WorkoutPlan(
            restInterval = "1 a 2 minutos",
            workouts = listOf(
                Workout(
                    title = "Treino A: Dorsal, Abdômen",
                    exercises = listOf(
                        Exercise("Pull-up", "4x10", ""),
                        Exercise("Prancha", "3xF", "FST-7")
                    )
                )
            )
        )

        val json = writer.write(plan)
        val node = mapper.readTree(json)

        // rest_interval
        assertEquals(60,  node["rest_interval"][0].intValue())
        assertEquals(120, node["rest_interval"][1].intValue())

        // workout name / body_parts
        val wo = node["workouts"][0]
        assertEquals("Treino A", wo["name"].textValue())
        assertEquals("Dorsal",  wo["body_parts"][0].textValue())
        assertEquals("Abdômen", wo["body_parts"][1].textValue())

        // first exercise — integer reps
        val ex0 = wo["exercises"][0]
        assertEquals("Pull-up", ex0["exercise"].textValue())
        assertEquals("", ex0["advanced_technique"].textValue())
        assertEquals(4,  ex0["sets"].intValue())
        assertTrue(ex0["reps"].isInt, "reps should be a JSON number")
        assertEquals(10, ex0["reps"].intValue())

        // second exercise — string reps + technique
        val ex1 = wo["exercises"][1]
        assertEquals("Prancha", ex1["exercise"].textValue())
        assertEquals("FST-7", ex1["advanced_technique"].textValue())
        assertEquals(3, ex1["sets"].intValue())
        assertTrue(ex1["reps"].isTextual, "reps should be a JSON string")
        assertEquals("F", ex1["reps"].textValue())
    }

    @Test
    fun `write handles multiple workouts`() {
        val plan = WorkoutPlan(
            restInterval = "2 a 3 minutos",
            workouts = listOf(
                Workout("Treino A: Peitoral", listOf(Exercise("Supino", "4x8", ""))),
                Workout("Treino B: Dorsal", listOf(Exercise("Remada", "4x10", "GVT")))
            )
        )

        val json = writer.write(plan)
        val node = mapper.readTree(json)

        assertEquals(120, node["rest_interval"][0].intValue())
        assertEquals(180, node["rest_interval"][1].intValue())
        assertEquals(2, node["workouts"].size())
        assertEquals("Treino A", node["workouts"][0]["name"].textValue())
        assertEquals("Treino B", node["workouts"][1]["name"].textValue())
    }

    @Test
    fun `write handles workout with no body parts in title`() {
        val plan = WorkoutPlan(
            restInterval = "1 a 2 minutos",
            workouts = listOf(Workout("Treino A", listOf(Exercise("Agachamento", "3x12", ""))))
        )

        val json = writer.write(plan)
        val node = mapper.readTree(json)

        assertEquals("Treino A", node["workouts"][0]["name"].textValue())
        assertEquals(0, node["workouts"][0]["body_parts"].size())
    }

    @Test
    fun `write handles special characters in exercise names`() {
        val plan = WorkoutPlan(
            restInterval = "1 a 2 minutos",
            workouts = listOf(
                Workout(
                    "Treino A: Peitoral",
                    listOf(Exercise("""Peck Deck \ Crucifixo Na Máquina""", "4x10", "Rest 'n' Pause 3x"))
                )
            )
        )

        val json = writer.write(plan)
        val node = mapper.readTree(json)
        val ex = node["workouts"][0]["exercises"][0]
        assertTrue(ex["exercise"].textValue().contains("\\"))
        assertEquals("Rest 'n' Pause 3x", ex["advanced_technique"].textValue())
    }

    @Test
    fun `write handles unrecognized rest interval`() {
        val plan = WorkoutPlan(
            restInterval = "",
            workouts = listOf(Workout("Treino A: Core", listOf(Exercise("Prancha", "4xF", ""))))
        )

        val json = writer.write(plan)
        val node = mapper.readTree(json)
        assertEquals(0, node["rest_interval"][0].intValue())
        assertEquals(0, node["rest_interval"][1].intValue())
    }

    // ── writeToFile ────────────────────────────────────────────────────────

    @Test
    fun `writeToFile creates file with json content`(@TempDir tmpDir: Path) {
        val plan = WorkoutPlan(
            restInterval = "1 a 2 minutos",
            workouts = listOf(Workout("Treino A: Peitoral", listOf(Exercise("Supino", "4x10", ""))))
        )
        val output = File(tmpDir.toFile(), "plan.json")

        writer.writeToFile(plan, output)

        assertTrue(output.exists())
        val node = mapper.readTree(output)
        assertEquals("Treino A", node["workouts"][0]["name"].textValue())
    }

    @Test
    fun `writeToFile creates parent directories if needed`(@TempDir tmpDir: Path) {
        val plan = WorkoutPlan(
            restInterval = "1 a 2 minutos",
            workouts = listOf(Workout("Treino A: Core", listOf(Exercise("Prancha", "3xF", ""))))
        )
        val output = File(tmpDir.toFile(), "nested/dir/plan.json")

        writer.writeToFile(plan, output)

        assertTrue(output.exists())
    }
}
