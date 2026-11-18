package com.example

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.core.di.VybalAppContainer
import com.example.ui.VybalApp
import com.example.ui.state.AdminViewModel
import com.example.ui.state.EngagementViewModel
import com.example.ui.state.VybalAppViewModel
import com.example.ui.theme.VybalTheme

class MainActivity : ComponentActivity() {
    private val appContainer by lazy { VybalAppContainer(applicationContext) }
    private val viewModel by viewModels<VybalAppViewModel> {
        VybalAppViewModel.Factory(appContainer)
    }
    private val engagementViewModel by viewModels<EngagementViewModel> {
        EngagementViewModel.Factory(appContainer)
    }
    private val adminViewModel by viewModels<AdminViewModel> {
        AdminViewModel.Factory(appContainer)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            VybalTheme {
                VybalApp(
                    viewModel = viewModel,
                    engagementViewModel = engagementViewModel,
                    adminViewModel = adminViewModel,
                    imageLoader = appContainer.imageLoader
                )
            }
        }
    }
}
