package com.example.gymtrack.data

data class Category(
    val name: String,
    val color: Long
)

data class Settings(
    val is24Hour: Boolean = true,
    val roundingSeconds: Int = 15,
    val darkMode: Boolean = true,
    val categories: List<Category> = emptyList()
)

data class NoteLine(
    val title: String,
    val text: String,
    val timestamp: Long,
    val categoryName: String? = null,
    val categoryColor: Long? = null
)
