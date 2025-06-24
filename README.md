
# Timestamped Notes App

A minimal Android notes application built with Jetpack Compose, designed to automatically assign a relative timestamp to each line of a note. This enables users to track precisely when each entry was made.

## Features

- **Line-based timestamps**  
  Each time the user presses Enter, a new line is added with a relative timestamp (e.g., "Just now", "2 minutes ago", "Yesterday 17:41").

- **Optimized for athletes and logging**  
  Ideal for tracking exercises and workout sets in chronological order. The timestamping system allows users to easily review the timing and sequence of their training sessions.

- **Grid-based note overview**  
  Notes are displayed in a responsive card layout for quick access and better organization.

- **Dedicated editing screen**  
  Tapping a note opens a separate page with full-screen editing capabilities for focused writing.

- **Multi-selection and batch deletion**  
  Long-press on notes to enable selection mode for bulk deletion or management.

- **Local data storage**  
  Notes are stored persistently on-device using Room database. No internet connection required.

## Technical Stack

- Kotlin with Jetpack Compose
- Room (SQLite persistence)
- Material 3 (dark theme)

## Use Case Example

An athlete recording their workout can note down each set, weight, or exercise in real time. The app automatically timestamps each entry, making it easy to later analyze the session duration, rest intervals, or order of execution.

## Status

This is a functional MVP. Planned improvements include search, tagging, and export or cloud synchronization capabilities.
