package com.example.gymtrack.core.util

fun importAndProcessCsv(
    csvRows: List<String>,
    parser: WorkoutParser,
    workoutTimestamp: Long // ID for the entire workout
): List<ParsedSetDTO> {

    val finalSetsToSave = mutableListOf<ParsedSetDTO>()

    var currentExerciseIndex = -1
    var lastWeight = 0f

    // We start processing from the beginning and rely on robust filtering inside the loop.
    val dataRows = csvRows

    dataRows.forEach { rowString ->

        // 1. Robust Row Filtering: Skip blank lines and known text headers immediately.
        if (rowString.isBlank() || rowString.startsWith("Title,") || rowString.startsWith("Main Index,")) {
            return@forEach
        }

        // --- Core Row Split ---
        val csvRow = parseCsvRow(rowString)

        // 2. Column Count Check: Must have at least the index and set details.
        if (csvRow.size < 2) return@forEach

        val exerciseIndexStr = csvRow[0].trim()
        val setDetailsText = csvRow[1].trim()

        // 3. Index Validity Check: Ensure the first column is a valid exercise index number.
        val newIndex = exerciseIndexStr.toIntOrNull()

        if (newIndex == null || newIndex < 0) {
            return@forEach // Skip row if index is null (text) or negative
        }

        // --- 4. Check for Exercise Group Change (State Reset) ---
        if (newIndex != currentExerciseIndex) {
            currentExerciseIndex = newIndex
            lastWeight = 0f // Reset weight memory for a new exercise group
        }

        // --- 5. Core Parsing and Stateful Carry-Over ---

        // The parser logic is designed for text blobs, so we feed it the relevant cell content.
        // We ensure we only run the parser if there is actual set text.
        if (setDetailsText.isNotBlank()) {
            val parsedSetList = parser.parseWorkout(setDetailsText)

            parsedSetList.forEach { set ->

                if (set.weight > 0f) {
                    lastWeight = set.weight // Store the new weight
                } else if (set.reps > 0) {
                    // If weight is 0 but reps exist, apply carry-over logic
                    set.weight = lastWeight
                }

                // Assign the workout ID and save the corrected set data
                finalSetsToSave.add(set.copy(workoutId = workoutTimestamp))
            }
        }
    }

    return finalSetsToSave
}