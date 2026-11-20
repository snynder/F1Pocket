package com.racepocket.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.racepocket.app.data.AppSettings
import com.racepocket.app.data.Repo
import com.racepocket.app.ui.AppRoot
import com.racepocket.app.ui.RaceTheme
import com.racepocket.app.ui.LocalUppercase

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        setContent {
            val settings by Repo.settings.flow.collectAsState(initial = AppSettings())
            RaceTheme(settings.theme) {
                CompositionLocalProvider(LocalUppercase provides settings.uppercase) {
                    AppRoot(settings)
                }
            }
        }
    }
}
