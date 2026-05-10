package com.workoutparser

import com.workoutparser.model.Exercise
import com.workoutparser.model.Workout
import com.workoutparser.model.WorkoutPlan
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayOutputStream
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Tests for XlsxParser. Synthetic xlsx files are built with Apache POI
 * to avoid coupling to out-of-sync reference files.
 *
 * XLSX block layout (0-indexed columns):
 *   col 0  = margin
 *   col 1  = workout1 exercise/title
 *   col 2  = workout1 SxR
 *   col 3  = workout1 Técnica Avançada
 *   col 4  = empty separator
 *   col 5  = workout2 exercise/title
 *   col 6  = workout2 SxR
 *   col 7  = workout2 Técnica Avançada
 *   col 8  = empty separator
 *   col 9  = workout3 exercise/title
 *   col 10 = workout3 SxR
 *   col 11 = workout3 Técnica Avançada
 *
 * Row pattern (0-indexed):
 *   row 0: plan title (col 1)
 *   row 1: workout titles (cols 1, 5, 9) — block start
 *   row 2: header row
 *   rows 3..N-1: exercise rows
 *   row N: "Intervalo entre séries..." row (col 1) — block end
 *   row N+1: next block starts (another title row) or sheet ends
 */
class XlsxParserTest {

    private val parser = XlsxParser()

    // ── helper: builds an xlsx in memory ─────────────────────────────────────

    private fun buildXlsx(block: XSSFWorkbook.() -> Unit): ByteArray {
        val wb = XSSFWorkbook()
        wb.block()
        val out = ByteArrayOutputStream()
        wb.write(out)
        wb.close()
        return out.toByteArray()
    }

    private fun XSSFWorkbook.newSheet(): org.apache.poi.xssf.usermodel.XSSFSheet =
        createSheet("Treino")

    private fun org.apache.poi.xssf.usermodel.XSSFSheet.setCell(
        rowIdx: Int, colIdx: Int, value: String
    ) {
        val row = getRow(rowIdx) ?: createRow(rowIdx)
        row.createCell(colIdx, CellType.STRING).setCellValue(value)
    }

    /**
     * Creates a minimal one-block xlsx with three workouts (A/B/C) and one
     * exercise each.
     *
     *  Row 0: plan title
     *  Row 1: workout titles A/B/C in cols 1,5,9
     *  Row 2: headers
     *  Row 3: exercise
     *  Row 4: interval row
     */
    private fun minimalThreeWorkoutXlsx(
        restInterval: String = "1 a 2 minutos",
        exercises: List<Triple<String, String, String>> = listOf(
            Triple("Supino", "4x10", "FST-7"),
            Triple("Pulldown", "4x10", ""),
            Triple("Leg Press", "5x12", "GVT")
        )
    ): ByteArray = buildXlsx {
        val sheet = newSheet()
        sheet.setCell(0, 1, "Plano de Treino 2024")
        sheet.setCell(1, 1, "Treino A: Peitoral")
        sheet.setCell(1, 5, "Treino B: Dorsal")
        sheet.setCell(1, 9, "Treino C: Coxa")
        sheet.setCell(2, 1, "Exercícios (qualquer dúvida, clique aqui)")
        sheet.setCell(2, 2, "SxR")
        sheet.setCell(2, 3, "Técnica Avançada")
        sheet.setCell(2, 5, "Exercícios")
        sheet.setCell(2, 6, "SxR")
        sheet.setCell(2, 7, "Técnica Avançada")
        sheet.setCell(2, 9, "Exercícios")
        sheet.setCell(2, 10, "SxR")
        sheet.setCell(2, 11, "Técnica Avançada")
        exercises.forEachIndexed { i, (name, sxr, tech) ->
            val col = 1 + i * 4
            sheet.setCell(3, col, name)
            sheet.setCell(3, col + 1, sxr)
            if (tech.isNotEmpty()) sheet.setCell(3, col + 2, tech)
        }
        // Interval value embedded in the cell text (matching real xlsx format)
        sheet.setCell(4, 1, "Intervalo entre séries e exercícios: $restInterval")
    }

    // ── basic parsing ─────────────────────────────────────────────────────────

    @Test
    fun `parses plan title as rest interval from interval row`() {
        val bytes = minimalThreeWorkoutXlsx(restInterval = "1 a 2 minutos")
        val plan = parser.parse(bytes)

        assertEquals("1 a 2 minutos", plan.restInterval)
    }

    @Test
    fun `parses three workout titles`() {
        val bytes = minimalThreeWorkoutXlsx()
        val plan = parser.parse(bytes)

        assertEquals(3, plan.workouts.size)
        assertEquals("Treino A: Peitoral", plan.workouts[0].title)
        assertEquals("Treino B: Dorsal", plan.workouts[1].title)
        assertEquals("Treino C: Coxa", plan.workouts[2].title)
    }

    @Test
    fun `parses exercises with techniques`() {
        val bytes = minimalThreeWorkoutXlsx(
            exercises = listOf(
                Triple("Supino", "4x10", "FST-7"),
                Triple("Pulldown", "3x12", ""),
                Triple("Leg Press", "5x12", "GVT")
            )
        )
        val plan = parser.parse(bytes)

        val exA = plan.workouts[0].exercises[0]
        assertEquals("Supino", exA.name)
        assertEquals("4x10", exA.setsReps)
        assertEquals("FST-7", exA.technique)

        val exB = plan.workouts[1].exercises[0]
        assertEquals("Pulldown", exB.name)
        assertEquals("3x12", exB.setsReps)
        assertEquals("", exB.technique)

        val exC = plan.workouts[2].exercises[0]
        assertEquals("Leg Press", exC.name)
        assertEquals("GVT", exC.technique)
    }

    @Test
    fun `parses multiple exercises per workout`() {
        val bytes = buildXlsx {
            val sheet = newSheet()
            sheet.setCell(0, 1, "Plan")
            sheet.setCell(1, 1, "Treino A: Peitoral")
            sheet.setCell(1, 5, "Treino B: Dorsal")
            sheet.setCell(1, 9, "Treino C: Coxa")
            sheet.setCell(2, 1, "Exercícios")
            sheet.setCell(2, 2, "SxR")
            sheet.setCell(2, 3, "Técnica Avançada")
            sheet.setCell(2, 5, "Exercícios")
            sheet.setCell(2, 6, "SxR")
            sheet.setCell(2, 7, "Técnica Avançada")
            sheet.setCell(2, 9, "Exercícios")
            sheet.setCell(2, 10, "SxR")
            sheet.setCell(2, 11, "Técnica Avançada")
            // Workout A: 3 exercises in rows 3,4,5
            sheet.setCell(3, 1, "Supino"); sheet.setCell(3, 2, "4x10")
            sheet.setCell(4, 1, "Crucifixo"); sheet.setCell(4, 2, "3x12")
            sheet.setCell(5, 1, "Paralela"); sheet.setCell(5, 2, "4x15")
            // Workout B: 2 exercises
            sheet.setCell(3, 5, "Pulldown"); sheet.setCell(3, 6, "4x10")
            sheet.setCell(4, 5, "Remada"); sheet.setCell(4, 6, "3x12")
            // Workout C: 1 exercise
            sheet.setCell(3, 9, "Leg Press"); sheet.setCell(3, 10, "5x12")
            // interval
            sheet.setCell(6, 1, "Intervalo entre séries e exercícios: 2 a 3 minutos")
        }

        val plan = parser.parse(bytes)

        assertEquals(3, plan.workouts[0].exercises.size)
        assertEquals(2, plan.workouts[1].exercises.size)
        assertEquals(1, plan.workouts[2].exercises.size)
        assertEquals("Paralela", plan.workouts[0].exercises[2].name)
        assertEquals("Remada", plan.workouts[1].exercises[1].name)
    }

    // ── two blocks (5 workouts) ───────────────────────────────────────────────

    @Test
    fun `parses two blocks merges into five workouts`() {
        val bytes = buildXlsx {
            val sheet = newSheet()
            // Block 1: rows 0-4
            sheet.setCell(0, 1, "Plan Title")
            sheet.setCell(1, 1, "Treino A: Peito")
            sheet.setCell(1, 5, "Treino B: Dorsal")
            sheet.setCell(1, 9, "Treino C: Coxa")
            sheet.setCell(2, 1, "Exercícios"); sheet.setCell(2, 2, "SxR"); sheet.setCell(2, 3, "Técnica Avançada")
            sheet.setCell(2, 5, "Exercícios"); sheet.setCell(2, 6, "SxR"); sheet.setCell(2, 7, "Técnica Avançada")
            sheet.setCell(2, 9, "Exercícios"); sheet.setCell(2, 10, "SxR"); sheet.setCell(2, 11, "Técnica Avançada")
            sheet.setCell(3, 1, "ExA1"); sheet.setCell(3, 2, "4x10")
            sheet.setCell(3, 5, "ExB1"); sheet.setCell(3, 6, "4x10")
            sheet.setCell(3, 9, "ExC1"); sheet.setCell(3, 10, "4x10")
            sheet.setCell(4, 1, "Intervalo entre séries e exercícios: 1 a 2 minutos")
            // Block 2: rows 5-9
            sheet.setCell(5, 1, "Treino D: Bíceps")
            sheet.setCell(5, 5, "Treino E: Tríceps")
            // Only 2 workouts in second block (no third title)
            sheet.setCell(6, 1, "Exercícios"); sheet.setCell(6, 2, "SxR"); sheet.setCell(6, 3, "Técnica Avançada")
            sheet.setCell(6, 5, "Exercícios"); sheet.setCell(6, 6, "SxR"); sheet.setCell(6, 7, "Técnica Avançada")
            sheet.setCell(7, 1, "ExD1"); sheet.setCell(7, 2, "3x12")
            sheet.setCell(7, 5, "ExE1"); sheet.setCell(7, 6, "3x12")
            sheet.setCell(8, 1, "Intervalo entre séries e exercícios: 1 a 2 minutos")
        }

        val plan = parser.parse(bytes)

        assertEquals(5, plan.workouts.size)
        assertEquals("Treino A: Peito", plan.workouts[0].title)
        assertEquals("Treino D: Bíceps", plan.workouts[3].title)
        assertEquals("Treino E: Tríceps", plan.workouts[4].title)
        assertEquals("ExD1", plan.workouts[3].exercises[0].name)
    }

    // ── file parsing ─────────────────────────────────────────────────────────

    @Test
    fun `parse from File delegates to byte array parser`() {
        val bytes = minimalThreeWorkoutXlsx()
        val tmpFile = File.createTempFile("test_workout", ".xlsx")
        tmpFile.deleteOnExit()
        tmpFile.writeBytes(bytes)

        val plan = parser.parse(tmpFile)

        assertEquals(3, plan.workouts.size)
        assertEquals("Treino A: Peitoral", plan.workouts[0].title)
    }

    // ── edge cases ────────────────────────────────────────────────────────────

    @Test
    fun `workout with title only in col 1 (single workout block)`() {
        val bytes = buildXlsx {
            val sheet = newSheet()
            sheet.setCell(0, 1, "Plan")
            sheet.setCell(1, 1, "Treino A: Cardio")
            // no cols 5 or 9 filled
            sheet.setCell(2, 1, "Exercícios"); sheet.setCell(2, 2, "SxR"); sheet.setCell(2, 3, "Técnica Avançada")
            sheet.setCell(3, 1, "Corrida"); sheet.setCell(3, 2, "30min")
            sheet.setCell(4, 1, "Intervalo entre séries e exercícios: 30s")
        }

        val plan = parser.parse(bytes)

        assertEquals(1, plan.workouts.size)
        assertEquals("Treino A: Cardio", plan.workouts[0].title)
        assertEquals(1, plan.workouts[0].exercises.size)
        assertEquals("Corrida", plan.workouts[0].exercises[0].name)
    }

    @Test
    fun `cells with only whitespace are treated as empty technique`() {
        val bytes = buildXlsx {
            val sheet = newSheet()
            sheet.setCell(0, 1, "Plan")
            sheet.setCell(1, 1, "Treino A: X")
            sheet.setCell(2, 1, "Exercícios"); sheet.setCell(2, 2, "SxR"); sheet.setCell(2, 3, "Técnica Avançada")
            sheet.setCell(3, 1, "Squat"); sheet.setCell(3, 2, "4x10"); sheet.setCell(3, 3, "  ")
            sheet.setCell(4, 1, "Intervalo entre séries e exercícios: 1 min")
        }

        val plan = parser.parse(bytes)
        assertEquals("", plan.workouts[0].exercises[0].technique)
    }

    // ── cell type coverage ────────────────────────────────────────────────────

    @Test
    fun `numeric cell value is converted to string`() {
        val bytes = buildXlsx {
            val sheet = newSheet()
            sheet.setCell(0, 1, "Plan")
            sheet.setCell(1, 1, "Treino A: X")
            sheet.setCell(2, 1, "Exercícios"); sheet.setCell(2, 2, "SxR"); sheet.setCell(2, 3, "Técnica Avançada")
            // Exercise name is a STRING, but SxR is a NUMERIC cell
            sheet.setCell(3, 1, "Leg Extension")
            val row = sheet.getRow(3) ?: sheet.createRow(3)
            row.createCell(2, org.apache.poi.ss.usermodel.CellType.NUMERIC).setCellValue(410.0)
            sheet.setCell(4, 1, "Intervalo entre séries e exercícios: 1 min")
        }

        val plan = parser.parse(bytes)
        // numericCellValue.toString() for 410.0 gives "410.0"
        assertEquals("410.0", plan.workouts[0].exercises[0].setsReps)
    }

    @Test
    fun `boolean cell value is converted to string`() {
        val bytes = buildXlsx {
            val sheet = newSheet()
            sheet.setCell(0, 1, "Plan")
            sheet.setCell(1, 1, "Treino A: X")
            sheet.setCell(2, 1, "Exercícios"); sheet.setCell(2, 2, "SxR"); sheet.setCell(2, 3, "Técnica Avançada")
            sheet.setCell(3, 1, "Squat"); sheet.setCell(3, 2, "4x10")
            // Technique is a BOOLEAN cell
            val row = sheet.getRow(3) ?: sheet.createRow(3)
            row.createCell(3, org.apache.poi.ss.usermodel.CellType.BOOLEAN).setCellValue(true)
            sheet.setCell(4, 1, "Intervalo entre séries e exercícios: 1 min")
        }

        val plan = parser.parse(bytes)
        assertEquals("true", plan.workouts[0].exercises[0].technique)
    }

    @Test
    fun `blank cell value returns empty string`() {
        val bytes = buildXlsx {
            val sheet = newSheet()
            sheet.setCell(0, 1, "Plan")
            sheet.setCell(1, 1, "Treino A: X")
            sheet.setCell(2, 1, "Exercícios"); sheet.setCell(2, 2, "SxR"); sheet.setCell(2, 3, "Técnica Avançada")
            sheet.setCell(3, 1, "Squat"); sheet.setCell(3, 2, "4x10")
            // Create a BLANK cell for technique
            val row = sheet.getRow(3) ?: sheet.createRow(3)
            row.createCell(3, org.apache.poi.ss.usermodel.CellType.BLANK)
            sheet.setCell(4, 1, "Intervalo entre séries e exercícios: 1 min")
        }

        val plan = parser.parse(bytes)
        assertEquals("", plan.workouts[0].exercises[0].technique)
    }

    // ── outer while else branch ───────────────────────────────────────────────

    @Test
    fun `rows without title columns are skipped in outer loop`() {
        // Insert an extra row before the first title that has data in col 0 only (not title cols 1,5,9)
        val bytes = buildXlsx {
            val sheet = newSheet()
            // Row 0: plan title (col 1)
            sheet.setCell(0, 1, "Plan Title")
            // Row 1: data in col 0 only — not a title row (cols 1,5,9 are empty)
            val extraRow = sheet.createRow(1)
            extraRow.createCell(0, org.apache.poi.ss.usermodel.CellType.STRING).setCellValue("some extra data")
            // Row 2: actual workout titles
            sheet.setCell(2, 1, "Treino A: Peitoral")
            // Row 3: header
            sheet.setCell(3, 1, "Exercícios"); sheet.setCell(3, 2, "SxR"); sheet.setCell(3, 3, "Técnica Avançada")
            // Row 4: exercise
            sheet.setCell(4, 1, "Supino"); sheet.setCell(4, 2, "4x10")
            // Row 5: interval
            sheet.setCell(5, 1, "Intervalo entre séries e exercícios: 1 a 2 minutos")
        }

        val plan = parser.parse(bytes)

        assertEquals(1, plan.workouts.size)
        assertEquals("Treino A: Peitoral", plan.workouts[0].title)
        assertEquals("Supino", plan.workouts[0].exercises[0].name)
    }

    // ── null row in outer while ───────────────────────────────────────────────

    @Test
    fun `null row in outer while is skipped`() {
        // Create an xlsx where rows 1 and 3 are null (POI: just don't create them)
        val bytes = buildXlsx {
            val sheet = newSheet()
            sheet.setCell(0, 1, "Plan")
            // Row 1 intentionally not created (null)
            // Row 2: workout title
            sheet.setCell(2, 1, "Treino A: X")
            // Row 3 intentionally not created (null) — but we start exercises at rowIdx+2
            // so exercises start at row 4
            sheet.setCell(3, 1, "Exercícios"); sheet.setCell(3, 2, "SxR"); sheet.setCell(3, 3, "Técnica Avançada")
            sheet.setCell(4, 1, "Bench"); sheet.setCell(4, 2, "5x5")
            sheet.setCell(5, 1, "Intervalo entre séries e exercícios: 2 min")
        }

        val plan = parser.parse(bytes)
        assertEquals(1, plan.workouts.size)
        assertEquals("Treino A: X", plan.workouts[0].title)
    }

    // ── Treino break without interval row ─────────────────────────────────────

    @Test
    fun `second block starting without interval row triggers treino break`() {
        // Block 1 exercises end with block 2's title row (no interval row in between)
        val bytes = buildXlsx {
            val sheet = newSheet()
            // Block 1
            sheet.setCell(0, 1, "Plan")
            sheet.setCell(1, 1, "Treino A: Peito")
            sheet.setCell(2, 1, "Exercícios"); sheet.setCell(2, 2, "SxR"); sheet.setCell(2, 3, "Técnica Avançada")
            sheet.setCell(3, 1, "Supino"); sheet.setCell(3, 2, "4x10")
            // Row 4 is block 2's title (no interval row for block 1)
            sheet.setCell(4, 1, "Treino B: Dorsal")
            sheet.setCell(5, 1, "Exercícios"); sheet.setCell(5, 2, "SxR"); sheet.setCell(5, 3, "Técnica Avançada")
            sheet.setCell(6, 1, "Pulldown"); sheet.setCell(6, 2, "3x12")
            sheet.setCell(7, 1, "Intervalo entre séries e exercícios: 1 a 2 minutos")
        }

        val plan = parser.parse(bytes)

        assertEquals(2, plan.workouts.size)
        assertEquals("Treino A: Peito", plan.workouts[0].title)
        assertEquals("Supino", plan.workouts[0].exercises[0].name)
        assertEquals("Treino B: Dorsal", plan.workouts[1].title)
        assertEquals("Pulldown", plan.workouts[1].exercises[0].name)
        assertEquals("1 a 2 minutos", plan.restInterval)
    }

    // ── interval row with no col2 content ────────────────────────────────────

    @Test
    fun `interval row with empty col2 does not set restInterval`() {
        val bytes = buildXlsx {
            val sheet = newSheet()
            sheet.setCell(0, 1, "Plan")
            sheet.setCell(1, 1, "Treino A: X")
            sheet.setCell(2, 1, "Exercícios"); sheet.setCell(2, 2, "SxR"); sheet.setCell(2, 3, "Técnica Avançada")
            sheet.setCell(3, 1, "Squat"); sheet.setCell(3, 2, "4x10")
            // Interval row has no col2 content
            sheet.setCell(4, 1, "Intervalo entre séries e exercícios")
            // col 2 intentionally not set
        }

        val plan = parser.parse(bytes)
        assertEquals("", plan.restInterval)
    }

    // ── inner while null row break ────────────────────────────────────────────

    @Test
    fun `inner while exits when no more rows`() {
        // Create xlsx where exercises end at the last row (no interval row)
        val bytes = buildXlsx {
            val sheet = newSheet()
            sheet.setCell(0, 1, "Plan")
            sheet.setCell(1, 1, "Treino A: Legs")
            sheet.setCell(2, 1, "Exercícios"); sheet.setCell(2, 2, "SxR"); sheet.setCell(2, 3, "Técnica Avançada")
            sheet.setCell(3, 1, "Squat"); sheet.setCell(3, 2, "5x5")
            // No interval row — inner while exits at end of sheet
        }

        val plan = parser.parse(bytes)
        assertEquals(1, plan.workouts.size)
        assertEquals(1, plan.workouts[0].exercises.size)
    }

    @Test
    fun `inner while skips null row within lastRowNum range`() {
        // exercises start at row 3, row 3 is null (not created), interval at row 5
        // The inner while should skip row 3 and eventually find the interval at row 5
        val bytes = buildXlsx {
            val sheet = newSheet()
            sheet.setCell(0, 1, "Plan")
            sheet.setCell(1, 1, "Treino A: X")
            sheet.setCell(2, 1, "Exercícios"); sheet.setCell(2, 2, "SxR"); sheet.setCell(2, 3, "Técnica Avançada")
            // Row 3 intentionally NOT created (null)
            sheet.setCell(4, 1, "Squat"); sheet.setCell(4, 2, "5x5")
            sheet.setCell(5, 1, "Intervalo entre séries e exercícios: 2 min")
        }

        val plan = parser.parse(bytes)
        assertEquals(1, plan.workouts.size)
        assertEquals(1, plan.workouts[0].exercises.size)
        assertEquals("Squat", plan.workouts[0].exercises[0].name)
        assertEquals("2 min", plan.restInterval)
    }}
