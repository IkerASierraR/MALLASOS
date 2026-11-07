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

Estados: ⬜ pendiente · 🔄 en curso · ✅ hecha · ⛔ bloqueada por teléfonos o datos de campo

✅ significa demostrado con pruebas. 21 de 47 tareas lo están. Una tarea escrita que aún no se ha ejecutado queda en 🔄.

| ID | Estado | Evidencia o motivo |
| --- | --- | --- |
| T0.1 | ✅ | Repositorio creado |
| T0.2 | ✅ | Proyecto compila y abre en emuladores API 26 y 37 |
| T0.3 | 🔄 | Propuesta en `docs/convenciones.md`; falta acuerdo del equipo |
| T0.4 | ⬜ | Lo completa cada integrante |
| T0.5 | ⬜ | Lo confirma Cristhian |
| T0.6 | ⬜ | Lo confirma el docente |
| T1.1 | 🔄 | Código y pruebas listos; espera T0.5 |
| T1.2 | ✅ | Room ejecutado en emulador |
| T1.3 | ✅ | Probado en emulador |
| T1.4 | 🔄 | Búsqueda real en API 37; conexión sin probar |
| T1.5 | ✅ | TCP real en localhost, también en Android |
| T1.6 | 🔄 | Eventos de mensaje y T0 reales; T1 a T3 sin probar |
| T1.7 | ✅ | Notificación y arranque tras reiniciar probados; pantalla apagada se verifica en M1 |
| T1.8 | ✅ | Diálogos reales en API 26 y 37; franja sin permisos en Inicio |
| T1.9 | 🔄 | 4 pantallas en Stitch; las 8 de la app en `docs/design/app/` |
| T1.10 | ✅ | Contraste AA medido en todos los tokens |
| T1.11 | ✅ | Probado en emulador |
| T1.12 | ⛔ | **M1:** necesita dos teléfonos |
| T2.1 | ✅ | JUnit y emulador |
| T2.2 | ✅ | JUnit con TCP real |
| T2.3 | ✅ | JUnit con TCP real |
| T2.4 | ✅ | JUnit y Room en emulador |
| T2.5 | ✅ | JUnit y Room en emulador |
| T2.6 | ✅ | JUnit; el borrado de filas no se ha visto en un dispositivo |
| T2.7 | 🔄 | JUnit; sin grupos reales |
| T2.8 | ✅ | JUnit y emulador; A→B→C→D solo en JUnit |
| T2.9 | 🔄 | Escrita en `PeerContact`, que aún no se ha ejecutado |
| T2.10 | ✅ | Cola, Detalle y Panel probados en emulador |
| T2.11 | 🔄 | Pantalla probada sin vecinos; faltan datos reales |
| T2.12 | ✅ | Probada en emulador |
| T2.13 | ✅ | JUnit y emulador |
| T2.14 | ✅ | Probada en API 26 (compartir) y 37 (Descargas) |
| T2.15 | 🔄 | Implementada y probada; D1 falta confirmar |
| T2.16 | ⛔ | **M2:** necesita tres teléfonos |
| T3.1 | ⛔ | Necesita teléfonos |
| T3.2 | ⛔ | E1: necesita teléfonos |
| T3.3 | ⛔ | E2: necesita teléfonos |
| T3.4 | ⛔ | E3: necesita teléfonos |
| T3.5 | ⛔ | E4: necesita teléfonos |
| T3.6 | 🔄 | Herramienta y `lora-sim/resultados.md`; falta fijar el escenario |
| T3.7 | ✅ | Scripts probados con datos sintéticos |
| T4.1 | 🔄 | Firma lista; la etiqueta `v1.0` espera a M2 |
| T4.2 | ⛔ | Necesita datos de los ensayos |
| T4.3 | ⛔ | Necesita datos de los ensayos |
| T4.4 | ⬜ | Lo escribe cada integrante |
| T4.5 | 🔄 | Borrador en `docs/trabajo-futuro.md` |
| T4.6 | ⬜ | 18 dic |

## Checkpoints

### CP0: proyecto listo (meta: 9 nov)

- [x] Repositorio creado con `/app`, `/lora-sim` y `/docs`.
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
| 7 nov | D1 **por confirmar con el equipo:** se implementó la opción recomendada, "Cortar tras N bytes", apagada por defecto. Cuenta bytes de mensajes tras el HELLO y actúa en un solo contacto | Iker pidió terminar la app. Es de un solo uso para que la corrida mida el contacto siguiente |
| 7 nov | En modo básico un mensaje ya enviado no vuelve a salir, salvo el ACK final | "No reintenta" (`plan.md` §3). Si saliera en cada contacto, H1 no distinguiría ambos modos; a cambio, en este modo cada copia se reenvía a un solo vecino |
| 7 nov | La espera de formación del grupo sube de 20 a 30 s | Algunos teléfonos piden aceptar la invitación a mano |
| 7 nov | La clave de firma vive fuera del repositorio (`~/.android/mallasos-release.jks`) y `keystore.properties` está ignorado | Una clave en el repositorio permitiría falsificar actualizaciones. Si se pierde, los teléfonos deben desinstalar para actualizar |

## Bloqueos

| Fecha | Tarea | Bloqueo | Quién lo resuelve |
| --- | --- | --- | --- |
| 7 nov | T1.12, T2.16, T3.1 a T3.5 | Hacen falta teléfonos reales: sin ellos no hay M1, M2 ni datos de ensayos, y T4.2 y T4.3 dependen de esos datos | Equipo |

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

### 2026-11-07 · Auditoría contra la memoria · Iker (+ IA: Claude Code)

- Hecho: revisión de `design.md` §7 en las pantallas, contraste AA medido (36 combinaciones), tema oscuro y fuente 1.3 en emulador API 37. Estado por tarea detallado arriba.
- Corregido: el selector de intent marcaba siempre Auto; el aviso de SOS nuevo quedaba oculto; faltaba la franja sin permisos en Inicio; la cola no mostraba Atendido; Vecinos decía Cliente sin nombre; el Panel no resaltaba lo no atendido. `design.md` actualizado (Jost y saltos restantes).
- Añadido: capturas en `docs/design/app/`, `lora-sim/resultados.md`, `docs/trabajo-futuro.md` y `docs/convenciones.md`.
- Pruebas: 120 JUnit en verde; las correcciones se comprobaron en emulador. El aviso se vio, pero la vibración no se puede comprobar en emulador.
- Pendiente o siguiente paso: lo bloqueado requiere teléfonos. El APK final (`v1.0`) espera a M2; `v1.0-prueba1` es anterior a estas correcciones.
- Checkpoint afectado: CP0 y CP3, parciales.

### 2026-11-07 · T2.15, APK de prueba · Iker (+ IA: Claude Code)

- Hecho: corte de enlace, modo básico sin repetir envíos, prueba A→B→C→D y APK de release firmado para instalar en teléfonos.
- Pruebas: 120 JUnit en verde, incluida la diferencia de H1 tras un corte. APK firmado instalado desde cero en emuladores API 26 y 37, con los diálogos reales de permisos y batería, hasta red activa. En debug se probaron también Deshacer y Borrar registro.
- Sin probar: dos teléfonos reales, Buscar ahora con vecinos, aviso de SOS nuevo con vibración y fuente grande (escala 1.3).
- Pendiente o siguiente paso: instalar en dos teléfonos (M1). Revisar con el equipo D1 y el `SUMMARY` vacío.
- Checkpoint afectado: CP3, parcial.

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
