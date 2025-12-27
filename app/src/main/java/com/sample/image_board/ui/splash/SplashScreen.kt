package com.sample.image_board.ui.splash

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sample.image_board.viewmodel.AuthState
import com.sample.image_board.viewmodel.AuthViewModel
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
        onNavigateToLogin: () -> Unit,
        onNavigateToMain: () -> Unit,
        authViewModel: AuthViewModel = viewModel()
) {
    val authState by authViewModel.authState.collectAsState()

    // Animate alpha from 0 to 1
    var startAnimation by remember { mutableStateOf(false) }
    val alpha by
            animateFloatAsState(
                    targetValue = if (startAnimation) 1f else 0f,
                    animationSpec = tween(durationMillis = 1000),
                    label = "splash_fade_in"
            )

    // Start animation immediately
    LaunchedEffect(Unit) { startAnimation = true }

    // Navigate after delay
    LaunchedEffect(authState) {
        delay(2000) // 2 seconds
        when (authState) {
            is AuthState.Success -> onNavigateToMain()
            else -> onNavigateToLogin()
        }
    }

    Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
    ) {
        Text(
                text = "IMGR",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.alpha(alpha)
        )
    }
}
