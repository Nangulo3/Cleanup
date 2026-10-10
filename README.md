# Cleanup

Aplicación Android personal para analizar, organizar y limpiar fotos y videos **en el propio
teléfono**: duplicados exactos, fotos y videos similares, capturas de pantalla y un modo
"Deslizar" para revisar mes a mes. Sin cuentas, anuncios, servidores ni límites.

> Estado: **Fase 5** (duplicados exactos verificados por contenido). Ver el avance por fases abajo.

## Requisitos

- Android Studio compatible con **AGP 9.2.1** (la versión con la que se creó el proyecto).
- JDK 21 para el daemon de Gradle (Android Studio lo gestiona; ver `gradle/gradle-daemon-jvm.properties`).
- Teléfono o emulador con **Android 11 (API 30) o superior**. Para probar el acceso parcial
  a fotos se necesita Android 14 (API 34) o superior.

## Versiones

| Componente | Versión | Nota |
|---|---|---|
| Android Gradle Plugin | 9.2.1 | Kotlin integrado (sin plugin `kotlin-android`) |
| Gradle | 9.4.1 | |
| Kotlin / Compose compiler | 2.2.10 | |
| KSP | 2.3.4 | Generación de código de Room |
| compileSdk / targetSdk / minSdk | 36.1 / 36 / 30 | |
| androidx.core (core-ktx) | 1.18.0 | 1.19+ exige compileSdk 37 |
| Compose BOM | 2026.02.01 | Material 3 |
| Navigation 3 | 1.0.0 | 1.2+ exige compileSdk 37 |
| Lifecycle | 2.10.0 | 2.11+ exige compileSdk 37 con Compose |
| Room | 2.8.4 | |
| WorkManager | 2.11.1 | |
| DataStore Preferences | 1.2.1 | |
| Coil | 3.3.0 | Sin módulo de red: la app no declara `INTERNET` |
| kotlinx-serialization-core | 1.9.0 | Claves de navegación |

Todas las versiones están en `gradle/libs.versions.toml`.

## Compilar y ejecutar

1. Abrir la carpeta del proyecto en Android Studio y esperar el **Gradle Sync**.
2. Elegir un dispositivo (teléfono con depuración USB o emulador) y pulsar **Run**.

Desde la terminal de Android Studio (Windows):

```powershell
.\gradlew.bat assembleDebug            # compila el APK de depuración
.\gradlew.bat testDebugUnitTest        # pruebas unitarias (JVM)
.\gradlew.bat connectedDebugAndroidTest  # pruebas instrumentadas (con emulador/teléfono conectado)
```

El APK de depuración queda en `app/build/outputs/apk/debug/app-debug.apk`.

## Permisos

| Android | Permisos pedidos |
|---|---|
| 11–12 (API 30–32) | `READ_EXTERNAL_STORAGE` |
| 13 (API 33) | `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO` |
| 14+ (API 34+) | Los anteriores + `READ_MEDIA_VISUAL_USER_SELECTED` (acceso parcial) |

No se piden permisos de escritura: para borrar se usa `MediaStore.createDeleteRequest`
(o `createTrashRequest`, según Ajustes), que muestra la confirmación de Android.
WorkManager añade por sí mismo `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`
y `ACCESS_NETWORK_STATE`; ninguno da acceso a internet.

## Privacidad

Todo el análisis ocurre en el teléfono. No se suben fotos, videos, miniaturas, nombres de
archivo ni metadatos. La copia de seguridad en la nube y la transferencia entre dispositivos
están desactivadas para los datos de la app.

## Estructura

```
app/src/main/java/com/example/cleanup/
  CleanupApp.kt, AppContainer.kt, MainActivity.kt
  ui/          tema, navegación, componentes y pantallas
  domain/      modelos y reglas puras (p. ej. ScanPlanner, SwipeResume)
  data/media/  permisos, consultas a MediaStore y miniaturas del sistema
  data/db/     Room: entidades, DAOs y base de datos (esquema exportado en app/schemas/)
  data/repo/   repositorios que consume la interfaz
  detection/   huellas de contenido, planificación de hashes y sugerencia de copia a conservar
  deletion/    Papelera de revisión, deshacer y coordinación de la eliminación con Android
  worker/      escaneo en segundo plano (WorkManager) y actualización automática
  settings/    preferencias locales (DataStore)
  util/        formato de tamaños, cantidades, fechas y duraciones
```

## Cómo funciona el escaneo

1. **Catálogo**: por cada volumen (almacenamiento interno, tarjeta SD) se compara la versión y la
   generación de MediaStore con las del último escaneo. Si la versión cambió o no hay escaneo previo
   se lee todo; si no, solo lo añadido o modificado desde la última generación.
2. **Conciliación por IDs**: se comparan los IDs visibles con los guardados para incorporar elementos
   que aparecieron sin cambiar de generación (p. ej. al ampliar el acceso parcial) y quitar los que
   se borraron desde otra app o ya no son accesibles.
3. Los resultados se guardan por lotes de 500, así que interrumpir el escaneo no corrompe nada.
4. Con la app abierta, un `ContentObserver` detecta cambios en la biblioteca y lanza un escaneo
   incremental tras 2 s sin más cambios. El primer análisis siempre lo inicia el usuario.

La app nunca borra archivos en este proceso: solo actualiza su base de datos local.

## Papelera de revisión y eliminación

- **Marcar** (desde el visor o con selección múltiple en las cuadrículas) solo añade una marca en
  `pending_deletions`. El archivo sigue en el teléfono y no se libera espacio.
- La marca es única por ID de MediaStore: marcar el mismo archivo desde dos pantallas no lo duplica.
- **Deshacer** revierte la última acción de marcar o desmarcar; el historial (`action_log`)
  sobrevive al cierre de la app y guarda las últimas 50 acciones.
- **Eliminar definitivamente**:
  1. Confirmación propia con número de archivos y tamaño estimado.
  2. Se comprueba en MediaStore que siguen existiendo. Con acceso completo, los que ya no existen se
     quitan de la cola; con acceso parcial se conservan, porque pueden no estar autorizados.
  3. Se guarda la solicitud (`deletion_batches`) y Android muestra su propio diálogo
     (`createDeleteRequest`, o `createTrashRequest` si en Ajustes se eligió la papelera del sistema).
     Máximo 1000 elementos por solicitud; si Android la rechaza por tamaño se reintenta con la mitad.
  4. Tras la respuesta se vuelve a consultar MediaStore y solo se retira de la cola lo que de verdad
     desapareció. El resultado distingue eliminados, ya inexistentes, sin acceso, fallidos y cancelados.
     El espacio liberado se calcula solo con lo confirmado.
- Si Android cierra la app mientras el diálogo está abierto, la solicitud guardada se verifica al
  recibir la respuesta o antes de la siguiente eliminación.

## Deslizar

- La lista agrupa fotos y videos por mes (fecha de captura; si no existe, la de modificación o la de
  alta) y muestra el progreso de cada mes y el total. "Reanudar" abre el mes de la última decisión si
  le quedan pendientes, o el más reciente con pendientes.
- En la revisión, arrastrar a la derecha (o el botón **Conservar**) no modifica nada; arrastrar a la
  izquierda (o **Eliminar**) solo añade el elemento a la Papelera de revisión. Las dos acciones también
  están disponibles como acciones de TalkBack.
- Si se conserva un elemento que ya estaba en la papelera (marcado desde otra pantalla), se quita de
  ella: la decisión más reciente manda. Deshacer restaura la marca original.
- La posición no se guarda aparte: es el primer elemento del mes sin decisión, así que sobrevive al
  cierre de la app y "Deshacer" la recupera automáticamente.
- Si un elemento ya no existe, se quita de la lista; si existe pero no se puede mostrar, se avisa y se
  puede decidir igual.
- "Volver a revisar este mes" borra las decisiones del mes, sin tocar la papelera ni los archivos.

## Duplicados exactos

El escaneo tiene una segunda fase que solo lee contenido cuando hace falta:

1. Solo pueden ser idénticos los archivos del mismo tipo **y tamaño**; el resto no se lee.
2. Entre ellos se calcula una **huella rápida** (SHA-256 de tamaño + primeros 64 KB + últimos 64 KB).
3. Solo los que coinciden en la huella rápida se leen completos (**SHA-256** por bloques de 64 KB,
   dos archivos a la vez como máximo). El tamaño o el nombre nunca bastan para declarar un duplicado.
4. Cada huella guarda la "firma" del archivo (tamaño, fecha de modificación y generación de MediaStore).
   Si el archivo cambia, la huella se ignora y se recalcula; si la lectura falla, el archivo no se
   clasifica y se reintenta solo en un reanálisis completo.

En la pantalla de Duplicados la app **sugiere** qué copia conservar (carpeta de cámara antes que
descargas o chats, luego la que conserva la fecha de captura, luego la más antigua; si nada destaca,
una de forma estable) y explica el motivo. Se puede elegir otra copia. "Marcar sobrantes" nunca marca
la copia que se conserva, y si esa copia ya está en la papelera el grupo se omite. Al eliminar desde
la papelera se avisa si se borrarían **todas** las copias de algún grupo.

**Limitación conocida:** sin el permiso de ubicación en medios (que la app no pide a propósito),
Android oculta los datos GPS de las fotos al leerlas. Dos fotos que solo difieran en esos datos se
consideran idénticas.

Historial de la base de datos: v1 catálogo · v2 papelera, deshacer y solicitudes · v3 decisiones de
Deslizar · v4 huellas de contenido y copias elegidas (migraciones automáticas generadas por Room a
partir de `app/schemas/`).

## Pruebas

| Tipo | Qué cubre | Comando |
|---|---|---|
| Unitarias (JVM) | Permisos por versión, formatos, plan de escaneo, conversión de filas de MediaStore, conciliación de eliminaciones, reanudar en Deslizar, huellas SHA-256, planificación de hashes, sugerencia de copia | `.\gradlew.bat testDebugUnitTest` |
| Instrumentadas | Consultas de Room, escaneo contra el MediaStore real, papelera y deshacer, decisiones de Deslizar, duplicados con archivos reales (copia exacta, cambio al final, cambio en el medio), migración 1→4 | `.\gradlew.bat connectedDebugAndroidTest` |

**Pruebas manuales de eliminación** (el diálogo de Android no se automatiza porque su texto cambia
según el fabricante):

- [ ] Marcar desde una cuadrícula y desde el visor el mismo archivo → aparece una sola vez.
- [ ] Quitar de la papelera y deshacer.
- [ ] Eliminar y **cancelar** el diálogo de Android → todo sigue en la papelera.
- [ ] Eliminar y **aceptar** → el resultado muestra los eliminados y el espacio verificado; Inicio se actualiza.
- [ ] Borrar desde la galería un archivo que está en la papelera y abrir la papelera → se quita solo.
- [ ] Modo "papelera del sistema" en Ajustes → el resultado dice "movidos" y no promete espacio liberado.

Las pruebas instrumentadas insertan imágenes propias en `Pictures/CleanupTest` y las borran al terminar.

## Avance por fases

- [x] Fase 0 — Configuración del proyecto
- [x] Fase 1 — Tema, navegación y permisos
- [x] Fase 2 — Catálogo MediaStore, escaneo incremental e Inicio
- [x] Fase 3 — Papelera de revisión y eliminación segura
- [x] Fase 4 — Deslizar fotos y videos
- [x] Fase 5 — Duplicados exactos *(pendiente de verificar en dispositivo)*
- [ ] Fase 6 — Capturas, fotos similares y fotos de chat
- [ ] Fase 7 — Videos similares
- [ ] Fase 8 — Ajustes, rendimiento y accesibilidad
- [ ] Fase 9 — Verificación final y APK firmado
