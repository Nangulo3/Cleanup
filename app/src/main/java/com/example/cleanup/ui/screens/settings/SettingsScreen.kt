package com.example.cleanup.ui.screens.settings

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.Switch
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.selection.toggleable
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
import com.example.cleanup.settings.SimilarityLevel
import com.example.cleanup.settings.ThemeMode
import com.example.cleanup.ui.components.AppIcons
import com.example.cleanup.ui.components.SectionHeader
import com.example.cleanup.ui.screens.permission.openAppSettings
import com.example.cleanup.ui.screens.similar.levelLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    access: MediaAccess,
    onExpandAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val viewModel: SettingsViewModel = viewModel {
        SettingsViewModel(
            container.settingsRepository,
            container.scanScheduler,
            container.appDataCleaner,
            container.applicationScope,
        )
    }
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val clearState by viewModel.clearState.collectAsStateWithLifecycle()
    var showClearDialog by rememberSaveable { mutableStateOf(false) }
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

            // Similitud
            SectionHeader(stringResource(R.string.settings_section_similarity))
            Column(Modifier.selectableGroup()) {
                SimilarityLevel.entries.forEach { level ->
                    RadioOptionRow(
                        title = levelLabel(level),
                        description = stringResource(level.descriptionRes()),
                        selected = settings.similarityLevel == level,
                        onClick = { viewModel.setSimilarityLevel(level) },
                    )
                }
            }

            // Análisis
            SectionHeader(stringResource(R.string.settings_section_scan))
            SwitchRow(
                title = stringResource(R.string.settings_auto_scan),
                description = stringResource(R.string.settings_auto_scan_desc),
                checked = settings.autoScan,
                onCheckedChange = viewModel::setAutoScan,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.settings_reanalyze_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = viewModel::reanalyzeAll) {
                Icon(AppIcons.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_reanalyze))
            }
            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.settings_clear_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showClearDialog = true },
                enabled = clearState != ClearDataState.RUNNING,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(AppIcons.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_clear))
            }
            val clearMessage = when (clearState) {
                ClearDataState.IDLE -> null
                ClearDataState.RUNNING -> R.string.settings_clear_running
                ClearDataState.DONE -> R.string.settings_clear_done
                ClearDataState.FAILED -> R.string.settings_clear_failed
            }
            if (clearMessage != null) {
                Text(
                    stringResource(clearMessage),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (clearState == ClearDataState.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    // TalkBack lee el cambio de estado sin que el usuario tenga que buscarlo.
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
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

    if (showClearDialog) {
        ClearDataDialog(
            onConfirm = {
                showClearDialog = false
                viewModel.clearAllData()
            },
            onDismiss = { showClearDialog = false },
        )
    }
}

/** Confirmación antes de borrar los datos de la app: explica qué se borra y qué no. */
@Composable
private fun ClearDataDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(AppIcons.Warning, contentDescription = null) },
        title = { Text(stringResource(R.string.settings_clear_confirm_title)) },
        text = { Text(stringResource(R.string.settings_clear_confirm_body)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.settings_clear_confirm_action)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * Fila con interruptor: toda la fila es tocable y TalkBack la anuncia como interruptor
 * (el Switch interno no recibe clics propios).
 */
@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
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

private fun SimilarityLevel.descriptionRes(): Int = when (this) {
    SimilarityLevel.STRICT -> R.string.similarity_strict_desc
    SimilarityLevel.NORMAL -> R.string.similarity_normal_desc
    SimilarityLevel.BROAD -> R.string.similarity_broad_desc
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
