# Workout Parser

A Kotlin CLI tool that converts workout plan spreadsheets (`.xlsx`) into `.csv` format.

## Requirements

- Java 21+

## Build

```bash
./gradlew shadowJar
```

This produces a self-contained fat jar at `build/libs/workout-parser-1.0.0.jar`.

To also run tests and verify coverage:

```bash
./gradlew check
```

## Usage

```
java -jar build/libs/workout-parser-1.0.0.jar --input <file-or-dir> [--output <file-or-dir>] [--format csv|json]
```

| Option | Short | Required | Description |
|--------|-------|----------|-------------|
| `--input` | `-i` | ✅ | Path to a `.xlsx` file or a directory containing `.xlsx` files |
| `--output` | `-o` | ❌ | Path for the output file or directory. Defaults to the same location as the input |
| `--format` | `-f` | ❌ | Output format: `csv` (default) or `json` |

### Parse a single file to CSV (default)

```bash
java -jar build/libs/workout-parser-1.0.0.jar \
  --input documents/original/2024-10-17.xlsx \
  --output documents/csv/2024-10-17.csv
```

### Parse a single file to JSON

```bash
java -jar build/libs/workout-parser-1.0.0.jar \
  --input documents/original/2024-10-17.xlsx \
  --output documents/json/2024-10-17.json \
  --format json
```

### Parse all files in a directory

```bash
# CSV
java -jar build/libs/workout-parser-1.0.0.jar \
  --input documents/original/ \
  --output documents/csv/

# JSON
java -jar build/libs/workout-parser-1.0.0.jar \
  --input documents/original/ \
  --output documents/json/ \
  --format json
```

When `--output` is omitted, the generated files are written next to their source `.xlsx` files with the appropriate extension (`.csv` or `.json`).

## CSV Output Format

Each CSV file uses `;` as the column separator and Windows line endings (`\r\n`).

| Row(s) | Content |
|--------|---------|
| 1 | Empty (margin) |
| 2 | Rest interval recommendation (e.g. `Intervalo entre séries e exercícios: 1 a 2 minutos`) |
| 3 | Empty |
| 4 | Workout titles (one per group of 4 columns) |
| 5 | Column headers (`Exercícios`, `SxR`, `Técnica Avançada`) per workout |
| 6+ | Exercise data rows, padded so all workouts have the same number of rows |

## JSON Output Format

Each JSON file is pretty-printed with 4-space indentation and contains:

```json
{
    "rest_interval": [60, 120],
    "workouts": [
        {
            "name": "Treino A",
            "body_parts": ["Dorsal", "Abdômen"],
            "exercises": [
                {
                    "exercise": "Pull-up",
                    "advanced_technique": "FST-7",
                    "sets": 4,
                    "reps": 10
                }
            ]
        }
    ]
}
```

| Field | Type | Description |
|-------|------|-------------|
| `rest_interval` | `[int, int]` | Rest interval in seconds `[min, max]` |
| `name` | string | Workout identifier (e.g. `"Treino A"`) |
| `body_parts` | string[] | Muscle groups targeted |
| `exercise` | string | Exercise name |
| `advanced_technique` | string | Advanced technique or empty string |
| `sets` | int | Number of sets |
| `reps` | int \| string | Number of reps, or `"F"` for failure |

## Running Tests

```bash
./gradlew test
```

A JaCoCo HTML coverage report is generated at `build/reports/jacoco/test/html/index.html`.

## Tech Stack

- **Kotlin 1.9** / **Gradle 8.10**
- **Apache POI 5.3.0** — xlsx parsing
- **Clikt 4.4.0** — CLI argument parsing
- **Jackson 2.17.2** — JSON serialization
- **JUnit 5** — testing
- **JaCoCo** — coverage (≥ 100% line, ≥ 95% branch)
