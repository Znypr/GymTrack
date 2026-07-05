package com.example.gymtrack.core.backup

import org.json.JSONArray
import org.json.JSONObject

internal fun JSONObject.putOptional(name: String, value: Any?): JSONObject =
    put(name, value ?: JSONObject.NULL)

internal fun JSONObject.optionalString(name: String): String? =
    if (isNull(name)) null else getString(name)

internal fun JSONObject.optionalLong(name: String): Long? =
    if (isNull(name)) null else getLong(name)

internal fun JSONObject.optionalInt(name: String): Int? =
    if (isNull(name)) null else getInt(name)

internal fun JSONObject.optionalDouble(name: String): Double? =
    if (isNull(name)) null else getDouble(name)

internal fun <T> List<T>.asJsonArray(block: (T) -> JSONObject): JSONArray = JSONArray().also { array ->
    forEach { array.put(block(it)) }
}

internal fun <T> JSONArray.mapJsonObjects(block: (JSONObject) -> T): List<T> =
    List(length()) { index -> block(getJSONObject(index)) }
