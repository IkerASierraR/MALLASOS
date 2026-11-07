# progress.md: avance de MALLA-SOS

> Archivo vivo. Se actualiza al terminar cada sesión de trabajo, humana o con IA.
> **Última actualización:** 7 nov 2026 · **Fase actual:** 0 (preparación) · **Próximo hito:** M1 (15 nov)

---

## Instrucciones para la IA (leer primero)

Antes de escribir código:

1. Lee `spec.md`, `plan.md`, `tasks.md` y este archivo. Si es una tarea de interfaz, lee también `design.md`.
2. Trabaja **una sola tarea** de `tasks.md` por sesión. Identifícala por su ID, por ejemplo T1.4.
3. No cambies el formato del mensaje, los nombres de los eventos del CSV ni las reglas R1 a R10 sin registrar una decisión en la sección "Decisiones".
4. No agregues dependencias nuevas: nada de Hilt ni de Google Play Services. Si hace falta una, propónla primero.
5. Escribe pruebas JUnit para el códec, la cola y la lógica DTN.
6. Al terminar, haz lo siguiente:
   - actualiza la tabla de estado;
   - agrega una entrada al registro de sesiones;
   - marca el checkpoint si se cumplió.
7. Si algo bloquea la tarea, regístralo en "Bloqueos" y detente. No inventes soluciones al formato ni al protocolo.

## Estado por tarea

Estados: ⬜ pendiente · 🔄 en curso · ✅ hecha · ⛔ bloqueada

| Fase | Tareas | Estado | Notas |
| --- | --- | --- | --- |
| 0. Preparación | T0.1 – T0.6 | 🔄 | T0.1 y T0.2 hechas; faltan T0.3 a T0.6 |
| 1. Base de la app | T1.1 – T1.12 | 🔄 | Hechas T1.2, T1.3, T1.10 y T1.11; T1.1 a la espera de T0.5; T1.4, T1.5 y T1.7 escritas, sin probar en teléfonos; T1.6 y T1.8 parciales |
| 2. DTN y prioridad | T2.1 – T2.16 | 🔄 | T2.1 hecha; T2.7 escrita, sin probar en teléfonos; T2.10 parcial (solo Cola) |
| 3. Ensayos | T3.1 – T3.7 | ⬜ | E5 puede empezar ya (fuera de la app) |
| 4. Cierre | T4.1 – T4.6 | ⬜ | Entrega: 18 dic |

### Detalle de las tareas en curso

| ID | Resp. | Estado | Rama o PR | Última nota |
| --- | --- | --- | --- | --- |
| — | — | — | — | — |

## Checkpoints

### CP0: proyecto listo (meta: 9 nov)

- [ ] Repositorio creado con `/app`, `/lora-sim` y `/docs`.
- [ ] El proyecto compila y abre en un teléfono con API 26.
- [ ] Cabecera de 19 bytes confirmada (T0.5).
- [ ] Inventario de teléfonos completo.

### CP1 / M1: dos teléfonos hablan (meta: 15 nov)

- [ ] Búsqueda y conexión entre 2 marcas distintas.
- [ ] HELLO intercambiado por TCP; IP del Group Owner 192.168.49.1.
- [ ] Un SOS creado en A aparece en la cola de B.
- [ ] T0 a T3 registrados en la tabla `events`.
- [ ] El servicio en primer plano sigue activo con la pantalla apagada.

### CP2: prueba piloto (meta: antes del 16 nov)

- [ ] Distancias útiles medidas.
- [ ] Umbrales de E1 fijados.
- [ ] Problemas por modelo documentados.

### CP3 / M2: MVP completo (meta: 29 nov)

- [ ] Recorrido A→B→D: entrega, ACK final y purga de copias.
- [ ] Duplicados descartados; "Atendido" funciona en el Puesto.
- [ ] Interruptores DTN y cola operativos.
- [ ] Generador de tráfico y exportación a CSV operativos.
- [ ] Decisión D1 resuelta.
- [ ] Se cumplen los criterios de aceptación de `spec.md` §10.

### CP4: datos de los ensayos (meta: 13 dic)

- [ ] E1 terminado (16–22 nov).
- [ ] E2 y E3: al menos 30 corridas cada uno (30 nov – 6 dic).
- [ ] E4: 20 repeticiones por recorrido (7–13 dic).
- [ ] E5: cálculo y simulación listos (hasta 6 dic).

### CP5: entrega (meta: 18 dic)

- [ ] APK `v1.0` en GitHub.
- [ ] Reportes de pruebas.
- [ ] Artículo final.
- [ ] Artículos de la Unidad III.

## Inventario de teléfonos

| Dueño | Marca y modelo | Android (API) | Wi-Fi Direct OK | Observaciones |
| --- | --- | --- | --- | --- |
| Iker | | | ⬜ | |
| Cristhian | | | ⬜ | |
| Abel | | | ⬜ | |
| Dayan | | | ⬜ | |

## Decisiones

| Fecha | Decisión | Motivo |
| --- | --- | --- |
| 7 nov | Todos los teléfonos son iguales; solo el modo Puesto confirma SOS dirigidos a `FFFFFFFF` | Simplicidad y fidelidad al escenario del albergue |
| 7 nov | El ACK final es automático al recibir; "Atendido" es solo visual | Mediciones H1 y E4 independientes del operador |
| 7 nov | Mensajes normales solo como tráfico de prueba | El MVP se centra en el SOS |
| 7 nov | Servicio en primer plano | El voluntario lleva los mensajes con el teléfono en el bolsillo |
| 7 nov | Solo el cifrado del grupo; la firma de SOS queda como trabajo futuro (Unidad III, Dayan) | Alcance y tiempo |
| 7 nov | Compose, API 26 o superior, Stitch para la interfaz, desarrollo guiado por IA | Elección del equipo |
| 7 nov | La tipografía es Jost ("Fuente 19" de Figma), empaquetada en el APK, en lugar de Roboto | Pedido de Iker; al ir empaquetada sigue sin depender de Internet |
| 7 nov | `compileSdk 37` con `targetSdk 35` | Compose BOM 2026.09.00 exige compilar con 37 |
| 7 nov | `memoria/` guarda los 5 .md; `docs/design/` guarda las capturas de Stitch | Mantener la carpeta de trabajo del equipo |
| 7 nov | Del diseño de Stitch se omiten LoRa, dBm y Ed25519, y el rojo es `#C62828` | Quedan fuera del MVP o contradicen `design.md` |
| 7 nov | `MallaService` retiene un `PARTIAL_WAKE_LOCK` mientras está activo (permiso `WAKE_LOCK`, no listado en `plan.md`) | Criterio 7 de `spec.md`: el intercambio debe seguir con la pantalla apagada |
| 7 nov | Los fallos de sesión se registran como `LINK_LOST` con `extra=session:<causa>` | No hay evento propio y el CSV no admite nombres nuevos sin decisión |
| — | D1: método de corte para E2 | **Pendiente** (antes del 30 nov) |

## Bloqueos

| Fecha | Tarea | Bloqueo | Quién lo resuelve |
| --- | --- | --- | --- |
| — | — | — | — |

## Registro de sesiones

> Plantilla. Copia el bloque por cada sesión, con la más reciente arriba.

```
### AAAA-MM-DD · <ID de tarea> · <responsable> (+ IA: <herramienta>)
- Hecho:
- Archivos tocados:
- Pruebas: (JUnit / en teléfono: modelos)
- Pendiente o siguiente paso:
- Checkpoint afectado:
```

### 2026-11-07 · T1.7, T2.7 · Iker (+ IA: Claude Code)

- Hecho: `ContactScheduler` (ciclo de 15 s, enfriamiento de 60 s, sesión de 30 s, atiende grupos ajenos), `PeerContact` (HELLO y registro en `known_nodes`), `MallaService`, `BootReceiver` y estado real de la red en Inicio. Corregidos el texto negro en modo oscuro y el receptor de Wi-Fi Direct, que en API < 33 perdía el estado inicial.
- Archivos tocados: `app/.../p2p/`, `service/`, `ui/`, `AppContainer.kt`, `MainActivity.kt`, `AndroidManifest.xml`.
- Pruebas: 55 JUnit; en emulador API 30, servicio en primer plano con notificación, wake lock, arranque tras reiniciar y `DISCOVERY_START` cada 15 s. Sin teléfonos reales: no hay búsqueda ni conexión reales, ni pruebas en API 34 o superior.
- Pendiente o siguiente paso: T1.12 requiere pasar mensajes en la sesión (T2.8). Falta el botón de permisos en API 33+ de `NEARBY_WIFI_DEVICES` probado en un teléfono.
- Riesgo: sin filtro por servicio, el planificador intenta conectar con cualquier dispositivo Wi-Fi Direct cercano (impresoras, televisores). El HELLO falla y entra en enfriamiento, pero puede mostrar diálogos en ese equipo.
- Checkpoint afectado: CP1, sin completar.

### 2026-11-07 · T1.4, T1.5 · Iker (+ IA: Claude Code)

- Hecho: `WifiDirectManager` (búsqueda, conexión con intent de Group Owner, grupo y desconexión; eventos T0 a T3, `SESSION_END` y `LINK_LOST`) y `SessionRunner` (servidor y cliente TCP en 8988, framing y HELLO validado). Aún nada los invoca: el ciclo lo arrancarán `MallaService` (T1.7) y `ContactScheduler` (T2.7).
- Archivos tocados: `app/.../p2p/`, `log/EventLogger.kt`, `AndroidManifest.xml`, iconos con XML mal formado.
- Pruebas: 41 JUnit en verde; `SessionRunner` con sockets reales en localhost; lint sin errores. `WifiDirectManager` no se ejecutó: el emulador no tiene Wi-Fi Direct y hacen falta dos teléfonos.
- Pendiente o siguiente paso: T1.7 y T2.7 para probarlo en dos teléfonos y cerrar M1.
- Checkpoint afectado: CP1, sin completar.

### 2026-11-07 · T0.1, T0.2, T1.1–T1.3, T1.10, T1.11, T2.1 · Iker (+ IA: Claude Code)

- Hecho: repositorio y proyecto Android; códec; Room; DataStore; tema con Jost; pantallas Bienvenida, Inicio, Crear SOS y Mis mensajes. Un SOS se guarda en la cola con sus eventos `CREATED` y `ENQUEUED`.
- Archivos tocados: `app/`, `gradle/`, `docs/`, `lora-sim/`, `memoria/design.md`, `memoria/progress.md`.
- Pruebas: 28 JUnit en verde; recorrido completo en emulador Pixel 5 API 30 (sin Wi-Fi Direct ni teléfono real).
- Pendiente o siguiente paso: confirmar la cabecera (T0.5); T1.4 y T1.5 (Wi-Fi Direct y sesión TCP); T1.7 (servicio).
- Checkpoint afectado: CP0, parcial. Falta probarlo en un teléfono con API 26.

### 2026-11-07 · Planificación · equipo + Claude

- Hecho: generados `spec.md`, `plan.md`, `tasks.md`, `progress.md` y `design.md`.
- Siguiente paso: T0.1 (crear el repositorio) y T0.5 (confirmar la cabecera).
