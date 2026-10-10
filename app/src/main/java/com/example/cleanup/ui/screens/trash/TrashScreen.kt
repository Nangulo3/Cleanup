package com.example.cleanup.ui.screens.trash

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

/** Papelera de revisión. Fase 1: estado vacío; la cola real llega en la Fase 3. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.trash_title)) }) },
    ) { padding ->
        EmptyState(
            icon = AppIcons.Delete,
            title = stringResource(R.string.trash_empty_title),
            message = stringResource(R.string.trash_empty_body),
            modifier = Modifier.padding(padding),
        )
    }
}
