package com.example.gymtrack.feature.stats

import android.content.Context
import com.example.gymtrack.core.data.NoteLine
import org.json.JSONArray
import org.json.JSONObject

class StatsPersistentCacheStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun read(storageKey: String): StatsState? {
        val raw = preferences.getString(storageKey(storageKey), null) ?: return null
        return runCatching { JSONObject(raw).toStatsState() }.getOrNull()
    }

    fun write(storageKey: String, state: StatsState) {
        val json = state.toJson().apply {
            put("cacheVersion", CACHE_VERSION)
        }
        preferences.edit()
            .putString(storageKey(storageKey), json.toString())
            .apply()
        trimCache()
    }

    private fun trimCache() {
        val cacheKeys = preferences.all.keys
            .filter { it.startsWith(STORAGE_PREFIX) }
            .sorted()
        if (cacheKeys.size <= MAX_PERSISTED_ENTRIES) return

        val editor = preferences.edit()
        cacheKeys.take(cacheKeys.size - MAX_PERSISTED_ENTRIES).forEach(editor::remove)
        editor.apply()
    }

    private fun storageKey(key: String): String = "$STORAGE_PREFIX$key"

    private fun StatsState.toJson(): JSONObject = JSONObject().apply {
        put("totalNotes", totalNotes)
        putFloat("avgWorkoutsPerWeek", avgWorkoutsPerWeek)
        putFloat("avgSets", avgSets)
        put("categoryCounts", categoryCounts.toIntMapJson())
        put("averageDurations", averageDurations.toFloatMapJson())
        put("heatmapData", heatmapData.toJson())
        put("topExercises", topExercises.toJson())
        put("weeklyWorkoutCounts", weeklyWorkoutCounts.toJson())
        put("trainingInsights", trainingInsights.toJson())
        put("currentRange", currentRange.name)
        put("filteredNotes", filteredNotes.toNotesJson())
        put("sourceNoteCount", sourceNoteCount)
    }

    private fun JSONObject.toStatsState(): StatsState {
        if (optInt("cacheVersion") != CACHE_VERSION) return StatsState()
        return StatsState(
            totalNotes = optInt("totalNotes"),
            avgWorkoutsPerWeek = optFloat("avgWorkoutsPerWeek"),
            avgSets = optFloat("avgSets"),
            categoryCounts = optJSONObject("categoryCounts").toIntMap(),
            averageDurations = optJSONObject("averageDurations").toFloatMap(),
            heatmapData = optJSONArray("heatmapData").toHeatmap(),
            topExercises = optJSONArray("topExercises").toTopExercises(),
            weeklyWorkoutCounts = optJSONArray("weeklyWorkoutCounts").toWeeklyWorkoutCounts(),
            trainingInsights = optJSONArray("trainingInsights").toTrainingInsights(),
            currentRange = runCatching { TimeRange.valueOf(optString("currentRange")) }.getOrDefault(TimeRange.ALL_TIME),
            filteredNotes = optJSONArray("filteredNotes").toNotes(),
            isLoading = false,
            loadingMessage = null,
            sourceNoteCount = optInt("sourceNoteCount"),
        )
    }

    private fun Map<String, Int>.toIntMapJson(): JSONObject = JSONObject().also { json ->
        forEach { (key, value) -> json.put(key, value) }
    }

    private fun Map<String, Float>.toFloatMapJson(): JSONObject = JSONObject().also { json ->
        forEach { (key, value) -> json.putFloat(key, value) }
    }

    private fun Array<IntArray>.toJson(): JSONArray = JSONArray().also { rows ->
        forEach { row ->
            rows.put(JSONArray().also { values ->
                row.forEach(values::put)
            })
        }
    }

    private fun List<Pair<String, Int>>.toJson(): JSONArray = JSONArray().also { array ->
        forEach { (name, count) ->
            array.put(JSONObject().apply {
                put("name", name)
                put("count", count)
            })
        }
    }

    private fun List<WeeklyWorkoutCount>.toJson(): JSONArray = JSONArray().also { array ->
        forEach { item ->
            array.put(JSONObject().apply {
                put("weekStart", item.weekStart)
                put("count", item.count)
            })
        }
    }

    private fun List<TrainingInsightRow>.toJson(): JSONArray = JSONArray().also { array ->
        forEach { row ->
            array.put(JSONObject().apply {
                put("category", row.category)
                put("workoutCount", row.workoutCount)
                put("summary", row.summary)
                put("strength", row.strength.toJson())
                put("density", row.density.toJson())
                put("setDepth", row.setDepth.toJson())
                put("exerciseCount", row.exerciseCount.toJson())
                put("reps", row.reps.toJson())
                put("duration", row.duration.toJson())
                put("trendPoints", row.trendPoints.toJson())
            })
        }
    }

    private fun TrainingMetricShift.toJson(): JSONObject = JSONObject().apply {
        put("label", label)
        putFloat("baseline", baseline)
        putFloat("recent", recent)
        put("unit", unit)
        put("higherIsUsuallyGood", higherIsUsuallyGood)
        put("impact", impact.name)
    }

    private fun List<TrainingTrendPoint>.toJson(): JSONArray = JSONArray().also { array ->
        forEach { point ->
            array.put(JSONObject().apply {
                put("timestamp", point.timestamp)
                putFloat("strengthPercent", point.strengthPercent)
                putFloat("densityPercent", point.densityPercent)
                putFloat("depthPercent", point.depthPercent)
                putFloat("exerciseCountPercent", point.exerciseCountPercent)
                putFloat("repsPercent", point.repsPercent)
                putFloat("durationPercent", point.durationPercent)
            })
        }
    }

    private fun List<NoteLine>.toNotesJson(): JSONArray = JSONArray().also { array ->
        forEach { note ->
            array.put(JSONObject().apply {
                put("title", note.title)
                put("text", note.text)
                put("timestamp", note.timestamp)
                put("categoryName", note.categoryName)
                put("categoryColor", note.categoryColor)
                put("learnings", note.learnings)
                put("rowMetadata", note.rowMetadata)
            })
        }
    }

    private fun JSONObject?.toIntMap(): Map<String, Int> {
        val source = this ?: return emptyMap()
        return source.keys().asSequence().associateWith { key -> source.optInt(key) }
    }

    private fun JSONObject?.toFloatMap(): Map<String, Float> {
        val source = this ?: return emptyMap()
        return source.keys().asSequence().associateWith { key -> source.optFloat(key) }
    }

    private fun JSONArray?.toHeatmap(): Array<IntArray> {
        val source = this ?: return Array(7) { IntArray(24) }
        return Array(7) { day ->
            val row = source.optJSONArray(day)
            IntArray(24) { hour -> row?.optInt(hour) ?: 0 }
        }
    }

    private fun JSONArray?.toTopExercises(): List<Pair<String, Int>> {
        val source = this ?: return emptyList()
        return buildList {
            for (index in 0 until source.length()) {
                val item = source.optJSONObject(index) ?: continue
                add(item.optString("name") to item.optInt("count"))
            }
        }
    }

    private fun JSONArray?.toWeeklyWorkoutCounts(): List<WeeklyWorkoutCount> {
        val source = this ?: return emptyList()
        return buildList {
            for (index in 0 until source.length()) {
                val item = source.optJSONObject(index) ?: continue
                add(WeeklyWorkoutCount(item.optLong("weekStart"), item.optInt("count")))
            }
        }
    }

    private fun JSONArray?.toTrainingInsights(): List<TrainingInsightRow> {
        val source = this ?: return emptyList()
        return buildList {
            for (index in 0 until source.length()) {
                val item = source.optJSONObject(index) ?: continue
                add(
                    TrainingInsightRow(
                        category = item.optString("category"),
                        workoutCount = item.optInt("workoutCount"),
                        summary = item.optString("summary"),
                        strength = item.optJSONObject("strength").toMetricShift(),
                        density = item.optJSONObject("density").toMetricShift(),
                        setDepth = item.optJSONObject("setDepth").toMetricShift(),
                        exerciseCount = item.optJSONObject("exerciseCount").toMetricShift(),
                        reps = item.optJSONObject("reps").toMetricShift(),
                        duration = item.optJSONObject("duration").toMetricShift(),
                        trendPoints = item.optJSONArray("trendPoints").toTrendPoints(),
                    ),
                )
            }
        }
    }

    private fun JSONObject?.toMetricShift(): TrainingMetricShift {
        val source = this ?: return TrainingMetricShift("", 0f, 0f, "")
        return TrainingMetricShift(
            label = source.optString("label"),
            baseline = source.optFloat("baseline"),
            recent = source.optFloat("recent"),
            unit = source.optString("unit"),
            higherIsUsuallyGood = source.optBoolean("higherIsUsuallyGood", true),
            impact = runCatching { MetricImpact.valueOf(source.optString("impact")) }.getOrDefault(MetricImpact.NEUTRAL),
        )
    }

    private fun JSONArray?.toTrendPoints(): List<TrainingTrendPoint> {
        val source = this ?: return emptyList()
        return buildList {
            for (index in 0 until source.length()) {
                val item = source.optJSONObject(index) ?: continue
                add(
                    TrainingTrendPoint(
                        timestamp = item.optLong("timestamp"),
                        strengthPercent = item.optFloat("strengthPercent"),
                        densityPercent = item.optFloat("densityPercent"),
                        depthPercent = item.optFloat("depthPercent"),
                        exerciseCountPercent = item.optFloat("exerciseCountPercent"),
                        repsPercent = item.optFloat("repsPercent"),
                        durationPercent = item.optFloat("durationPercent"),
                    ),
                )
            }
        }
    }

    private fun JSONArray?.toNotes(): List<NoteLine> {
        val source = this ?: return emptyList()
        return buildList {
            for (index in 0 until source.length()) {
                val item = source.optJSONObject(index) ?: continue
                add(
                    NoteLine(
                        title = item.optString("title"),
                        text = item.optString("text"),
                        timestamp = item.optLong("timestamp"),
                        categoryName = item.optNullableString("categoryName"),
                        categoryColor = item.optNullableLong("categoryColor"),
                        learnings = item.optString("learnings"),
                        rowMetadata = item.optString("rowMetadata"),
                    ),
                )
            }
        }
    }

    private fun JSONObject.putFloat(name: String, value: Float) {
        put(name, if (value.isFinite()) value.toDouble() else 0.0)
    }

    private fun JSONObject.optFloat(name: String): Float = optDouble(name, 0.0).toFloat()

    private fun JSONObject.optNullableString(name: String): String? =
        if (has(name) && !isNull(name)) optString(name) else null

    private fun JSONObject.optNullableLong(name: String): Long? =
        if (has(name) && !isNull(name)) optLong(name) else null

    private companion object {
        const val CACHE_VERSION = 1
        const val MAX_PERSISTED_ENTRIES = 12
        const val PREFERENCES_NAME = "stats_cache"
        const val STORAGE_PREFIX = "stats_state_"
    }
}
