# progress.md: avance de MALLA-SOS

> Archivo vivo. Se actualiza al terminar cada sesión de trabajo, humana o con IA.
> **Última actualización:** 7 nov 2026 · **Fase actual:** app escrita; falta probarla en teléfonos · **Próximo hito:** M1 (15 nov)

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
| 1. Base de la app | T1.1 – T1.12 | 🔄 | Hechas T1.2, T1.3, T1.10 y T1.11; T1.1 a la espera de T0.5; T1.4, T1.5 y T1.7 escritas, sin dos teléfonos reales; falta T1.12 (M1) |
| 2. DTN y prioridad | T2.1 – T2.16 | 🔄 | T2.1 a T2.14 escritas y probadas en emulador con un vecino simulado, sin dos teléfonos reales; T2.15 espera D1; falta T2.16 (M2) |
| 3. Ensayos | T3.1 – T3.7 | 🔄 | T3.6 (herramienta de E5) y T3.7 escritas con pruebas; faltan los parámetros del artículo y todos los ensayos de campo |
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
- [x] Interruptores DTN y cola operativos.
- [x] Generador de tráfico y exportación a CSV operativos (emuladores API 26 y 37).
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
| 7 nov | **Por validar con el equipo:** el fin de la cola se señala con un `SUMMARY` sin contenido; reemplaza al medio cierre TCP usado en T2.8 | Quien vació su cola debe seguir enviando ACK, así que hace falta una señal dentro del canal. La cabecera no cambia, y un `SUMMARY` con contenido queda libre para la mejora P2 |
| 7 nov | La sesión termina cuando se envió y se recibió ese `SUMMARY`; cada lado lo envía tras recibir los ACK de su cola | Así ningún ACK queda sin respuesta al cerrar |
| 7 nov | Si un mensaje agota sus 3 reintentos, el resto de la cola espera al próximo contacto | Con 19 s por mensaje, seguir agotaría la sesión de 30 s en un enlace que ya no responde |
| 7 nov | El ACK final se envía antes que el ACK salto del SOS, sin esperar confirmación, y además queda en la cola del Puesto | Quien espera el ACK salto no puede cerrar antes de recibirlo, y la copia en cola llega a quien lleve copias del SOS |
| 7 nov | `readTimeoutMs` sube de 5 a 10 s | La pausa entre reintentos llega a 7 s y no debe parecer un enlace caído |
| 7 nov | Al llenarse la cola, los ACK propios se descartan después de los ajenos | `plan.md` no los ordena; preservan la purga de copias |
| 7 nov | Un mensaje recibido se guarda como `CARRIED`, con `isOwn=false` | Es lo que muestra "En tránsito" en la pestaña Llevando; `PENDING` queda para lo que espera su salto (T2.2) |
| 7 nov | No se devuelve un mensaje a su origen ni a quien lo entregó | Evita reenvíos inútiles; el receptor los descartaría como duplicados |
| 7 nov | Un SOS mal formado corta la sesión (`LINK_LOST`, `extra=session:malformed`) sin guardarse | Se reutiliza el tratamiento del HELLO; no hay evento propio y es un caso anómalo |
| 7 nov | Tras los `SUMMARY`, cada lado vacía lo pendiente, hace medio cierre TCP y lee hasta el fin de datos del vecino | Sin esto, en modo básico el ACK final se perdía y quedaba un `LINK_LOST` falso que habría contaminado E2 |
| 7 nov | `CREATED` lleva en `extra` el tipo (`SOS` o `NORMAL`) | El análisis necesita separarlos; no cambia el formato del CSV |
| 7 nov | El Panel muestra saltos restantes, no recorridos | El teléfono no conoce los saltos iniciales del origen; `design.md` §4.5 pedía recorridos |
| 7 nov | En API 26 a 28 el CSV solo se comparte, sin copia en Descargas | Escribir allí exigiría un permiso de almacenamiento más |
| 7 nov | `DemoReceiver`, solo en debug, simula un vecino por TCP local | Permite probar `MessageDao` y las pantallas sin Wi-Fi Direct ni dependencias nuevas |
| — | D1: método de corte para E2 | **Pendiente** (antes del 30 nov). Sin implementar: lo decide el equipo |

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

### 2026-11-07 · T2.10 a T2.14, T3.6, T3.7 · Iker (+ IA: Claude Code)

- Hecho: Panel del Puesto, Detalle del SOS, Vecinos, Configuración oculta (7 toques), `CsvExporter`, `TrafficGenerator`, `lora-sim/lora_sim.py` y `analisis/analisis.py`.
- Corregido tras probar en emulador: cierre de sesión en modo básico (ver Decisiones); el aviso de toques movía el pie y los toques siguientes fallaban; la fila del generador no respondía al tocar el texto; contenido bajo la barra de navegación.
- Pruebas: JUnit y Python en verde. Emulador API 26 (sin Wi-Fi Direct) y API 37 (con Wi-Fi Direct, sin vecino): bienvenida, crear SOS, cola, Detalle, Panel, marcar atendido, cambio de modo, tema oscuro en el Panel, CSV, generador y reinicio. `MessageDao` ya se ejecutó con Room real mediante `DemoReceiver`.
- Tras reiniciar en API 37 el servicio arranca a los 30 s aprox. y solo como `connectedDevice`, porque el sistema niega `location` en segundo plano: hay que comprobar en un teléfono si busca pares con la pantalla apagada sin abrir la app.
- Sin probar: dos teléfonos reales, recorrido A→B→C→D, el botón Deshacer, Borrar registro, Buscar ahora con vecinos, el aviso de SOS nuevo con vibración y fuente grande (escala 1.3).
- Pendiente o siguiente paso: T0.3 a T0.6, D1 (T2.15), M1 (T1.12), M2 (T2.16), ensayos de campo y cierre (Fase 4).
- Checkpoint afectado: CP3, parcial.

### 2026-11-07 · T2.2 a T2.6, T2.9 · Iker (+ IA: Claude Code)

- Hecho: ACK salto a salto con esperas de 3 s y reintentos tras 1, 2 y 4 s; modo básico sin espera ni reintentos; respuesta a duplicados; ACK final automático, propagación y purga con lápida; `CLOCK_OFFSET` en cada HELLO; `TtlSweeper` cada 60 s (vencimiento, lápidas y límite de 500). Solo una corrutina escribe en el socket, y la lectura nunca se bloquea al escribir. `insertIfNew` ya no aborta si la lápida se purgó y la fila sigue existiendo. El contador de la notificación solo cuenta SOS y normales.
- Archivos tocados: `app/.../dtn/`, `p2p/`, `service/MallaService.kt`, `data/db/Daos.kt`, `log/EventLogger.kt`, `AppContainer.kt`, `memoria/plan.md`, `CONTINUAR.txt`.
- Pruebas: 95 JUnit en verde; lint sin errores. Con TCP real en localhost: reintentos, modo básico, A→D, A→B→D con purga y estado Entregado en A. Las consultas de `MessageDao` compilan con Room pero siguen sin ejecutarse: no hay pruebas instrumentadas y añadirlas requiere una dependencia nueva.
- `DtnPolicy` no es una clase propia: el modo se resuelve en `MessageExchange.forward`.
- Cada contacto reenvía a cada vecino lo que siga en cola, aunque ya lo tenga (la mejora P2 `SUMMARY` lo evitaría).
- Pendiente o siguiente paso: probar en dos teléfonos (M1); Panel del Puesto y Detalle (T2.10), Vecinos (T2.11), Configuración oculta (T2.12), `TrafficGenerator` (T2.13), `CsvExporter` (T2.14) y D1 (T2.15).
- Checkpoint afectado: CP1 y CP3, sin completar.

### 2026-11-07 · T2.8 · Iker (+ IA: Claude Code)

- Hecho: `MessageExchange` (tras el HELLO, cada lado envía su cola y lee la del vecino a la vez, y cierra su envío al terminar) y `Forwarding` (R6, R7 y R8: orden de salida, vencidos, saltos, destino final y descuento de saltos). `insertIfNew` descarta duplicados con `seen_ids`. `SessionRunner` cierra el socket al cancelar, para que una escritura bloqueada respete el límite de 30 s. Eventos nuevos: `SENT`, `RECEIVED`, `ENQUEUED` en el receptor y `DUPLICATE_DROPPED`.
- Archivos tocados: `app/.../dtn/`, `p2p/`, `data/db/Daos.kt`, `log/EventLogger.kt`, `AppContainer.kt`, `CONTINUAR.txt`.
- Pruebas: 79 JUnit en verde en 6 corridas seguidas; lint sin errores. El intercambio se probó con TCP real en localhost y `MessageStore` en memoria, incluido A→B→D por contactos sucesivos. Sin teléfonos ni emulador: `MessageDao.insertIfNew` y `queued` compilan con Room, pero no se han ejecutado.
- Corregida una carrera ya existente en `ContactSchedulerTest` (el sondeo podía publicar el grupo dos veces).
- Límites conocidos hasta T2.2 y T2.5: sin `ACK_HOP`, cada mensaje se reenvía en cada contacto con un vecino nuevo y su estado no cambia; el destinatario final lo guarda como `CARRIED` y puede seguir reenviándolo, porque aún no genera `ACK_FINAL`.
- Pendiente o siguiente paso: T2.2 (ACK salto a salto) y el resto de T2.4; luego probar en dos teléfonos para cerrar M1 (T1.12).
- Checkpoint afectado: CP1, sin completar.

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
