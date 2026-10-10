package com.example.cleanup.ui.screens.permission

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import com.example.cleanup.data.media.MediaAccessRepository
import com.example.cleanup.domain.model.MediaAccess

/**
 * Encapsula la solicitud de permisos de medios desde Compose.
 *
 * [permanentlyDenied] se vuelve true cuando, tras una solicitud, Android ya no mostraría el
 * diálogo (el usuario lo rechazó dos veces): en ese caso solo queda abrir los ajustes de la app.
 */
@Stable
class MediaPermissionRequester internal constructor(
    private val launch: () -> Unit,
    private val readPermanentlyDenied: () -> Boolean,
) {
    // Se lee del estado de Compose en cada acceso, así la pantalla se recompone cuando cambia.
    val permanentlyDenied: Boolean get() = readPermanentlyDenied()

    /** Muestra el diálogo del sistema (o, con acceso parcial, el selector para ampliar). */
    fun request() = launch()
}

@Composable
fun rememberMediaPermissionRequester(repository: MediaAccessRepository): MediaPermissionRequester {
    val activity = LocalActivity.current
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        repository.refresh()
        val stillDenied = repository.access.value == MediaAccess.DENIED
        permanentlyDenied = stillDenied && activity != null &&
            repository.permissionsToRequest.none { permission ->
                ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
            }
    }

    return remember(launcher) {
        MediaPermissionRequester(
            launch = { launcher.launch(repository.permissionsToRequest) },
            readPermanentlyDenied = { permanentlyDenied },
        )
    }
}

/** Abre la pantalla de información de la app en Ajustes del sistema (permisos). */
fun Context.openAppSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
    ).apply {
        if (this@openAppSettings !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(intent)
}
