package com.workoutparser

import com.workoutparser.model.Exercise
import com.workoutparser.model.Workout
import com.workoutparser.model.WorkoutPlan
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

class CsvWriterTest {

    private val writer = CsvWriter()

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Splits the CSV output by CRLF and drops the trailing empty entry. */
    private fun rows(plan: WorkoutPlan) = writer.write(plan).split("\r\n").dropLast(1)

    // ── Column layout for n workouts ─────────────────────────────────────────
    //
    //  totalCols = 4*n
    //  col 0             : margin (always empty)
    //  col 1+i*4         : workout i — exercise / title / interval
    //  col 2+i*4         : workout i — SxR
    //  col 3+i*4         : workout i — Técnica Avançada
    //  col 4+i*4         : separator (empty), present for i < n-1
    //
    //  Interval row: "Intervalo entre séries e exercícios: VALUE" in col 1

    // ── n=1: 4 fields ─────────────────────────────────────────────────────────

    @Test
    fun `single workout single exercise produces 6-row csv`() {
        val plan = WorkoutPlan(
            restInterval = "1 a 2 minutos",
            workouts = listOf(
                Workout("Treino A: Peitoral", listOf(Exercise("Supino", "4x10", "FST-7")))
            )
        )

        val rows = rows(plan)

        assertEquals(6, rows.size)
        assertEquals(";;;", rows[0])
        assertEquals(";Intervalo entre séries e exercícios: 1 a 2 minutos;;", rows[1])
        assertEquals(";;;", rows[2])
        assertEquals(";Treino A: Peitoral;;", rows[3])
        assertEquals(";Exercícios (qualquer dúvida, clique aqui);SxR;Técnica Avançada", rows[4])
        assertEquals(";Supino;4x10;FST-7", rows[5])
    }

    @Test
    fun `output uses CRLF line endings and ends with trailing newline`() {
        val plan = WorkoutPlan(
            restInterval = "1 min",
            workouts = listOf(Workout("Treino A", listOf(Exercise("Squat", "5x5"))))
        )

        val csv = writer.write(plan)

        assert(csv.contains("\r\n")) { "Expected CRLF line endings" }
        assert(csv.endsWith("\r\n")) { "Expected trailing CRLF" }
    }

    @Test
    fun `single workout exercise with empty technique produces trailing semicolon`() {
        val plan = WorkoutPlan(
            restInterval = "2 a 3 minutos",
            workouts = listOf(
                Workout("Treino A: Dorsal", listOf(Exercise("Remada", "4x5")))
            )
        )

        val rows = rows(plan)

        assertEquals(";Remada;4x5;", rows[5])
    }

    // ── n=2: 8 fields, 7 seps ────────────────────────────────────────────────

    @Test
    fun `two workouts correct column structure`() {
        val plan = WorkoutPlan(
            restInterval = "1 min",
            workouts = listOf(
                Workout("Treino A", listOf(Exercise("E1", "4x10"))),
                Workout("Treino B", listOf(Exercise("E2", "3x12")))
            )
        )

        val rows = rows(plan)

        assertEquals(";;;;;;;", rows[0])
        assertEquals(";Intervalo entre séries e exercícios: 1 min;;;;;;", rows[1])
        assertEquals(";;;;;;;", rows[2])
        assertEquals(";Treino A;;;;Treino B;;", rows[3])
        assertEquals(";Exercícios (qualquer dúvida, clique aqui);SxR;Técnica Avançada;;Exercícios;SxR;Técnica Avançada", rows[4])
        assertEquals(";E1;4x10;;;E2;3x12;", rows[5])
    }

    // ── n=3: 12 fields, 11 seps ──────────────────────────────────────────────

    @Test
    fun `three workouts ragged exercises pads shorter workouts`() {
        val plan = WorkoutPlan(
            restInterval = "1 a 2 minutos",
            workouts = listOf(
                Workout("Treino A: Dorsal", listOf(
                    Exercise("Ex-A1", "4x10"),
                    Exercise("Ex-A2", "4x10")
                )),
                Workout("Treino B: Peitoral", listOf(
                    Exercise("Ex-B1", "4x10"),
                    Exercise("Ex-B2", "4x10"),
                    Exercise("Ex-B3", "4x10")
                )),
                Workout("Treino C: Coxa", listOf(
                    Exercise("Ex-C1", "4x10")
                ))
            )
        )

        val rows = rows(plan)

        assertEquals(8, rows.size)
        assertEquals(";Treino A: Dorsal;;;;Treino B: Peitoral;;;;Treino C: Coxa;;", rows[3])
        assertEquals(
            ";Exercícios (qualquer dúvida, clique aqui);SxR;Técnica Avançada;;Exercícios;SxR;Técnica Avançada;;Exercícios;SxR;Técnica Avançada",
            rows[4]
        )
        assertEquals(";Ex-A1;4x10;;;Ex-B1;4x10;;;Ex-C1;4x10;", rows[5])
        assertEquals(";Ex-A2;4x10;;;Ex-B2;4x10;;;;;", rows[6])
        assertEquals(";;;;;Ex-B3;4x10;;;;;", rows[7])
    }

    // ── n=5: 20 fields, 19 seps ──────────────────────────────────────────────

    @Test
    fun `five workouts produces 20-field rows`() {
        val plan = WorkoutPlan(
            restInterval = "2 a 3 minutos",
            workouts = listOf(
                Workout("Treino A: Dorsal, Abdômen", listOf(Exercise("E1", "4x5"))),
                Workout("Treino B: Peitoral, Tríceps", listOf(Exercise("E2", "4x5"))),
                Workout("Treino C: Bíceps", listOf(Exercise("E3", "4x5"))),
                Workout("Treino D: Coxa", listOf(Exercise("E4", "4x5"))),
                Workout("Treino E: Deltóide", listOf(Exercise("E5", "4x5")))
            )
        )

        val rows = rows(plan)

        assertEquals(6, rows.size)
        assertEquals(";;;;;;;;;;;;;;;;;;;", rows[0])
        assertEquals(";Intervalo entre séries e exercícios: 2 a 3 minutos;;;;;;;;;;;;;;;;;;", rows[1])
        assertEquals(
            ";Treino A: Dorsal, Abdômen;;;;Treino B: Peitoral, Tríceps;;;;Treino C: Bíceps;;;;Treino D: Coxa;;;;Treino E: Deltóide;;",
            rows[3]
        )
        assertEquals(";E1;4x5;;;E2;4x5;;;E3;4x5;;;E4;4x5;;;E5;4x5;", rows[5])
    }

    // ── technique handling ────────────────────────────────────────────────────

    @Test
    fun `exercise with non-empty technique has no trailing semicolon on last workout`() {
        val plan = WorkoutPlan(
            restInterval = "X",
            workouts = listOf(
                Workout("A", listOf(Exercise("Ex", "4x10", "FST-7")))
            )
        )

        val csv = writer.write(plan)
        val exerciseRow = csv.split("\r\n").dropLast(1)[5]

        assertEquals(";Ex;4x10;FST-7", exerciseRow)
    }

    // ── empty plans ───────────────────────────────────────────────────────────

    @Test
    fun `plan with no workouts produces five empty rows`() {
        val plan = WorkoutPlan(restInterval = "1 min", workouts = emptyList())

        val rows = rows(plan)

        assertEquals(5, rows.size)
        // All rows are empty strings (0 fields → 0 separators → "")
        rows.forEach { assertEquals("", it) }
    }

    @Test
    fun `workout with no exercises produces only header rows`() {
        val plan = WorkoutPlan(
            restInterval = "1 min",
            workouts = listOf(Workout("Treino A", emptyList()))
        )

        val rows = rows(plan)

        assertEquals(5, rows.size)
    }

    // ── file I/O ─────────────────────────────────────────────────────────────

    @Test
    fun `writeToFile writes same content as write`() {
        val plan = WorkoutPlan(
            restInterval = "1 a 2 minutos",
            workouts = listOf(
                Workout("Treino A", listOf(Exercise("Supino", "4x10")))
            )
        )
        val tmpFile = File.createTempFile("workout_test", ".csv")
        tmpFile.deleteOnExit()

        writer.writeToFile(plan, tmpFile)

        assertEquals(writer.write(plan), tmpFile.readText())
    }
}
