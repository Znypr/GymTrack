package com.example.gymtrack.feature.help

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import com.example.gymtrack.BuildConfig

const val FEEDBACK_EMAIL = "znyprfitness@gmail.com"

/** A feedback email: device facts only, never workout data (#345). */
data class FeedbackEmail(val to: String, val subject: String, val body: String)

/** Pure, so it can be unit-tested without Android. */
fun buildFeedbackEmail(
    appVersion: String,
    androidVersion: String,
    sdkInt: Int,
    deviceModel: String,
): FeedbackEmail = FeedbackEmail(
    to = FEEDBACK_EMAIL,
    subject = "ZnyprFit feedback ($appVersion)",
    body = buildString {
        append("What happened, or what would you like?\n\n\n")
        append("---\n")
        append("App: ZnyprFit $appVersion\n")
        append("Android: $androidVersion (API $sdkInt)\n")
        append("Device: $deviceModel\n")
        append("No workout data is included. Attach a screenshot if it helps.\n")
    },
)

/** The app's version line, e.g. "0.1.0 (2026070701)". */
fun appVersionLabel(): String = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

/** Opens the user's email app with [buildFeedbackEmail] for this device. */
fun sendFeedback(context: Context) {
    val email = buildFeedbackEmail(
        appVersion = appVersionLabel(),
        androidVersion = Build.VERSION.RELEASE ?: "?",
        sdkInt = Build.VERSION.SDK_INT,
        deviceModel = listOfNotNull(Build.MANUFACTURER, Build.MODEL).joinToString(" "),
    )
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:")
        putExtra(Intent.EXTRA_EMAIL, arrayOf(email.to))
        putExtra(Intent.EXTRA_SUBJECT, email.subject)
        putExtra(Intent.EXTRA_TEXT, email.body)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No email app found. Write to $FEEDBACK_EMAIL", Toast.LENGTH_LONG).show()
    }
}

/** Opens [url] in the browser; shows the address when no browser exists. */
fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, url, Toast.LENGTH_LONG).show()
    }
}
