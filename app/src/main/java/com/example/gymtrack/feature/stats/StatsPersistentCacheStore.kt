package com.example.gymtrack.feature.stats

import android.content.Context
import com.example.gymtrack.core.data.WeightUnit
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persisted Stats results (SharedPreferences, JSON). Note text is NOT stored: it is the input,
 * and storing it made every entry as large as the whole history (#303). [StatsEngine] re-attaches
 * the counted notes after a read.
 */
class StatsPersistentCacheStore(context: Context) : StatsStateStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun read(storageKey: String): StatsState? {
        val raw = preferences.getString(storageKey(storageKey), null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            if (json.optInt("cacheVersion") != CACHE_VERSION) return null
            json.toStatsState()
        }.getOrNull()
    }

    override fun write(storageKey: String, state: StatsState) {
        val json = state.toJson().apply {
            put("cacheVersion", CACHE_VERSION)
        }
        val stored = storageKey(storageKey)
        val slot = storageKey(StatsStateStore.slotOf(storageKey))
        val editor = preferences.edit().putString(stored, json.toString())
        // One entry per slot (range + unit): an older entry in the same slot is for an older
        // history and can never be hit again. Entries of older cache versions go too.
        preferences.all.keys
            .filter { it.startsWith(LEGACY_STORAGE_PREFIX) && it != stored }
            .filter { !it.startsWith(STORAGE_PREFIX) || it.startsWith(slot + StatsStateStore.SLOT_SEPARATOR) }
            .forEach(editor::remove)
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
        put("topExercises", topExercisesToJson(topExercises))
        put("weeklyWorkoutCounts", weeklyWorkoutCountsToJson(weeklyWorkoutCounts))
        put("trainingInsights", trainingInsightsToJson(trainingInsights))
        put("currentRange", currentRange.name)
        put("weeklyDurations", JSONArray().also { array ->
            weeklyDurations.forEach { (weekStart, minutes) ->
                array.put(JSONObject().apply {
                    put("weekStart", weekStart)
                    putFloat("minutes", minutes)
                })
            }
        })
        put("unreasonableDurationCount", unreasonableDurationCount)
        put("timedSetCount", timedSets.setCount)
        put("timedSetSeconds", timedSets.totalSeconds)
        put("timedProgress", timedProgressToJson(timedProgress))
        put("sourceNoteCount", sourceNoteCount)
        put("unfinishedCount", unfinishedCount)
        put("displayUnit", displayUnit.storageValue)
    }

    private fun JSONObject.toStatsState(): StatsState {
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
            filteredNotes = emptyList(),
            weeklyDurations = optJSONArray("weeklyDurations").toWeeklyDurations(),
            unreasonableDurationCount = optInt("unreasonableDurationCount"),
            timedSets = TimedSetSummary(optInt("timedSetCount"), optInt("timedSetSeconds")),
            timedProgress = optJSONArray("timedProgress").toTimedProgress(),
            isLoading = false,
            loadingMessage = null,
            sourceNoteCount = optInt("sourceNoteCount"),
            unfinishedCount = optInt("unfinishedCount"),
            displayUnit = WeightUnit.fromStorage(optString("displayUnit")),
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
                row.forEach { value -> values.put(value) }
            })
        }
    }

    private fun topExercisesToJson(items: List<Pair<String, Int>>): JSONArray = JSONArray().also { array ->
        items.forEach { (name, count) ->
            array.put(JSONObject().apply {
                put("name", name)
                put("count", count)
            })
        }
    }

    private fun weeklyWorkoutCountsToJson(items: List<WeeklyWorkoutCount>): JSONArray = JSONArray().also { array ->
        items.forEach { item ->
            array.put(JSONObject().apply {
                put("weekStart", item.weekStart)
                put("count", item.count)
            })
        }
    }

    private fun trainingInsightsToJson(items: List<TrainingInsightRow>): JSONArray = JSONArray().also { array ->
        items.forEach { row ->
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
                put("trendPoints", trendPointsToJson(row.trendPoints))
                put("unknownUnitSets", row.unknownUnitSets)
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

    private fun trendPointsToJson(items: List<TrainingTrendPoint>): JSONArray = JSONArray().also { array ->
        items.forEach { point ->
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

    private fun timedProgressToJson(groups: List<TimedProgressGroup>): JSONArray = JSONArray().also { array ->
        groups.forEach { group ->
            array.put(JSONObject().apply {
                put("baseKey", group.baseKey)
                put("name", group.name)
                put("metric", group.metric.name)
                put("unitLabel", group.unitLabel)
                put("timedSetCount", group.timedSetCount)
                put("series", JSONArray().also { seriesArray ->
                    group.series.forEach { series ->
                        seriesArray.put(JSONObject().apply {
                            put("key", series.key)
                            put("points", JSONArray().also { points ->
                                series.points.forEach { point ->
                                    points.put(JSONObject().apply {
                                        put("timestamp", point.timestamp)
                                        putFloat("value", point.value)
                                    })
                                }
                            })
                        })
                    }
                })
            })
        }
    }

    private fun JSONArray?.toTimedProgress(): List<TimedProgressGroup> {
        val source = this ?: return emptyList()
        return buildList {
            for (index in 0 until source.length()) {
                val item = source.optJSONObject(index) ?: continue
                val seriesArray = item.optJSONArray("series")
                val series = buildList {
                    for (s in 0 until (seriesArray?.length() ?: 0)) {
                        val seriesItem = seriesArray?.optJSONObject(s) ?: continue
                        val pointsArray = seriesItem.optJSONArray("points")
                        add(
                            TimedProgressSeries(
                                key = seriesItem.optString("key"),
                                points = buildList {
                                    for (p in 0 until (pointsArray?.length() ?: 0)) {
                                        val point = pointsArray?.optJSONObject(p) ?: continue
                                        add(StatsPoint(point.optLong("timestamp"), point.optFloat("value")))
                                    }
                                },
                            ),
                        )
                    }
                }
                add(
                    TimedProgressGroup(
                        baseKey = item.optString("baseKey"),
                        name = item.optString("name"),
                        metric = runCatching { TimedProgressMetric.valueOf(item.optString("metric")) }
                            .getOrDefault(TimedProgressMetric.HOLD_SECONDS),
                        unitLabel = item.optString("unitLabel", "s"),
                        series = series,
                        timedSetCount = item.optInt("timedSetCount"),
                    ),
                )
            }
        }
    }

    private fun JSONArray?.toWeeklyDurations(): List<Pair<Long, Float>> {
        val source = this ?: return emptyList()
        return buildList {
            for (index in 0 until source.length()) {
                val item = source.optJSONObject(index) ?: continue
                add(item.optLong("weekStart") to item.optFloat("minutes"))
            }
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
                        unknownUnitSets = item.optInt("unknownUnitSets"),
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

    private fun JSONObject.putFloat(name: String, value: Float) {
        put(name, if (value.isFinite()) value.toDouble() else 0.0)
    }

    private fun JSONObject.optFloat(name: String): Float = optDouble(name, 0.0).toFloat()

    private companion object {
        // 2: finished-only counts (#322) and display-unit conversion (#326).
        // 3: timed sets (#283), duration chart data, no note text, stable key (#303).
        // 4: weekly average spans at least one week (a single workout was "604800000 per week").
        // Bump this whenever a cached StatsState field's calculation changes.
        const val CACHE_VERSION = 4
        const val PREFERENCES_NAME = "stats_cache"
        const val LEGACY_STORAGE_PREFIX = "stats_state_"
        const val STORAGE_PREFIX = "stats_state_v$CACHE_VERSION:"
    }
}
