package com.example.cleanup.data.media

import android.Manifest
import com.example.cleanup.domain.model.MediaAccess
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaPermissionsTest {

    private val images = Manifest.permission.READ_MEDIA_IMAGES
    private val videos = Manifest.permission.READ_MEDIA_VIDEO
    private val userSelected = Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
    private val legacy = Manifest.permission.READ_EXTERNAL_STORAGE

    private fun grants(vararg granted: String): (String) -> Boolean = { it in granted }

    @Test
    fun `permisos a pedir segun la version`() {
        assertArrayEquals(arrayOf(legacy), MediaPermissions.permissionsToRequest(30))
        assertArrayEquals(arrayOf(legacy), MediaPermissions.permissionsToRequest(32))
        assertArrayEquals(arrayOf(images, videos), MediaPermissions.permissionsToRequest(33))
        assertArrayEquals(arrayOf(images, videos, userSelected), MediaPermissions.permissionsToRequest(34))
        assertArrayEquals(arrayOf(images, videos, userSelected), MediaPermissions.permissionsToRequest(36))
    }

    @Test
    fun `android 11 y 12 dependen del permiso de almacenamiento`() {
        assertEquals(MediaAccess.FULL, MediaPermissions.resolveAccess(30, grants(legacy)))
        assertEquals(MediaAccess.DENIED, MediaPermissions.resolveAccess(32, grants()))
    }

    @Test
    fun `android 13 con imagenes y videos es acceso completo`() {
        assertEquals(MediaAccess.FULL, MediaPermissions.resolveAccess(33, grants(images, videos)))
    }

    @Test
    fun `android 13 con solo un tipo es acceso parcial`() {
        assertEquals(MediaAccess.PARTIAL, MediaPermissions.resolveAccess(33, grants(images)))
        assertEquals(MediaAccess.PARTIAL, MediaPermissions.resolveAccess(33, grants(videos)))
    }

    @Test
    fun `android 13 ignora el permiso de seleccion parcial`() {
        assertEquals(MediaAccess.DENIED, MediaPermissions.resolveAccess(33, grants(userSelected)))
    }

    @Test
    fun `android 14 con seleccion parcial es acceso parcial`() {
        assertEquals(MediaAccess.PARTIAL, MediaPermissions.resolveAccess(34, grants(userSelected)))
    }

    @Test
    fun `android 14 con todo concedido es acceso completo`() {
        assertEquals(
            MediaAccess.FULL,
            MediaPermissions.resolveAccess(34, grants(images, videos, userSelected)),
        )
    }

    @Test
    fun `sin permisos es acceso denegado`() {
        assertEquals(MediaAccess.DENIED, MediaPermissions.resolveAccess(34, grants()))
        assertEquals(MediaAccess.DENIED, MediaPermissions.resolveAccess(36, grants(legacy)))
    }
}
