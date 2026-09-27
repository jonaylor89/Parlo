package com.parlo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.parlo.app.ui.MainViewModel
import com.parlo.app.ui.ParloNavHost
import com.parlo.app.ui.theme.ParloTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels { MainViewModel.factory(application) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ParloTheme { ParloNavHost(viewModel) }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.bind(this)
    }

    override fun onStop() {
        viewModel.unbind(this)
        super.onStop()
    }
}
