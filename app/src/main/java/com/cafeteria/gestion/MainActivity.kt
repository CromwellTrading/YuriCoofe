package com.cafeteria.gestion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.cafeteria.gestion.ui.CafeteriaApp
import com.cafeteria.gestion.ui.CafeteriaViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: CafeteriaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CafeteriaApp(viewModel) }
    }
}
