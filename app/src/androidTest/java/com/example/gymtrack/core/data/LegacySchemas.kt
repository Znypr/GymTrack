package com.example.gymtrack.core.data

internal const val VERSION_1_NOTES_SCHEMA =
    "CREATE TABLE notes (timestamp INTEGER NOT NULL, text TEXT NOT NULL, PRIMARY KEY(timestamp))"

internal const val VERSION_2_NOTES_SCHEMA =
    "CREATE TABLE notes (timestamp INTEGER NOT NULL, title TEXT NOT NULL, text TEXT NOT NULL, PRIMARY KEY(timestamp))"

internal const val VERSION_3_NOTES_SCHEMA =
    "CREATE TABLE notes (timestamp INTEGER NOT NULL, title TEXT NOT NULL, text TEXT NOT NULL, categoryName TEXT, categoryColor INTEGER, PRIMARY KEY(timestamp))"
