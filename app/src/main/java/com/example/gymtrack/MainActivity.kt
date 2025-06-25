package com.example.gymtrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.navigation.compose.rememberNavController
import com.example.gymtrack.data.Settings
import com.example.gymtrack.ui.screens.NavigationHost
import com.example.gymtrack.ui.theme.GymTrackTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settingsState = remember { mutableStateOf(Settings()) }
            GymTrackTheme(darkTheme = settingsState.value.darkMode) {
                val navController = rememberNavController()
                NavigationHost(navController, settingsState)
            }
        }
    }
}
