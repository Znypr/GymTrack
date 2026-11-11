package com.example.gymtrack.feature.about

/**
 * Open-source notices shown under About › Open-source licences (#338).
 *
 * Maintained by hand. `OpenSourceNoticesTest` reads `app/build.gradle.kts`, the version catalog
 * and `res/font/`, and fails when a shipped dependency or a bundled font has no notice here:
 * add the notice in the same change that adds the dependency.
 *
 * @property covers Maven coordinates this notice covers: `group:` (whole group) or
 *   `group:artifact`. Fonts use `font:<file name>`.
 */
data class OssNotice(
    val name: String,
    val copyright: String,
    val license: String,
    val licenseUrl: String,
    val covers: List<String>,
)

private const val APACHE_2 = "Apache License 2.0"
private const val APACHE_2_URL = "https://www.apache.org/licenses/LICENSE-2.0"
private const val OFL_1_1 = "SIL Open Font License 1.1"
private const val OFL_URL = "https://openfontlicense.org"

object OpenSourceNotices {
    val all: List<OssNotice> = listOf(
        OssNotice(
            name = "AndroidX (Core, Activity, Lifecycle, Navigation, DataStore, Room)",
            copyright = "Copyright The Android Open Source Project",
            license = APACHE_2,
            licenseUrl = APACHE_2_URL,
            covers = listOf(
                "androidx.core:", "androidx.activity:", "androidx.lifecycle:", "androidx.navigation:",
                "androidx.datastore:", "androidx.room:", "androidx.annotation:", "androidx.collection:",
                "androidx.sqlite:", "androidx.savedstate:",
            ),
        ),
        OssNotice(
            name = "Jetpack Compose (UI, Foundation, Material 3, Material Icons)",
            copyright = "Copyright The Android Open Source Project",
            license = APACHE_2,
            licenseUrl = APACHE_2_URL,
            covers = listOf(
                "androidx.compose:", "androidx.compose.ui:", "androidx.compose.foundation:",
                "androidx.compose.material:", "androidx.compose.material3:", "androidx.compose.runtime:",
                "androidx.compose.animation:",
            ),
        ),
        OssNotice(
            name = "Kotlin standard library and kotlinx.coroutines",
            copyright = "Copyright JetBrains s.r.o. and Kotlin Programming Language contributors",
            license = APACHE_2,
            licenseUrl = APACHE_2_URL,
            covers = listOf("org.jetbrains.kotlin:", "org.jetbrains.kotlinx:"),
        ),
        OssNotice(
            name = "Caveat font (weight 600, subset)",
            copyright = "Copyright 2014 The Caveat Project Authors (https://github.com/googlefonts/caveat)",
            license = OFL_1_1,
            licenseUrl = OFL_URL,
            covers = listOf("font:caveat_semibold.ttf"),
        ),
        OssNotice(
            name = "Patrick Hand font (subset)",
            copyright = "Copyright (c) 2010-2012 Patrick Wagesreiter",
            license = OFL_1_1,
            licenseUrl = OFL_URL,
            covers = listOf("font:patrick_hand_regular.ttf"),
        ),
    )

    /** True when some notice covers [coordinate] (`group:artifact`, or `font:<file>`). */
    fun covers(coordinate: String): Boolean = all.any { notice ->
        notice.covers.any { c -> if (c.endsWith(":")) coordinate.startsWith(c) else coordinate == c }
    }

    /** Full OFL texts bundled in `assets/licenses/` (OFL 1.1 requires the licence to travel with the font). */
    val bundledLicenseFiles: List<String> = listOf("licenses/Caveat-OFL.txt", "licenses/PatrickHand-OFL.txt")
}
