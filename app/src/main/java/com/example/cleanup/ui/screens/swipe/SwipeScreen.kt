package com.example.cleanup.ui.screens.swipe

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.cleanup.R
import com.example.cleanup.ui.components.AppIcons
import com.example.cleanup.ui.components.EmptyState

/** Deslizar fotos y videos. Fase 1: solo el estado vacío; la revisión llega en la Fase 4. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeScreen(modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.swipe_title)) }) },
    ) { padding ->
        EmptyState(
            icon = AppIcons.Swipe,
            title = stringResource(R.string.swipe_empty_title),
            message = stringResource(R.string.swipe_empty_body),
            modifier = Modifier.padding(padding),
        )
    }
}
