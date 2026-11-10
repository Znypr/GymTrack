package com.example.gymtrack.feature.help

/**
 * Help texts (#345). Pure data so tests can check them. Only shipped features are described
 * (docs/launch/04-marketing.md "claim only what ships"); grammar per docs/EDITOR_BEHAVIOUR.md:
 * sets are REPS × WEIGHT.
 */
data class GrammarEntry(val example: String, val meaning: String)

data class FaqEntry(val question: String, val answer: String)

object HelpContent {
    val grammar: List<GrammarEntry> = listOf(
        GrammarEntry("8x 80", "8 reps at 80 (your default unit)"),
        GrammarEntry("8x", "8 reps, same weight as the set before in this exercise"),
        GrammarEntry("8x 80lb", "a unit you type wins over the default"),
        GrammarEntry("10+3+2x 45", "rest-pause or partials: 15 reps at 45"),
        GrammarEntry("60s", "a 60-second hold (timed set)"),
        GrammarEntry("45s 10", "a 45-second hold with 10 of load"),
        GrammarEntry("Enter twice", "an empty Enter ends the exercise; type the next one"),
        GrammarEntry("L/R", "unilateral: each set is one effort per side"),
        GrammarEntry("SS", "superset: done back to back with the next exercise"),
    )

    val faq: List<FaqEntry> = listOf(
        FaqEntry(
            "How do I log a set?",
            "Type the exercise name, press Enter, then type reps × weight on the keypad, e.g. 8 X 80, and press Enter. " +
                "Each Enter saves the set and opens the next one.",
        ),
        FaqEntry(
            "I typed a wrong number. How do I fix it?",
            "Tap the set row to edit its text. Workouts save automatically as a draft; nothing is lost when you leave.",
        ),
        FaqEntry(
            "Kilograms or pounds?",
            "Pick your default unit in Settings. A unit typed in a set (80lb) wins. Changing the default never changes " +
                "saved workouts; Progress just shows every weight converted to your current unit.",
        ),
        FaqEntry(
            "Which workouts count in Progress?",
            "Only finished workouts (you tapped Finish). Unfinished drafts are listed as \"+N unfinished, not counted\".",
        ),
        FaqEntry(
            "Can I change or remove the categories?",
            "Yes, in Settings › Categories, including Push, Pull and Legs. Saved workouts keep the category name they " +
                "were saved with.",
        ),
        FaqEntry(
            "How does the timer work?",
            "The workout clock starts with your first exercise and keeps running when you switch apps. " +
                "Each set also records the time since the previous one.",
        ),
        FaqEntry(
            "How do I back up or move to a new phone?",
            "Settings › Data › Back up all data creates one file in a place you choose. On the new phone, install " +
                "ZnyprFit and use Restore from backup. Restore replaces the data on that phone.",
        ),
        FaqEntry(
            "Where is my data? Does it leave my phone?",
            "Your workouts are stored only on this phone. ZnyprFit has no account, no ads, no analytics and no internet " +
                "permission. Android may include the app's data in your own Google backup; you can turn that off.",
        ),
        FaqEntry(
            "How do I delete all my data?",
            "Uninstall ZnyprFit, or clear its storage in Android Settings › Apps › ZnyprFit › Storage. " +
                "Backup files you exported stay where you saved them.",
        ),
        FaqEntry(
            "How do I report a problem or suggest something?",
            "Use Send feedback below. It opens your email app with the app and Android version filled in. " +
                "No workout data is attached.",
        ),
    )
}
