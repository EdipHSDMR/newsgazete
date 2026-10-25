package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.ui.CizgiApp
import com.example.util.CizgiNotificationHelper
import com.example.data.NewsRefreshScheduler

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NewsRefreshScheduler.schedule(this)
        CizgiNotificationHelper.createNotificationChannels(this)
        enableEdgeToEdge()
        setContent {
            CizgiApp()
        }
    }
}
