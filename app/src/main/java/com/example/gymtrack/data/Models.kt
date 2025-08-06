package com.example.gymtrack.data

data class Category(
    val name: String,
    val color: Long
)

val DEFAULT_CATEGORIES = listOf(
    Category("Push", 0xFFFF0000),
    Category("Pull", 0xFF800080),
    Category("Legs", 0xFF008000),
)

data class Settings(
    val is24Hour: Boolean = true,
    val roundingSeconds: Int = 5,
    val darkMode: Boolean = true,
    val categories: List<Category> = DEFAULT_CATEGORIES
)

data class NoteLine(
    val title: String,
    val text: String,
    val timestamp: Long,
    val categoryName: String? = null,
    val categoryColor: Long? = null,
    val learnings: String = ""
)
