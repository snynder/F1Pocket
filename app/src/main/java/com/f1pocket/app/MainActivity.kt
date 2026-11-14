package com.f1pocket.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.f1pocket.app.data.AppSettings
import com.f1pocket.app.data.Repo
import com.f1pocket.app.ui.AppRoot
import com.f1pocket.app.ui.F1Theme
import com.f1pocket.app.ui.LocalUppercase

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        setContent {
            val settings by Repo.settings.flow.collectAsState(initial = AppSettings())
            F1Theme(settings.theme) {
                CompositionLocalProvider(LocalUppercase provides settings.uppercase) {
                    AppRoot(settings)
                }
            }
        }
    }
}
