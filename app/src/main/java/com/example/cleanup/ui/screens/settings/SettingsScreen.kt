package com.example.cleanup.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cleanup.BuildConfig
import com.example.cleanup.LocalAppContainer
import com.example.cleanup.R
import com.example.cleanup.domain.model.MediaAccess
import com.example.cleanup.settings.DeletionMode
import com.example.cleanup.settings.ThemeMode
import com.example.cleanup.ui.components.AppIcons
import com.example.cleanup.ui.components.SectionHeader
import com.example.cleanup.ui.screens.permission.openAppSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    access: MediaAccess,
    onExpandAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val viewModel: SettingsViewModel = viewModel { SettingsViewModel(container.settingsRepository) }
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            // Apariencia
            SectionHeader(stringResource(R.string.settings_section_appearance))
            Column(Modifier.selectableGroup()) {
                ThemeMode.entries.forEach { mode ->
                    RadioOptionRow(
                        title = stringResource(mode.labelRes()),
                        selected = settings.themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                    )
                }
            }

            // Eliminación
            SectionHeader(stringResource(R.string.settings_section_deletion))
            Column(Modifier.selectableGroup()) {
                RadioOptionRow(
                    title = stringResource(R.string.deletion_mode_permanent),
                    description = stringResource(R.string.deletion_mode_permanent_desc),
                    selected = settings.deletionMode == DeletionMode.PERMANENT,
                    onClick = { viewModel.setDeletionMode(DeletionMode.PERMANENT) },
                )
                RadioOptionRow(
                    title = stringResource(R.string.deletion_mode_trash),
                    description = stringResource(R.string.deletion_mode_trash_desc),
                    selected = settings.deletionMode == DeletionMode.SYSTEM_TRASH,
                    onClick = { viewModel.setDeletionMode(DeletionMode.SYSTEM_TRASH) },
                )
            }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Top) {
                Icon(
                    AppIcons.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.settings_irreversible_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Acceso
            SectionHeader(stringResource(R.string.settings_section_access))
            Text(
                stringResource(access.labelRes()),
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(8.dp))
            Row {
                if (access == MediaAccess.PARTIAL) {
                    TextButton(onClick = onExpandAccess) {
                        Text(stringResource(R.string.partial_access_expand))
                    }
                    Spacer(Modifier.width(8.dp))
                }
                OutlinedButton(onClick = { context.openAppSettings() }) {
                    Text(stringResource(R.string.permission_open_settings))
                }
            }

            // Privacidad
            SectionHeader(stringResource(R.string.settings_section_privacy))
            Text(
                stringResource(R.string.settings_privacy_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Acerca de
            SectionHeader(stringResource(R.string.settings_section_about))
            Text(
                stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Fila de opción única accesible: toda la fila es tocable y TalkBack la anuncia como
 * botón de opción (el RadioButton interno no recibe clics propios).
 */
@Composable
private fun RadioOptionRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    description: String? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(top = 2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

private fun MediaAccess.labelRes(): Int = when (this) {
    MediaAccess.FULL -> R.string.access_full
    MediaAccess.PARTIAL -> R.string.access_partial
    MediaAccess.DENIED -> R.string.access_denied
}
