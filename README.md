# Cleanup

Aplicación Android personal para analizar, organizar y limpiar fotos y videos **en el propio
teléfono**: duplicados exactos, fotos y videos similares, capturas de pantalla y un modo
"Deslizar" para revisar mes a mes. Sin cuentas, anuncios, servidores ni límites.

> Estado: **Fase 8** (ajustes, rendimiento y accesibilidad). Ver el avance por fases abajo.

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

**Registros (logs)**: en la versión release el código de la app no escribe nada en Logcat (WorkManager
puede registrar mensajes de estado del trabajo, sin datos de tus archivos). En debug solo se registra el
error del escaneo y los avisos de StrictMode.

## Ajustes

| Sección | Opciones |
|---|---|
| Apariencia | Tema del sistema, claro u oscuro. |
| Eliminación | Eliminar definitivamente (`createDeleteRequest`) o mover a la papelera del sistema (`createTrashRequest`), con aviso de que la eliminación puede ser irreversible. |
| Fotos y videos similares | Nivel más estricto, normal o más amplio (vuelve a agrupar reutilizando las firmas). |
| Análisis | **Analizar cambios automáticamente** (activado por defecto; al desactivarlo solo se analiza con el botón Analizar). **Volver a analizar todo** (recalcula huellas). **Borrar datos de la app**: vacía la base de datos local (resultados, papelera de revisión, progreso de Deslizar, historial para deshacer) y vuelve a analizar; no toca fotos, videos ni ajustes. |
| Acceso | Estado del permiso, ampliar acceso parcial y abrir los ajustes de Android. |
| Privacidad y Acerca de | Procesamiento local, sin cuentas ni servidores; versión de la app. |

## Rendimiento

- Todo lo pesado (MediaStore, hashes, miniaturas, fotogramas, base de datos) corre fuera del hilo
  principal: WorkManager para el escaneo y corrutinas en `Dispatchers.IO` para el resto.
- En **debug** se activa **StrictMode**: Logcat avisa (etiqueta `StrictMode`) si se lee o escribe disco
  en el hilo principal, si se olvida cerrar un cursor o archivo, o si se comparte una URI sin permiso
  temporal. Solo registra; no cierra la app. Algunos avisos vienen de librerías al arrancar
  (WorkManager abre su base de datos), no de la app.
- WorkManager sin servicio en primer plano detiene un trabajo a los ~10 minutos. Como cada lote se
  guarda al momento, el escaneo continúa donde quedó cuando Android lo reanuda.

## Accesibilidad

- Todas las acciones tienen etiqueta para TalkBack; las miniaturas anuncian nombre, tamaño y estado
  (seleccionada, en la papelera, sugerida). En Deslizar, la tarjeta tiene acciones personalizadas
  **Conservar** y **Eliminar** (menú de acciones de TalkBack), además de los botones visibles.
- Las opciones de Ajustes se anuncian como botón de opción o interruptor, y los títulos de sección
  como encabezados (se puede saltar entre ellos con TalkBack).
- Zonas táctiles de al menos 48 dp.
- Texto grande: los botones crecen en alto en vez de cortar el texto y, con fuente ≥ 150 %, Inicio
  muestra las tarjetas en una columna.
- La selección no depende solo del color: hay casilla visible y borde.

## Estructura

```
app/src/main/java/com/example/cleanup/
  CleanupApp.kt, AppContainer.kt, MainActivity.kt
  ui/          tema, navegación, componentes y pantallas
  domain/      modelos y reglas puras (p. ej. ScanPlanner, SwipeResume)
  data/media/  permisos, consultas a MediaStore y miniaturas del sistema
  data/db/     Room: entidades, DAOs y base de datos (esquema exportado en app/schemas/)
  data/repo/   repositorios que consume la interfaz (y AppDataCleaner para "Borrar datos de la app")
  detection/   huellas exactas, firmas visuales de fotos y videos, agrupación y sugerencias
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

Tipos de análisis:

| Cuándo | Qué hace |
|---|---|
| Automático (al abrir la app o al detectar cambios) | Incremental: solo lo nuevo o modificado. |
| Botón **Analizar** | Relee todo el catálogo (segundos), reintenta archivos que fallaron y reutiliza huellas válidas. Corrige archivos que se leyeron mientras otra app los estaba guardando. |
| Ajustes → **Volver a analizar todo** | Además borra las huellas (exactas, visuales y de video) y las recalcula desde cero. |

Fases del escaneo: catálogo → huellas exactas (duplicados) → firmas visuales de fotos → firmas de
videos → agrupación.

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

## Capturas, fotos de chat y fotos similares

**Capturas de pantalla**: se reconocen sumando señales (carpeta "Screenshots"/"Capturas" +3, nombre
"Screenshot…"/"Captura…" +2, dimensiones iguales a la pantalla del teléfono +2, PNG +1; umbral 3), sin
depender de una sola convención. Las capturas idénticas aparecen en Duplicados (marcadas como
capturas), las parecidas en "Capturas similares" y el resto en "Otras capturas".

**Fotos de chat**: se infieren por la carpeta (WhatsApp, Telegram, Signal, Messenger) o por el nombre
típico de WhatsApp (`IMG-AAAAMMDD-WA…`). La pantalla indica que es una inferencia.

**Fotos similares** (no idénticas):

1. Para cada foto se calculan, sobre la miniatura del sistema (~256 px, ya orientada), un **pHash**
   (DCT de 32×32 → 64 bits), un **dHash** de 64 bits y un **dHash de 256 bits**, además de un indicador
   de imagen casi uniforme y la **nitidez** (varianza del laplaciano). Todo en luminancia: dos fotos con
   colores parecidos pero distinta estructura no se parecen.
2. Los duplicados exactos se cuentan como una sola foto (ya tienen su categoría).
3. Fotos: se comparan las tomadas cerca en el tiempo (ventana según el nivel) exigiendo pHash **y**
   dHash bajo el umbral, más una pasada global muy estricta (pHash ≤ 3) para copias casi idénticas de
   otra fecha, indexada por fragmentos de 16 bits para no comparar todas contra todas.
4. Capturas: todas contra todas con el hash de 256 bits, porque la interfaz de la app hace que dos
   capturas distintas del mismo chat tengan hashes de 64 bits muy parecidos.
5. Agrupación por **enlace completo**: un elemento entra a un grupo solo si se parece a todos sus
   miembros, así no se forman cadenas A~B~C con A y C distintas. Las imágenes casi uniformes no se agrupan.
6. Nada se preselecciona. La "sugerida" (la más nítida) es solo una ayuda; "Seleccionar todas menos la
   sugerida" solo selecciona, y el usuario confirma con "Enviar a la papelera".

Niveles (Ajustes → Fotos y videos similares; distancias de Hamming):

| Nivel | Fotos (pHash / dHash / ventana) | Capturas (dHash 256 / pHash) |
|---|---|---|
| Más estricto | 6 / 8 / 2 h | 12 / 10 |
| Normal | 10 / 12 / 6 h | 20 / 14 |
| Más amplio | 14 / 16 / 24 h | 30 / 18 |

Los umbrales son iniciales y deben calibrarse con fotos reales. **Limitaciones conocidas**: recortes
fuertes, rotaciones o filtros intensos pueden no agruparse; fondos muy genéricos (cielo, pared) a veces
quedan fuera porque se marcan como imágenes de poca información.

## Videos similares

Se comparan **secuencias de fotogramas**, nunca un solo fotograma:

1. De cada video se extraen **8 fotogramas** repartidos entre el 10 % y el 90 % de la duración (se evitan
   el principio y el final, que suelen ser negros o temblorosos) con `MediaMetadataRetriever`, usando el
   fotograma clave más cercano (`OPTION_CLOSEST_SYNC`, rápido) escalado a 160 px. A cada fotograma se le
   calcula pHash, dHash e indicador de poca información, igual que a las fotos.
2. Se procesa **un video a la vez** (decodificar video consume memoria) y se guarda cada 4, así el
   análisis se puede interrumpir y continuar. Los videos que no se pueden leer (formato no compatible,
   archivo dañado) se cuentan y se muestran como aviso; "Analizar" los reintenta.
3. Dos videos son similares si: duración compatible, misma proporción de imagen (teniendo en cuenta la
   rotación) y suficientes fotogramas alineados parecidos (pHash **y** dHash bajo el umbral). Los
   fotogramas casi uniformes (fundidos a negro) se ignoran; con menos de 3 fotogramas útiles no se agrupa.
4. Las copias idénticas byte a byte siguen en Duplicados y cuentan como un solo video aquí.
5. Agrupación por enlace completo, como en fotos. Nada se preselecciona; el "sugerido" es el de mayor
   resolución, luego el de mayor tasa de datos (mejor calidad) y luego el más reciente.

| Nivel | pHash / dHash por fotograma | Fotogramas que deben coincidir | Diferencia de duración |
|---|---|---|---|
| Más estricto | 6 / 8 | 85 % | 2 % (mín. 1 s) |
| Normal | 10 / 12 | 75 % | 3 % (mín. 1 s) |
| Más amplio | 14 / 16 | 60 % | 5 % (mín. 2 s) |

**Casos que sí detecta**: el mismo video guardado con otra calidad o resolución (por ejemplo, reenviado
por WhatsApp), dos grabaciones casi iguales de la misma escena. **Limitaciones**: videos recortados al
principio o al final, editados, con cambio de velocidad, con partes reordenadas, con marcas de agua grandes
o girados respecto al original no se reconocen. El primer análisis de una biblioteca con muchos videos
tarda más que el de fotos (cada video se decodifica en 8 puntos).

Historial de la base de datos: v1 catálogo · v2 papelera, deshacer y solicitudes · v3 decisiones de
Deslizar · v4 huellas de contenido y copias elegidas · v5 firmas visuales, grupos de similares y
clasificación · v6 firmas de video (migraciones automáticas generadas por Room a partir de `app/schemas/`).

## Pruebas

| Tipo | Qué cubre | Comando |
|---|---|---|
| Unitarias (JVM) | Permisos por versión, formatos, plan de escaneo, conversión de filas de MediaStore, conciliación de eliminaciones, reanudar en Deslizar, huellas SHA-256, planificación de hashes, sugerencia de copia, clasificador de capturas/chat, firmas perceptuales con imágenes sintéticas, agrupación sin encadenamiento, comparación de videos por secuencias (duración, proporción y rotación, fotogramas uniformes, copias exactas, sugerencia) | `.\gradlew.bat testDebugUnitTest` |
| Instrumentadas | Consultas de Room, escaneo contra el MediaStore real, papelera y deshacer, decisiones de Deslizar, duplicados con archivos reales (copia exacta, cambio al final, cambio en el medio), firmas con miniaturas reales (PNG vs JPEG), migración 1→6 | `.\gradlew.bat connectedDebugAndroidTest` |

**Pruebas manuales de eliminación** (el diálogo de Android no se automatiza porque su texto cambia
según el fabricante):

- [ ] Marcar desde una cuadrícula y desde el visor el mismo archivo → aparece una sola vez.
- [ ] Quitar de la papelera y deshacer.
- [ ] Eliminar y **cancelar** el diálogo de Android → todo sigue en la papelera.
- [ ] Eliminar y **aceptar** → el resultado muestra los eliminados y el espacio verificado; Inicio se actualiza.
- [ ] Borrar desde la galería un archivo que está en la papelera y abrir la papelera → se quita solo.
- [ ] Modo "papelera del sistema" en Ajustes → el resultado dice "movidos" y no promete espacio liberado.

**Pruebas manuales de videos similares** (crear videos de prueba en una prueba automática es poco fiable):

- [ ] Grabar un video, reenviarlo a uno mismo por WhatsApp y guardarlo → aparecen juntos en "Videos similares"; el sugerido es el original (más resolución).
- [ ] Dos videos distintos de la misma duración → no se agrupan.
- [ ] Un video y una copia recortada → no se agrupan (limitación documentada).

**Pruebas manuales de Ajustes, accesibilidad y rendimiento:**

- [ ] Desactivar "Analizar cambios automáticamente", tomar una foto y volver a la app → no se analiza hasta pulsar Analizar.
- [ ] "Borrar datos de la app" → cancelar no cambia nada; confirmar vacía la papelera de revisión y Deslizar, Inicio vuelve a analizar y la galería del teléfono sigue igual.
- [ ] TalkBack activado: recorrer Inicio, una cuadrícula, Duplicados, Similares, Deslizar (acciones Conservar/Eliminar) y la Papelera; todo se anuncia con sentido.
- [ ] Ajustes de Android → tamaño de fuente al máximo: ningún botón corta el texto e Inicio usa una columna.
- [ ] Ejecutar la versión debug con Logcat filtrado por `StrictMode` mientras se navega y se analiza: no debe haber avisos de `com.example.cleanup` en el hilo principal.
- [ ] Biblioteca grande (miles de fotos y algunos videos pesados): el desplazamiento de las cuadrículas es fluido y el escaneo avanza sin bloquear la interfaz.

Las pruebas instrumentadas insertan imágenes propias en `Pictures/CleanupTest` y las borran al terminar.

## Avance por fases

- [x] Fase 0 — Configuración del proyecto
- [x] Fase 1 — Tema, navegación y permisos
- [x] Fase 2 — Catálogo MediaStore, escaneo incremental e Inicio
- [x] Fase 3 — Papelera de revisión y eliminación segura
- [x] Fase 4 — Deslizar fotos y videos
- [x] Fase 5 — Duplicados exactos
- [x] Fase 6 — Capturas, fotos similares y fotos de chat *(pendiente de verificar y calibrar en dispositivo)*
- [x] Fase 7 — Videos similares *(pendiente de verificar en dispositivo)*
- [x] Fase 8 — Ajustes, rendimiento y accesibilidad *(pendiente de verificar en dispositivo)*
- [ ] Fase 9 — Verificación final y APK firmado
