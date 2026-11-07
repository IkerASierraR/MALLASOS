# plan.md: plan técnico de MALLA-SOS

> Cómo se construye lo definido en `spec.md`. Las tareas están en `tasks.md`.

---

## 1. Stack

| Capa | Elección |
| --- | --- |
| Lenguaje | Kotlin |
| UI | Jetpack Compose, Material 3 y Navigation Compose |
| Diseño de pantallas | Google Stitch: genera las pantallas, que luego se pasan a mano a Compose (ver `design.md`) |
| Persistencia | Room (SQLite) para mensajes, duplicados, nodos y eventos; DataStore para la identidad y la configuración |
| Concurrencia | Corrutinas y Flow |
| Inyección de dependencias | Manual, con un contenedor `AppContainer` (evita Hilt para mantenerlo simple) |
| Red | API `WifiP2pManager` y sockets TCP (`java.net`) |
| Ubicación | `LocationManager` (GPS_PROVIDER), sin Google Play Services, para que funcione sin Internet |
| Pruebas | JUnit para el códec, la cola y la lógica DTN; pruebas de campo E1 a E4 |
| Extensión LoRa | Python 3 en `/lora-sim`, fuera del APK |
| Versionado | GitHub: un repositorio con `/app` y `/lora-sim`, y una rama por módulo |
| Versiones de SDK | `minSdk 26`, `targetSdk 35` (o la más reciente que acepte el equipo) |

## 2. Arquitectura

```
┌──────────────────────── UI (Compose) ─────────────────────────┐
│ Bienvenida · Inicio · Crear SOS · Cola · Panel Puesto ·       │
│ Detalle SOS · Vecinos · Configuración oculta                  │
└──────────────┬─────────────────────────────────▲──────────────┘
               │ acciones                        │ StateFlow
┌──────────────▼──────────── Dominio ────────────┴──────────────┐
│ SosUseCases · QueuePolicy (prioridad/FIFO) · DtnPolicy        │
│ (propuesto/básico) · AckManager · TtlSweeper                  │
└──────────────┬────────────────────────────────────────────────┘
┌──────────────▼──────── MallaService (en primer plano) ─────────┐
│ ContactScheduler ─► WifiDirectManager ─► SessionRunner (TCP)  │
│  descubrir → conectar → HELLO → intercambio → desconectar     │
│ TrafficGenerator (pruebas) · EventLogger                      │
└──────────────┬────────────────────────────────────────────────┘
┌──────────────▼──────────── Datos ─────────────────────────────┐
│ Room: messages · seen_ids · known_nodes · events              │
│ DataStore: identidad, modo, configuración de pruebas          │
│ MessageCodec (binario de 19 bytes + contenido)                │
└───────────────────────────────────────────────────────────────┘
```

### Paquetes

```
app/src/main/java/pe/upt/mallasos/
  data/      (db, dao, entities, datastore)
  codec/     (MessageCodec, PayloadCodec)
  p2p/       (WifiDirectManager, SessionRunner, ContactScheduler)
  dtn/       (DtnPolicy, QueuePolicy, AckManager, TtlSweeper)
  service/   (MallaService, TrafficGenerator)
  log/       (EventLogger, CsvExporter)
  ui/        (screens/*, components/*, theme/)
```

## 3. Ciclo de un contacto

1. **Descubrimiento.** `discoverPeers()` se ejecuta en ciclos de unos 15 s. Se registra `T0`.
2. **Selección.** Cuando aparece un vecino se registra `T1`. Se elige uno que no se haya visto en los últimos 60 s, para evitar repetir siempre el mismo par.
3. **Conexión.** Se llama a `connect()` con `groupOwnerIntent` según la configuración (0, 7, 15 o automático). Se registra `T2`.
4. **Grupo formado.** Llega `WifiP2pInfo` y se registra `T3`.
   - El Group Owner abre un `ServerSocket` en el puerto 8988.
   - El cliente se conecta a `192.168.49.1:8988`.
5. **Sesión TCP bidireccional.**
   1. Cada lado envía un `HELLO` con su ID, alias, modo y reloj en ms.
   2. Cada lado envía su cola en el orden que indica `QueuePolicy`.
   3. Por cada mensaje recibido se devuelve un `ACK_HOP`.
   4. Si el receptor es el destinatario final, genera además un `ACK_FINAL`, que entra a su propia cola con la máxima prioridad y se envía en esa misma sesión, antes que el `ACK_HOP`.
   5. Al vaciar su cola, y tras recibir sus `ACK_HOP`, cada lado envía un `SUMMARY` sin contenido.
6. **Cierre.** Cuando ambos lados han enviado y recibido su `SUMMARY`, cada uno escribe lo pendiente, hace medio cierre TCP y lee hasta el fin de datos del vecino. Entonces, o tras un tiempo máximo de 30 s, se llama a `removeGroup()` y se vuelve a descubrir.

### Reintentos en modo DTN propuesto

- Si no llega el `ACK_HOP` en 3 s, se reenvía el mensaje. Se hacen hasta 3 intentos, con esperas de 1, 2 y 4 s.
- Si la sesión se cae, el mensaje sigue en estado `PENDING` y se intenta en el próximo contacto.

### Modo DTN básico

Es la línea base para H1:
- no espera `ACK_HOP`;
- marca el salto como hecho al escribir el mensaje en el socket;
- no reintenta.

## 4. Formato del mensaje

### Cabecera: 19 bytes, big-endian

| Offset | Bytes | Campo | Notas |
| --- | --- | --- | --- |
| 0 | 1 | versión (4 bits) y tipo (4 bits) | versión = 1 |
| 1 | 1 | prioridad | 0 = control, 1 = SOS, 2 = normal |
| 2 | 4 | ID de origen | |
| 6 | 2 | número de secuencia | Se incrementa por cada mensaje creado. **Identificador único = origen + secuencia (6 bytes)** |
| 8 | 4 | ID de destino | `FFFFFFFF` = cualquier puesto |
| 12 | 4 | marca de tiempo | Segundos Unix al crear el mensaje (`uint32`) |
| 16 | 1 | tiempo de vida | En horas (por defecto 24) |
| 17 | 1 | saltos restantes | Por defecto 5 |
| 18 | 1 | longitud del contenido | De 0 a 255 bytes |

> **Supuesto que hay que validar:** el README lista los campos de la cabecera, pero no sus tamaños. Esta distribución suma exactamente 19 bytes. Cristhian debe confirmarla contra su artículo de la Unidad I antes de cerrar el códec.

### Tipos de mensaje

| Valor | Tipo |
| --- | --- |
| 1 | `SOS` |
| 2 | `NORMAL` |
| 3 | `ACK_HOP` |
| 4 | `ACK_FINAL` |
| 5 | `HELLO` |
| 6 | `SUMMARY`: sin contenido indica el fin de la cola; con contenido queda para la mejora P2 |

### Contenido de un SOS (como máximo 254 bytes)

| Campo | Bytes |
| --- | --- |
| tipo de emergencia | 1 |
| personas | 1 |
| batería (%) | 1 |
| flags (bit 0 = hay GPS) | 1 |
| latitud × 1e7 (`int32`) | 4 |
| longitud × 1e7 (`int32`) | 4 |
| longitud del texto de ubicación + texto UTF-8 | 1 + hasta 80 |
| longitud del texto libre + texto UTF-8 | 1 + hasta 160 |

La interfaz limita los caracteres (40 y 100) y **además** valida los bytes, porque las tildes y los emojis ocupan más de un byte.

### Contenido de los otros tipos

| Tipo | Contenido |
| --- | --- |
| `ACK_HOP` | identificador único confirmado (6 bytes) |
| `ACK_FINAL` | identificador único del SOS (6) + ID de quien lo recibió (4) |
| `HELLO` | ID (4) + modo (1) + reloj en ms (8) + longitud del alias (1) + alias |

Un `ACK_FINAL` tiene su propio identificador y se reenvía por la red como cualquier mensaje.

### Framing sobre TCP

Se leen 19 bytes, se interpreta la longitud y luego se lee el contenido.

## 5. Entidades (Room)

### `messages`

| Campo | Tipo | Nota |
| --- | --- | --- |
| `uid` | TEXT PK | origen + secuencia, en hexadecimal |
| `type`, `priority` | INT | |
| `originId`, `destId`, `seq` | INT | |
| `createdAt` | LONG | |
| `ttlHours`, `hopsLeft` | INT | |
| `payload` | BLOB | |
| `state` | TEXT | `PENDING`, `CARRIED`, `DELIVERED` o `EXPIRED` |
| `isOwn` | BOOL | creado en este teléfono |
| `enqueuedAt` | LONG | para el orden de llegada y el retardo |
| `attempts`, `lastAttemptAt` | INT, LONG | |
| `receivedFrom` | INT? | ID del vecino que lo entregó |
| `attended` | BOOL | solo en el Puesto, marca visual |

### Otras tablas

- **`seen_ids`**: `uid` (PK), `firstSeenAt` y `delivered` (bool). Sirve para descartar duplicados y como lápida de los mensajes ya entregados. Se purga al vencer el tiempo de vida más un margen.
- **`known_nodes`**: `nodeId` (PK), `alias`, `isPuesto`, `lastSeenAt`, `lastRssiOrDistanceNote` (opcional) y `clockOffsetMs`.
- **`events`**: `id`, `tsMs` (reloj local), `runLabel`, `nodeId`, `event`, `uid?`, `peerId?`, `hopsLeft?`, `dtnMode`, `queueMode`, `goIntent` y `extra`.

### DataStore

- Identidad: `nodeId`, `alias`, `mode` (Normal o Puesto) y el contador de secuencia.
- Configuración de pruebas: `dtnMode`, `queueMode`, `goIntent`, `trafficGen` (activo, cada cuántos segundos, tamaño) y `runLabel`.

### Capacidad de la cola

Máximo 500 mensajes. Al llenarse se descartan primero los `NORMAL` más antiguos, luego los SOS ajenos más antiguos y **nunca** los SOS propios que aún no se han entregado.

## 6. Eventos del CSV

Encabezado del archivo:

```
ts_ms,run_label,node_id,event,uid,peer_id,hops_left,dtn_mode,queue_mode,go_intent,extra
```

Eventos:
- **Wi-Fi Direct:** `DISCOVERY_START` (T0), `PEER_FOUND` (T1), `CONNECT_REQUEST` (T2), `GROUP_FORMED` (T3; en `extra` va si este teléfono es Group Owner), `SESSION_END` y `LINK_LOST`.
- **Mensajes:** `CREATED`, `ENQUEUED`, `SENT`, `RETRY`, `ACK_HOP_RX`, `RECEIVED`, `DUPLICATE_DROPPED`, `DELIVERED_FINAL`, `ACK_FINAL_RX`, `PURGED`, `EXPIRED` y `QUEUE_EVICTED`.
- **Reloj:** `CLOCK_OFFSET`, calculado en cada `HELLO`.

El CSV se exporta a `Descargas/MALLA-SOS/` (MediaStore) o se comparte desde la hoja del sistema.

### Relojes

Los retardos entre teléfonos dependen de sus relojes. Para que las mediciones sean válidas:

1. Antes de cada salida, se sincronizan los teléfonos con hora automática (con datos) y luego se activa el modo avión, dejando el Wi-Fi encendido.
2. Cada `HELLO` registra la diferencia de reloj con el vecino (`CLOCK_OFFSET`), para corregir los tiempos en el análisis.

## 7. Servicio y permisos

### Servicio

- `MallaService`: servicio en primer plano con notificación fija ("MALLA-SOS activo · N en cola").
- En API 34 o superior, `foregroundServiceType="connectedDevice|location"`.
- Arranca después de la bienvenida y al reiniciar el teléfono (`BOOT_COMPLETED`).

### Permisos

| Permiso | Para qué |
| --- | --- |
| `ACCESS_FINE_LOCATION` | Necesario para buscar pares en API 26–32 y para el GPS |
| `NEARBY_WIFI_DEVICES` | API 33 o superior |
| `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE`, `INTERNET` | Los sockets locales requieren `INTERNET` |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, `FOREGROUND_SERVICE_LOCATION` | Servicio en primer plano (API 34 o superior) |
| `POST_NOTIFICATIONS` | API 33 o superior |
| `RECEIVE_BOOT_COMPLETED` | Reiniciar el servicio tras apagar el teléfono |

La bienvenida además:
- pide **desactivar la optimización de batería** para la app;
- recuerda mantener la **ubicación del sistema encendida**, porque muchos teléfonos no buscan pares sin ella.

## 8. Seguridad (MVP)

- **Confidencialidad en el enlace:** cifrado WPA2 del grupo Wi-Fi Direct.
- **Integridad básica:** el códec valida la versión, la longitud y los rangos. Los mensajes mal formados se descartan y se registran.
- **Límites contra saturación:** contenido de 255 bytes como máximo, cola de 500 mensajes, sesión de 30 s y un vecino a la vez.
- **Identidad:** un ID propio de la app, porque la dirección MAC cambia por privacidad. La app **no autentica** al emisor.
- **Fuera del MVP:** la firma o el HMAC de los SOS y el análisis de amenazas de capa 2 quedan para la Unidad III (Dayan) como trabajo futuro.
- **Privacidad:** no se envía nada a Internet. El CSV contiene alias y ubicaciones, así que solo se comparte dentro del equipo.

## 9. Pantallas

Hay 8 pantallas. El detalle está en `design.md`.

| # | Pantalla | Ruta | Modo |
| --- | --- | --- | --- |
| 1 | Bienvenida | `welcome` | Todos (solo la primera vez) |
| 2 | Inicio | `home` | Todos |
| 3 | Crear SOS | `sos/new` | Todos |
| 4 | Mis mensajes / Cola | `queue` | Todos |
| 5 | Panel del Puesto | `post` | Puesto |
| 6 | Detalle del SOS | `sos/{uid}` | Todos |
| 7 | Vecinos y conexión | `neighbors` | Todos |
| 8 | Configuración oculta | `settings/test` | Todos (7 toques sobre la versión, en Inicio) |

## 10. Riesgos y mitigaciones

| Riesgo | Mitigación |
| --- | --- |
| La búsqueda de pares es lenta o falla según la marca (de 1 a más de 8 s) | Ciclos de búsqueda repetidos; registrar T0 a T3; prueba piloto antes de E1 |
| El sistema detiene el servicio o la búsqueda en Doze | Exención de batería; servicio en primer plano; en las pruebas, la pantalla queda encendida cuando el protocolo lo permita |
| Diálogos del sistema para aceptar la conexión en algunas marcas | Documentarlo por modelo en `progress.md`; aceptar a mano durante los ensayos |
| Relojes no sincronizados | Procedimiento de sincronización y `CLOCK_OFFSET` en cada HELLO |
| El enlace no se corta igual en cada corrida de E2 | Decisión D1 de `spec.md` (corte tras N bytes) |
| Teléfonos API 26–28 con fallas en `WifiP2pManager` | Probar en ellos primero; si un modelo no sirve, documentarlo y excluirlo |
