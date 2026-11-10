package com.example.gymtrack.core.ui.components

import androidx.compose.ui.graphics.Color
import com.example.gymtrack.core.data.DEFAULT_CATEGORIES
import com.example.gymtrack.core.ui.theme.NotebookColors
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryKindTest {

    @Test
    fun `default categories map to their marks`() {
        assertEquals(
            listOf(CategoryKind.PUSH, CategoryKind.PULL, CategoryKind.LEGS),
            DEFAULT_CATEGORIES.map { CategoryKind.fromName(it.name) },
        )
    }

    @Test
    fun `names are matched loosely and user categories are other`() {
        assertEquals(CategoryKind.PUSH, CategoryKind.fromName(" PUSH "))
        assertEquals(CategoryKind.LEGS, CategoryKind.fromName("leg"))
        assertEquals(CategoryKind.OTHER, CategoryKind.fromName("Arms"))
        assertEquals(CategoryKind.OTHER, CategoryKind.fromName(null))
    }

    @Test
    fun `other uses the custom colour, else muted ink`() {
        val custom = Color(0xFF123456)
        assertEquals(custom, NotebookColors.categoryColor(CategoryKind.OTHER, custom))
        assertEquals(NotebookColors.inkMuted, NotebookColors.categoryColor(CategoryKind.OTHER))
        assertEquals(NotebookColors.catPush, NotebookColors.categoryColor(CategoryKind.PUSH, custom))
    }
}
