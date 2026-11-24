# MALLA-SOS

App Android que envía mensajes de auxilio (SOS) entre teléfonos cercanos, sin Internet ni red celular, mediante Wi-Fi Direct y una capa de mensajería tolerante a interrupciones (DTN).

Proyecto del curso SI-882 Redes y Comunicación de Datos I, UPT, 2026-II.

| Carpeta | Contenido |
| --- | --- |
| `app/` | App Android (Kotlin, Jetpack Compose) |
| `lora-sim/` | Simulación LoRa en Python (fuera del APK) |
| `analisis/` | Unión de CSV y pruebas estadísticas en Python (fuera del APK) |
| `docs/` | Diseño, capturas de la app, convenciones y trabajo futuro |
| `memoria/` | `spec.md`, `plan.md`, `tasks.md`, `progress.md` y `design.md` |

## Compilar

Requiere Android Studio con JDK 17 o superior y el SDK de Android.

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```
