package com.workoutparser

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
class MainTest {

    @Test
    fun `parse single file outputs csv in same directory by default`(@TempDir tmpDir: Path) {
        val xlsxFile = createMinimalXlsx(tmpDir, "workout.xlsx")
        val main = WorkoutParserCli()

        main.run(input = xlsxFile.absolutePath, output = null)

        val expectedOutput = File(tmpDir.toFile(), "workout.csv")
        assertTrue(expectedOutput.exists(), "Expected CSV file to be created")
        val csv = expectedOutput.readText()
        assertTrue(csv.contains("Treino A: Peitoral"))
    }

    @Test
    fun `parse single file outputs csv to specified output path`(@TempDir tmpDir: Path) {
        val xlsxFile = createMinimalXlsx(tmpDir, "workout.xlsx")
        val outputFile = File(tmpDir.toFile(), "output/result.csv")
        val main = WorkoutParserCli()

        main.run(input = xlsxFile.absolutePath, output = outputFile.absolutePath)

        assertTrue(outputFile.exists())
        assertTrue(outputFile.readText().contains("Treino A: Peitoral"))
    }

    @Test
    fun `parse directory processes all xlsx files`(@TempDir tmpDir: Path) {
        val dir = tmpDir.toFile()
        createMinimalXlsx(dir.toPath(), "a.xlsx")
        createMinimalXlsx(dir.toPath(), "b.xlsx")
        // create a non-xlsx file — should be ignored
        File(dir, "notes.txt").writeText("ignore me")
        val outputDir = File(dir, "out")
        val main = WorkoutParserCli()

        main.run(input = dir.absolutePath, output = outputDir.absolutePath)

        assertTrue(File(outputDir, "a.csv").exists())
        assertTrue(File(outputDir, "b.csv").exists())
        assertFalse(File(outputDir, "notes.csv").exists())
    }

    @Test
    fun `parse directory with no output creates csv files alongside xlsx`(@TempDir tmpDir: Path) {
        val dir = tmpDir.toFile()
        createMinimalXlsx(dir.toPath(), "plan.xlsx")
        val main = WorkoutParserCli()

        main.run(input = dir.absolutePath, output = null)

        assertTrue(File(dir, "plan.csv").exists())
    }

    @Test
    fun `output csv contains rest interval`(@TempDir tmpDir: Path) {
        val xlsxFile = createMinimalXlsx(tmpDir, "w.xlsx", restInterval = "2 a 3 minutos")
        val main = WorkoutParserCli()

        main.run(input = xlsxFile.absolutePath, output = null)

        val csv = File(tmpDir.toFile(), "w.csv").readText()
        assertTrue(csv.contains("2 a 3 minutos"))
    }

    @Test
    fun `run with non-existent input throws error`() {
        val main = WorkoutParserCli()
        org.junit.jupiter.api.assertThrows<IllegalStateException> {
            main.run(input = "/does/not/exist/workout.xlsx", output = null)
        }
    }

    @Test
    fun `main function processes xlsx file via CLI args`(@TempDir tmpDir: Path) {
        val xlsxFile = createMinimalXlsx(tmpDir, "cli_main_test.xlsx")
        val outputFile = File(tmpDir.toFile(), "cli_main_test.csv")

        com.workoutparser.main(arrayOf("--input", xlsxFile.absolutePath, "--output", outputFile.absolutePath))

        assertTrue(outputFile.exists())
        assertTrue(outputFile.readText().contains("Treino A: Peitoral"))
    }

    // ── JSON format ──────────────────────────────────────────────────────────

    @Test
    fun `parse single file json outputs json in same directory by default`(@TempDir tmpDir: Path) {
        val xlsxFile = createMinimalXlsx(tmpDir, "workout.xlsx")
        val main = WorkoutParserCli()

        main.run(input = xlsxFile.absolutePath, output = null, format = "json")

        val expectedOutput = File(tmpDir.toFile(), "workout.json")
        assertTrue(expectedOutput.exists(), "Expected JSON file to be created")
        assertTrue(expectedOutput.readText().contains("\"Treino A\""))
    }

    @Test
    fun `parse single file json outputs to specified output path`(@TempDir tmpDir: Path) {
        val xlsxFile = createMinimalXlsx(tmpDir, "workout.xlsx")
        val outputFile = File(tmpDir.toFile(), "out/result.json")
        val main = WorkoutParserCli()

        main.run(input = xlsxFile.absolutePath, output = outputFile.absolutePath, format = "json")

        assertTrue(outputFile.exists())
        assertTrue(outputFile.readText().contains("\"Treino A\""))
    }

    @Test
    fun `parse directory json processes all xlsx files`(@TempDir tmpDir: Path) {
        val dir = tmpDir.toFile()
        createMinimalXlsx(dir.toPath(), "a.xlsx")
        createMinimalXlsx(dir.toPath(), "b.xlsx")
        val outputDir = File(dir, "out")
        val main = WorkoutParserCli()

        main.run(input = dir.absolutePath, output = outputDir.absolutePath, format = "json")

        assertTrue(File(outputDir, "a.json").exists())
        assertTrue(File(outputDir, "b.json").exists())
    }

    @Test
    fun `parse directory json with no output creates json files alongside xlsx`(@TempDir tmpDir: Path) {
        val dir = tmpDir.toFile()
        createMinimalXlsx(dir.toPath(), "plan.xlsx")
        val main = WorkoutParserCli()

        main.run(input = dir.absolutePath, output = null, format = "json")

        assertTrue(File(dir, "plan.json").exists())
    }

    @Test
    fun `main function produces json via CLI args with format flag`(@TempDir tmpDir: Path) {
        val xlsxFile = createMinimalXlsx(tmpDir, "cli_json_test.xlsx")
        val outputFile = File(tmpDir.toFile(), "cli_json_test.json")

        com.workoutparser.main(
            arrayOf("--input", xlsxFile.absolutePath, "--output", outputFile.absolutePath, "--format", "json")
        )

        assertTrue(outputFile.exists())
        assertTrue(outputFile.readText().contains("\"Treino A\""))
    }

    // ── helper ──────────────────────────────────────────────────────────────

    private fun createMinimalXlsx(dir: Path, name: String, restInterval: String = "1 a 2 minutos"): File {
        val bytes = buildMinimalXlsx(restInterval)
        val file = File(dir.toFile(), name)
        file.writeBytes(bytes)
        return file
    }

    private fun buildMinimalXlsx(restInterval: String): ByteArray {
        val wb = org.apache.poi.xssf.usermodel.XSSFWorkbook()
        val sheet = wb.createSheet("Treino")
        fun setCell(r: Int, c: Int, v: String) {
            val row = sheet.getRow(r) ?: sheet.createRow(r)
            row.createCell(c, org.apache.poi.ss.usermodel.CellType.STRING).setCellValue(v)
        }
        setCell(0, 1, "Plano")
        setCell(1, 1, "Treino A: Peitoral")
        setCell(2, 1, "Exercícios"); setCell(2, 2, "SxR"); setCell(2, 3, "Técnica Avançada")
        setCell(3, 1, "Supino"); setCell(3, 2, "4x10")
        setCell(4, 1, "Intervalo entre séries e exercícios: $restInterval")
        val out = java.io.ByteArrayOutputStream()
        wb.write(out)
        wb.close()
        return out.toByteArray()
    }
}
