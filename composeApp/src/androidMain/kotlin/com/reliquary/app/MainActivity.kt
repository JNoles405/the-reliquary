package com.reliquary.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.reliquary.app.data.createAndroidDriver
import com.reliquary.app.di.AppContainer

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = AppContainer(createAndroidDriver(applicationContext))
        setContent {
            App(container)
        }
    }
}
