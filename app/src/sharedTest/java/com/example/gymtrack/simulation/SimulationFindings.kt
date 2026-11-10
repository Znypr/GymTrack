package com.example.gymtrack.simulation

import java.time.LocalDate

enum class Severity { HIGH, MEDIUM, LOW, INFO }

data class SimulationFinding(
    val id: String,
    val severity: Severity,
    val title: String,
    val seed: Long,
    val firstDay: Int,
    val firstDate: LocalDate,
    val workoutIndex: Int?,
    val example: String,
    val issue: String? = null,
    val occurrences: Int = 1,
)

/**
 * Collects invariant violations. The first occurrence of each finding id keeps its simulated
 * day, date, workout and example; later ones only bump the count.
 */
class SimulationFindings {
    private val byId = linkedMapOf<String, SimulationFinding>()
    val metrics = mutableListOf<String>()

    val all: List<SimulationFinding> get() = byId.values.sortedWith(compareBy({ it.severity }, { it.firstDay }))

    fun report(
        id: String,
        severity: Severity,
        title: String,
        history: SimHistory,
        day: Int,
        workoutIndex: Int?,
        example: String,
        issue: String? = null,
    ) {
        val existing = byId[id]
        byId[id] = existing?.copy(occurrences = existing.occurrences + 1) ?: SimulationFinding(
            id = id,
            severity = severity,
            title = title,
            seed = history.seed,
            firstDay = day,
            firstDate = history.dateOf(day),
            workoutIndex = workoutIndex,
            example = example,
            issue = issue,
        )
    }

    fun metric(line: String) {
        metrics += line
    }

    fun merge(other: SimulationFindings) {
        other.all.forEach { finding ->
            val existing = byId[finding.id]
            byId[finding.id] = existing?.copy(occurrences = existing.occurrences + finding.occurrences) ?: finding
        }
        metrics += other.metrics
    }

    fun toMarkdown(heading: String): String = buildString {
        appendLine("## $heading")
        appendLine()
        appendLine("| Severity | Finding | First seen (sim day / date) | Seed | Workout | Count | Issue |")
        appendLine("| --- | --- | --- | --- | --- | --- | --- |")
        all.forEach { f ->
            appendLine(
                "| ${f.severity} | **${f.id}** ${f.title} | day ${f.firstDay} / ${f.firstDate} | ${f.seed} | " +
                    "${f.workoutIndex ?: "-"} | ${f.occurrences} | ${f.issue ?: "new"} |",
            )
        }
        appendLine()
        appendLine("### Examples")
        all.forEach { f -> appendLine("- **${f.id}**: ${f.example.replace("\n", "⏎")}") }
        if (metrics.isNotEmpty()) {
            appendLine()
            appendLine("### Metrics")
            metrics.forEach { appendLine("- $it") }
        }
    }
}
