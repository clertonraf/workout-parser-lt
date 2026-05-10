package com.workoutparser

import com.workoutparser.model.Exercise
import com.workoutparser.model.Workout
import com.workoutparser.model.WorkoutPlan
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.xssf.usermodel.XSSFRow
import org.apache.poi.xssf.usermodel.XSSFSheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File

class XlsxParser {

    // Column indices (0-based) for the three workout "slots" in a block
    private val titleCols = listOf(1, 5, 9)

    fun parse(bytes: ByteArray): WorkoutPlan {
        val wb = XSSFWorkbook(bytes.inputStream())
        return wb.use { parseSheet(it.getSheetAt(0)) }
    }

    fun parse(file: File): WorkoutPlan = parse(file.readBytes())

    private fun parseSheet(sheet: XSSFSheet): WorkoutPlan {
        val workouts = mutableListOf<Workout>()
        var restInterval = ""

        // Skip plan title row (row 0), start from row 1
        var rowIdx = 1
        val lastRow = sheet.lastRowNum

        while (rowIdx <= lastRow) {
            val row = sheet.getRow(rowIdx)
            if (row == null) { rowIdx++; continue }

            val blockTitles = titleCols.map { col -> rowString(row, col) }

            if (blockTitles.any { it.isNotBlank() }) {
                var exerciseRowIdx = rowIdx + 2
                val slotCount = blockTitles.count { it.isNotBlank() }
                val blockExercises = List(slotCount) { mutableListOf<Exercise>() }

                while (exerciseRowIdx <= lastRow) {
                    val exRow = sheet.getRow(exerciseRowIdx)
                    if (exRow == null) { exerciseRowIdx++; continue }

                    val col1Value = rowString(exRow, 1)

                    if (col1Value.startsWith("Intervalo entre séries")) {
                        val colons = col1Value.indexOf(": ")
                        val intervalFromCell = if (colons >= 0) col1Value.substring(colons + 2).trim() else ""
                        val intervalFromCol2 = rowString(exRow, 2)
                        val intervalValue = intervalFromCell.ifBlank { intervalFromCol2 }
                        if (restInterval.isEmpty() && intervalValue.isNotBlank()) {
                            restInterval = intervalValue
                        }
                        exerciseRowIdx++
                        break
                    }

                    if (col1Value.startsWith("Treino")) break

                    blockTitles.forEachIndexed { slotIdx, title ->
                        if (title.isNotBlank()) {
                            val baseCol = titleCols[slotIdx]
                            val name = rowString(exRow, baseCol)
                            if (name.isNotBlank()) {
                                val sxr = rowString(exRow, baseCol + 1)
                                val techRaw = rowString(exRow, baseCol + 2).trim()
                                val tech = if (techRaw.isBlank()) "" else techRaw
                                blockExercises[slotIdx].add(Exercise(name, sxr, tech))
                            }
                        }
                    }

                    exerciseRowIdx++
                }

                blockTitles.forEachIndexed { slotIdx, title ->
                    if (title.isNotBlank()) {
                        workouts.add(Workout(title, blockExercises[slotIdx]))
                    }
                }

                rowIdx = exerciseRowIdx
            } else {
                rowIdx++
            }
        }

        return WorkoutPlan(restInterval, workouts)
    }

    /** Reads a cell's string value, or "" if the cell is absent or blank. */
    private fun rowString(row: XSSFRow, col: Int): String {
        val cell = row.getCell(col) ?: return ""
        return cellString(cell)
    }

    private fun cellString(cell: Cell): String = when (cell.cellType) {
        CellType.STRING  -> cell.richStringCellValue.string
        CellType.NUMERIC -> cell.numericCellValue.toString()
        CellType.BOOLEAN -> cell.booleanCellValue.toString()
        else             -> ""
    }
}
