package com.example.cleanup.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.cleanup.R

/** Aviso de acceso parcial (Android 14+) con la acción para ampliar la selección. */
@Composable
fun PartialAccessBanner(onExpandAccess: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Info, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.partial_access_title),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                stringResource(R.string.partial_access_body),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            TextButton(
                onClick = onExpandAccess,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.partial_access_expand))
            }
        }
    }
}
