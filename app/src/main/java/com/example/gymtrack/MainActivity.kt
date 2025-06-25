package com.example.gymtrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.gymtrack.data.Settings
import com.example.gymtrack.data.SettingsStore
import com.example.gymtrack.ui.screens.NavigationHost
import com.example.gymtrack.ui.theme.GymTrackTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settingsState = remember { mutableStateOf(Settings()) }
            val context = LocalContext.current
            LaunchedEffect(Unit) {
                settingsState.value = SettingsStore.load(context)
            }
            LaunchedEffect(settingsState.value) {
                SettingsStore.save(context, settingsState.value)
            }
            GymTrackTheme(darkTheme = settingsState.value.darkMode) {
                val lastRoute = rememberSaveable { mutableStateOf("main") }
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                LaunchedEffect(navBackStackEntry) {
                    val route = navBackStackEntry?.destination?.route
                    if (route != null && route !in setOf("settings", "edit")) {
                        lastRoute.value = route
                    }
                }
                val start = if (lastRoute.value == "settings") "main" else lastRoute.value
                NavigationHost(navController, settingsState, start)
            }
        }
    }
}
