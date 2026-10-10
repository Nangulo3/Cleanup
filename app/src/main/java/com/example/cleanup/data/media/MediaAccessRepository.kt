package com.example.cleanup.data.media

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.cleanup.domain.model.MediaAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fuente única del estado de acceso a la biblioteca. La interfaz llama a [refresh] al volver
 * a primer plano porque el usuario puede cambiar el permiso desde Ajustes del sistema.
 */
class MediaAccessRepository(context: Context) {

    private val appContext = context.applicationContext

    private val _access = MutableStateFlow(currentAccess())
    val access: StateFlow<MediaAccess> = _access.asStateFlow()

    /** Permisos que hay que pedir en este dispositivo. */
    val permissionsToRequest: Array<String>
        get() = MediaPermissions.permissionsToRequest(Build.VERSION.SDK_INT)

    fun refresh() {
        _access.value = currentAccess()
    }

    /** Consulta el permiso en este momento (lo usa también el escaneo en segundo plano). */
    fun currentAccess(): MediaAccess =
        MediaPermissions.resolveAccess(Build.VERSION.SDK_INT) { permission ->
            ContextCompat.checkSelfPermission(appContext, permission) ==
                PackageManager.PERMISSION_GRANTED
        }
}
