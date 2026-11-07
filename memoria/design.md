# design.md: diseño de la interfaz de MALLA-SOS (para Stitch)

> Guía para generar las 8 pantallas en **Google Stitch** y pasarlas luego a **Jetpack Compose con Material 3**.
> Reglas y datos en `spec.md`; rutas y entidades en `plan.md`.

---

## 1. Principios

1. **Uso bajo estrés.** Botones grandes (alto mínimo de 56 dp, y de 120 dp para el botón SOS), textos cortos y una acción principal por pantalla.
2. **Rojo exclusivo para SOS.** El rojo SOS no se usa para errores, borrar ni alertas genéricas; los errores van en ámbar oscuro o neutro.
3. **El color nunca va solo.** Todo estado lleva **icono + texto + color**.
4. **Funciona sin conexión.** Nada en la interfaz depende de Internet: sin mapas ni fuentes remotas, y con iconos empaquetados.
5. **Solo en español.** Tema claro y oscuro.
6. **Accesibilidad.** Contraste AA, áreas táctiles de 48 dp como mínimo y textos escalables. La descripción para lectores de pantalla repite el estado en texto.

## 2. Estilo visual

### Paleta (tokens)

| Token | Claro | Oscuro | Uso |
| --- | --- | --- | --- |
| `primary` | `#1F4E79` | `#9CC3EE` | Acciones normales, barra superior, enlaces |
| `onPrimary` | `#FFFFFF` | `#0B2540` | |
| `sos` | `#C62828` | `#FF6B66` | **Solo SOS**: botón SOS, etiqueta del tipo SOS, icono SOS |
| `onSos` | `#FFFFFF` | `#3B0000` | |
| `pending` | `#8A5300` | `#FFC56B` | Estado "Pendiente" o "Reintentando" |
| `delivered` | `#2E7D32` | `#7FD48A` | Estado "Entregado" o "Atendido" |
| `carried` | `#3D5A80` | `#A9C1E8` | Estado "En tránsito" o "Llevando" |
| `neutral` | `#5F6368` | `#C4C7CC` | Estado "Vencido", textos secundarios |
| `surface` | `#FAFAFC` | `#121417` | Fondo |
| `surfaceVariant` | `#EEF1F5` | `#1E2227` | Tarjetas |
| `warning` | `#8A5300` | `#FFC56B` | Errores y avisos (no rojo) |

### Tipografía

Roboto, la del sistema, sin fuentes descargadas:

| Uso | Tamaño |
| --- | --- |
| Títulos | 22 sp, semibold |
| Cuerpo | 16 sp |
| Etiquetas | 14 sp, medium |
| Botón SOS | 28 sp, bold |
| Datos técnicos (Vecinos, Configuración) | monoespaciada, 13 sp |

### Forma y espacio

- Esquinas de 12 dp en las tarjetas y de 28 dp en el botón SOS.
- Márgenes laterales de 16 dp y separación base de 8 dp.

### Iconos

Material Symbols (empaquetados):

| Icono | Significado |
| --- | --- |
| `sos` | SOS |
| `schedule` | Pendiente |
| `local_shipping` / `directions_walk` | En tránsito |
| `check_circle` | Entregado |
| `task_alt` | Atendido |
| `hourglass_disabled` | Vencido |
| `wifi_tethering` | Vecinos |
| `router` | Group Owner |
| `cell_tower` (tachado) | Sin red |

## 3. Componentes

| Componente | Descripción | Estados |
| --- | --- | --- |
| `SosButton` | Botón circular o cuadrado grande, `sos`, con el texto "ENVIAR SOS" | normal, presionado, deshabilitado (gris, con texto que explica el motivo) |
| `StatusChip` | Píldora con icono y texto | Pendiente (`schedule`, ámbar) · En tránsito (`directions_walk`, azul) · Entregado (`check_circle`, verde) · Atendido (`task_alt`, verde con borde) · Vencido (`hourglass_disabled`, gris) |
| `TypeBadge` | Etiqueta del tipo de mensaje | SOS (rojo con icono `sos`) · Normal (gris, texto "Prueba") · ACK (gris) |
| `MessageCard` | Tarjeta: tipo de emergencia, personas, ubicación en texto, antigüedad ("hace 3 min"), saltos, `StatusChip` | normal, nueva (borde resaltado), atendida |
| `NeighborRow` | Alias, ID corto, icono de Group Owner o cliente, última vez visto y estado del enlace | buscando, encontrado, conectando, conectado, desconectado |
| `ServiceBanner` | Franja superior: "Red activa · 3 vecinos · 2 en cola" | activo, pausado, sin permisos (ámbar, con un botón para resolverlo) |
| `SegmentedToggle` | Selector de dos o cuatro opciones (Configuración) | — |
| `EmptyState` | Ilustración simple de línea, título y una línea de ayuda | — |

## 4. Pantallas

### 4.1 Bienvenida (`welcome`, solo la primera vez)

- **Objetivo:** dejar el teléfono listo.
- **Diseño:** asistente de 3 pasos con indicador de progreso.
  1. **Alias:** campo de texto de 2 a 20 caracteres, más el ID generado mostrado como `ID: 7A3F·91C2`.
  2. **Modo:** dos tarjetas grandes:
     - "Normal": puedo pedir ayuda y llevar mensajes.
     - "Puesto de coordinación": recibo y confirmo SOS.
  3. **Permisos:** lista con un check por permiso:
     - ubicación;
     - dispositivos cercanos;
     - notificaciones;
     - exención de batería;
     - "Ubicación del sistema encendida".
- **Acción principal:** "Continuar" y, al final, "Activar red".
- **Estados:** permiso denegado (fila ámbar con "Abrir ajustes") y todo listo (check verde).

### 4.2 Inicio (`home`)

- **Objetivo:** pedir ayuda en un toque.
- **Diseño:**
  - **Arriba:** `ServiceBanner` y un chip con el modo actual ("Normal" o "Puesto").
  - **Centro:** `SosButton` gigante.
  - **Debajo, 3 accesos en fila:**
    - "Mis mensajes", con contador;
    - "Vecinos", con contador;
    - "Panel del Puesto", solo en modo Puesto.
  - **Pie:** el texto "MALLA-SOS v1.0 · ID 7A3F·91C2". **7 toques** sobre él abren la Configuración oculta, con el aviso "Faltan 3 toques" a partir del cuarto.
- **Estados:** servicio activo, servicio detenido (banner ámbar con "Reactivar") y sin permisos.

### 4.3 Crear SOS (`sos/new`)

- **Objetivo:** enviar un SOS completo en menos de 20 s.
- **Diseño:**
  1. **Tipo:** cuadrícula de 5 botones grandes con icono:
     - Herido;
     - Atrapado;
     - Médico;
     - Incendio;
     - Otro.
  2. **Personas:** contador con − y + (de 1 a 99).
  3. **Ubicación:**
     - estado del GPS: "GPS: buscando…", "GPS: listo ±12 m" o "Sin GPS";
     - campo obligatorio "¿Dónde estás?" (40 caracteres), con contador de caracteres.
  4. **Mensaje (opcional):** 100 caracteres, con contador. Si se supera el límite en bytes aparece: "Texto demasiado largo (tildes o emojis ocupan más)".
  5. **Destino:** "Cualquier puesto" seleccionado por defecto, con la opción "Elegir teléfono…", que abre una lista de nodos conocidos.
  6. **Información automática:** batería del teléfono.
- **Acción principal:** botón `sos` fijo abajo, "ENVIAR SOS". Se deshabilita mientras falte el tipo o la ubicación en texto, y explica por qué.
- **Después de enviar:** pantalla de confirmación con el texto "SOS guardado. Se enviará al encontrar a alguien cerca", un `StatusChip` "Pendiente" y el botón "Ver mis mensajes".

### 4.4 Mis mensajes / Cola (`queue`)

- **Objetivo:** saber qué pasó con cada mensaje.
- **Diseño:**
  - **Pestañas:** "Míos" (SOS creados aquí) y "Llevando" (de otros).
  - **Lista:** `MessageCard`, ordenada como la cola real. El primer elemento muestra "Siguiente en salir".
  - **Filtro:** chips Pendiente, En tránsito, Entregado y Vencido.
- **Acción principal:** tocar una tarjeta abre el Detalle.
- **Estados vacíos:**
  - Míos: "No has enviado SOS".
  - Llevando: "No llevas mensajes de otros".

### 4.5 Panel del Puesto (`post`, solo en modo Puesto)

- **Objetivo:** ver y atender los SOS recibidos.
- **Diseño:**
  - **Resumen arriba:** "5 SOS · 2 sin atender".
  - **Lista:** primero los no atendidos, luego por antigüedad. Cada `MessageCard` muestra:
    - tipo;
    - personas;
    - ubicación en texto;
    - coordenadas si hay GPS;
    - alias de origen;
    - saltos recorridos;
    - "Recibido hace X".
  - **Etiqueta fija en cada tarjeta:** "Confirmado ✓" (ACK automático).
- **Acción principal:** "Marcar atendido" en cada tarjeta. Cambia la tarjeta a `StatusChip` "Atendido" y se puede deshacer.
- **Estados:**
  - vacío: "Sin SOS recibidos. La red está escuchando.";
  - SOS nuevo: aviso en la parte superior y vibración.

### 4.6 Detalle del SOS (`sos/{uid}`)

- **Objetivo:** ver todos los datos y el recorrido.
- **Diseño:**
  - **Cabecera:** `TypeBadge` SOS, tipo de emergencia y `StatusChip`.
  - **Datos:**
    - personas;
    - ubicación en texto;
    - GPS (latitud y longitud en texto, sin mapa);
    - batería;
    - mensaje;
    - origen (alias e ID);
    - destino;
    - fecha de creación;
    - tiempo de vida restante;
    - saltos restantes.
  - **Línea de tiempo vertical** con los eventos registrados de ese identificador. Por ejemplo:
    - "Creado 10:02";
    - "Entregado a Iker 10:07 (ACK salto)";
    - "Confirmado por Puesto 10:15".
  - **Pie técnico:** identificador único (`7A3F91C2·0012`) en monoespaciada.
- **Acción principal:** en modo Puesto, "Marcar atendido". En los demás modos no hay acción.

### 4.7 Vecinos y conexión (`neighbors`)

- **Objetivo:** ver el estado de Wi-Fi Direct, para E1 y para depurar.
- **Diseño:**
  - **Tarjeta de estado del enlace:**
    - estado: Buscando, Conectando, Conectado o Libre;
    - rol: "Este teléfono es Group Owner" o "Cliente de …";
    - IP: `192.168.49.1`;
    - intent de Group Owner configurado.
  - **Últimos tiempos:** T0 → T1, T1 → T2 y T2 → T3 (en ms), más el total.
  - **Lista "Cerca ahora":** `NeighborRow` de los pares encontrados.
  - **Lista "Conocidos":** nodos vistos antes, con alias, modo y "visto hace X".
- **Acción principal:** "Buscar ahora", que fuerza un ciclo de búsqueda.
- **Estados:**
  - Wi-Fi apagado: tarjeta ámbar con "Activar Wi-Fi";
  - ubicación del sistema apagada: aviso ámbar;
  - sin vecinos: "Nadie cerca. Sigue buscando…".

### 4.8 Configuración oculta (`settings/test`)

- **Objetivo:** controlar los ensayos.
- **Diseño:**
  - **Encabezado:** "Modo pruebas", con aviso neutro: "Cambia el comportamiento de la red".
  - **Controles:**
    1. **Capa DTN:** `SegmentedToggle` con "Propuesta (ACK + reintentos)" o "Básica (sin ACK)". Si se aprueba D1, también "Cortar tras N bytes", con campo numérico y un interruptor.
    2. **Cola:** "Prioridad estricta" u "Orden de llegada".
    3. **Intent de Group Owner:** 0, 7, 15 o Auto.
    4. **Generador de tráfico:** interruptor, más "cada X s" y "tamaño en bytes".
    5. **Registro:** campo "Etiqueta de corrida" (por ejemplo `E2-propuesto-07`), botón "Exportar CSV", contador de eventos y botón "Borrar registro". Borrar pide confirmación y el botón es neutro, no rojo.
  - **Al final:**
    - modo del teléfono (Normal o Puesto), con confirmación al cambiarlo;
    - parámetros por defecto del SOS (tiempo de vida en horas y saltos).
- **Acción principal:** los cambios se aplican al instante. Aparece un aviso "Aplicado: DTN básica".
- **Mientras la configuración no sea la predeterminada:** `ServiceBanner` muestra en Inicio la franja "Modo pruebas activo".

## 5. Navegación

```
Bienvenida ─► Inicio ─┬─► Crear SOS ─► Confirmación ─► Mis mensajes
                      ├─► Mis mensajes ─► Detalle SOS
                      ├─► Vecinos y conexión
                      ├─► Panel del Puesto ─► Detalle SOS   (solo Puesto)
                      └─(7 toques en la versión)─► Configuración oculta
```

- Barra superior con flecha atrás. **No hay barra inferior**, para que Inicio sea lo más simple posible.

## 6. Prompts para Stitch

**Prefijo común** (pegar antes de cada pantalla):

> Mobile Android app, Material 3, Spanish UI, offline emergency messaging app "MALLA-SOS". Light theme. Primary color #1F4E79. Red #C62828 is ONLY for SOS elements, never for errors. Status always shown with icon + text + color (Pendiente amber #8A5300 schedule icon, En tránsito blue #3D5A80, Entregado green #2E7D32 check icon, Vencido gray). Large touch targets, high contrast, Roboto, no maps, no photos.

**Prompts por pantalla:**

1. **Bienvenida:** "3-step onboarding wizard: step 1 alias text field with generated device ID shown below; step 2 two large selectable cards 'Normal' and 'Puesto de coordinación'; step 3 permissions checklist with status per row and 'Activar red' button."
2. **Inicio:** "Home with top status banner 'Red activa · 3 vecinos · 2 en cola', mode chip, huge red circular button 'ENVIAR SOS' centered, three shortcut tiles below (Mis mensajes, Vecinos, Panel del Puesto), small footer text with version and device ID."
3. **Crear SOS:** "Form: 5 large icon buttons grid for emergency type (Herido, Atrapado, Médico, Incendio, Otro), people counter with minus/plus, GPS status line, required text field '¿Dónde estás?' with character counter, optional message field, destination selector defaulting to 'Cualquier puesto', fixed bottom red button 'ENVIAR SOS'."
4. **Mis mensajes:** "Tabs 'Míos' and 'Llevando', filter chips, list of message cards each with type badge, people, location text, time ago, hops, and status chip; first card labeled 'Siguiente en salir'."
5. **Panel del Puesto:** "Coordination dashboard: summary '5 SOS · 2 sin atender', list of SOS cards sorted unattended first, each with type, people, location text, coordinates, sender alias, hops, 'Confirmado ✓' label and 'Marcar atendido' button."
6. **Detalle SOS:** "Detail page: header with SOS badge and status chip, data rows, vertical event timeline (Creado, Entregado a…, Confirmado por Puesto), monospace message ID footer."
7. **Vecinos:** "Technical screen: link status card (state, Group Owner role, IP 192.168.49.1, GO intent), timing row T0→T1→T2→T3 in ms, list 'Cerca ahora' and list 'Conocidos', button 'Buscar ahora'."
8. **Configuración oculta:** "Test settings: header 'Modo pruebas', segmented controls for Capa DTN (Propuesta/Básica), Cola (Prioridad/Orden de llegada), GO intent (0/7/15/Auto), traffic generator switch with interval and size fields, run label field, 'Exportar CSV' button, event counter, neutral 'Borrar registro' button."

Después, regenera cada pantalla en tema oscuro con los tokens de la sección 2.

## 7. Paso de Stitch a Compose

- Stitch genera la referencia visual. **No se copia su HTML.** Las pantallas se reconstruyen en Compose con los tokens de `ui/theme/` y los componentes de la sección 3.
- Se exportan las capturas de Stitch a `/docs/design/` para la revisión y el artículo.
- Revisión de cada pantalla:
  - [ ] Contraste AA.
  - [ ] Rojo solo en elementos SOS.
  - [ ] Cada estado con icono y texto.
  - [ ] Funciona con fuente grande (escala 1.3).
  - [ ] Funciona en tema oscuro.
