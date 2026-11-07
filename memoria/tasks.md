# tasks.md: tareas de MALLA-SOS

> Lista de trabajo priorizada, basada en `spec.md` y `plan.md`. El avance se marca en `progress.md`.

**Prioridades**
- **P0:** sin esto no hay MVP.
- **P1:** necesario para los ensayos.
- **P2:** mejora.

**Responsables:** I = Iker, C = Cristhian, A = Abel, D = Dayan, T = todo el equipo.

> La interfaz (Stitch y Compose) se asigna a **Dayan**, porque su ensayo E5 queda fuera de la app. Es una propuesta que el equipo puede reasignar.

---

## Fase 0: preparación (7–9 nov)

| ID | Tarea | Resp. | Prio. | Depende de |
| --- | --- | --- | --- | --- |
| T0.1 | Crear el repositorio en GitHub con `/app`, `/lora-sim`, `/docs` (los 5 .md) y `.gitignore` | I | P0 | — |
| T0.2 | Crear el proyecto Android: Kotlin, Compose, `minSdk 26`, paquete `pe.upt.mallasos` | I | P0 | T0.1 |
| T0.3 | Acordar ramas (`main`, `feat/<modulo>`), PR con 1 revisión y convención de commits | T | P0 | T0.1 |
| T0.4 | Inventariar los teléfonos (marca, modelo, versión de Android) en `progress.md` | T | P0 | — |
| T0.5 | Confirmar con Cristhian los tamaños de la cabecera de 19 bytes (`plan.md` §4) | C | P0 | — |
| T0.6 | Confirmar los temas de las Unidades II y III con el docente (D2) | T | P1 | — |

## Fase 1: base de la app (2–15 nov)

| ID | Tarea | Resp. | Prio. | Depende de |
| --- | --- | --- | --- | --- |
| T1.1 | `MessageCodec`: codificar y decodificar la cabecera y los contenidos (SOS, ACK, HELLO), con pruebas JUnit | I + C | P0 | T0.5 |
| T1.2 | Room: entidades `messages`, `seen_ids`, `known_nodes` y `events`, con sus DAOs | C | P0 | T0.2 |
| T1.3 | DataStore: identidad (ID aleatorio distinto de `FFFFFFFF` y `00000000`), alias, modo, secuencia y configuración de pruebas | C | P0 | T0.2 |
| T1.4 | `WifiDirectManager`: búsqueda de pares, conexión con intent de Group Owner, `WifiP2pInfo` y `removeGroup` | I | P0 | T0.2 |
| T1.5 | `SessionRunner`: servidor y cliente TCP en el puerto 8988, framing e intercambio de HELLO | I | P0 | T1.1, T1.4 |
| T1.6 | `EventLogger`: eventos T0 a T3 y eventos de mensaje en la tabla `events` | A + I | P0 | T1.2 |
| T1.7 | `MallaService`: servicio en primer plano con notificación, arranque al iniciar el teléfono y `foregroundServiceType` | I | P0 | T1.4 |
| T1.8 | Permisos según la versión de Android y exención de batería | I | P0 | T0.2 |
| T1.9 | Diseñar las 8 pantallas en Stitch a partir de `design.md` | D | P0 | — |
| T1.10 | Tema de Compose: colores, tipografía, componentes `StatusChip` y `SosButton` | D | P0 | T1.9 |
| T1.11 | Pantallas Bienvenida, Inicio y Crear SOS (con GPS mediante `LocationManager` y validación de bytes) | D | P0 | T1.10, T1.3 |
| T1.12 | **Hito M1:** dos teléfonos se conectan, intercambian HELLO y un SOS de A aparece en B | T | P0 | T1.5, T1.11 |

## Fase 2: capa DTN y prioridad (9–29 nov)

| ID | Tarea | Resp. | Prio. | Depende de |
| --- | --- | --- | --- | --- |
| T2.1 | `QueuePolicy`: prioridad estricta (control, SOS, normal) o por orden de llegada | C | P0 | T1.2 |
| T2.2 | ACK salto a salto: esperar 3 s, hasta 3 reintentos con esperas de 1, 2 y 4 s; estado `PENDING` o `CARRIED` | C | P0 | T1.5 |
| T2.3 | `DtnPolicy` básico: sin ACK y sin reintentos (línea base de H1) | C | P0 | T2.2 |
| T2.4 | Duplicados: `seen_ids`, descartar y responder igualmente con `ACK_HOP` | C | P0 | T1.2 |
| T2.5 | `ACK_FINAL`: generarlo en el destinatario o Puesto, propagarlo y purgar las copias | C + A | P0 | T2.2 |
| T2.6 | `TtlSweeper`: vencer mensajes, purgar lápidas y aplicar el límite de cola de 500 | C | P1 | T1.2 |
| T2.7 | `ContactScheduler`: ciclo de búsqueda, enfriamiento de 60 s por vecino, sesión máxima de 30 s y desconexión | A | P0 | T1.5 |
| T2.8 | Reenvío multisalto: pasar los pendientes al vecino tras el HELLO y descontar saltos | A | P0 | T2.7, T2.1 |
| T2.9 | Registrar `CLOCK_OFFSET` en cada HELLO | A | P1 | T1.5 |
| T2.10 | Pantallas Cola, Detalle del SOS y Panel del Puesto (con "Atendido") | D | P0 | T1.10 |
| T2.11 | Pantalla Vecinos y conexión: pares, Group Owner, estado del enlace, últimos T0 a T3 | D + I | P1 | T1.4 |
| T2.12 | Configuración oculta: 7 toques sobre la versión; los 5 controles y la etiqueta de corrida | D | P1 | T1.3 |
| T2.13 | `TrafficGenerator`: mensajes `NORMAL` cada X segundos y de tamaño configurable | C | P1 | T2.1 |
| T2.14 | `CsvExporter`: exportar a Descargas y compartir; encabezado según `plan.md` §6 | A + I | P1 | T1.6 |
| T2.15 | Resolver D1 (corte repetible para E2) y, si se elige, implementar "cortar tras N bytes" | C | P1 | T2.2 |
| T2.16 | **Hito M2:** recorrido A→B→D completo; ACK final que purga las copias; CSV correcto | T | P0 | T2.5, T2.8, T2.14 |

## Fase 3: ensayos (16 nov – 13 dic)

| ID | Tarea | Resp. | Prio. | Fechas |
| --- | --- | --- | --- | --- |
| T3.1 | Prueba piloto con 2 teléfonos: fijar las distancias y los umbrales de entrega | T | P0 | antes del 16 nov |
| T3.2 | **E1:** T0 a T3 por marca, intent de Group Owner (0/7/15/automático) y distancia (5/10/20 m); 10 repeticiones por combinación | I (+T) | P0 | 16–22 nov |
| T3.3 | **E2 (H1):** DTN propuesto frente a básico con corte; al menos 30 corridas; McNemar | C (+T) | P0 | 30 nov – 6 dic |
| T3.4 | **E3 (H2):** prioridad frente a orden de llegada con tráfico mixto; al menos 30 corridas; Wilcoxon | C (+T) | P0 | 30 nov – 6 dic |
| T3.5 | **E4:** recorridos A→D, A→B→D y A→B→C→D; 20 repeticiones cada uno | A (+T) | P0 | 7–13 dic |
| T3.6 | **E5:** tiempo en aire LoRa con la fórmula de Semtech y simulación ALOHA en `/lora-sim` | D | P0 | 2 nov – 6 dic |
| T3.7 | Scripts de análisis en Python: unir los CSV, corregir relojes y calcular las pruebas | C + A | P1 | 30 nov – 13 dic |

## Fase 4: cierre (7–18 dic)

| ID | Tarea | Resp. | Prio. |
| --- | --- | --- | --- |
| T4.1 | Generar el APK firmado de entrega (`release`) y una etiqueta `v1.0` en GitHub | I | P0 |
| T4.2 | Reportes de pruebas (tablas y gráficos por ensayo) | T | P0 |
| T4.3 | Artículo final del proyecto integrador (APA 7, rúbrica) | T | P0 |
| T4.4 | Artículos individuales de la Unidad III | T | P0 |
| T4.5 | Sección de trabajo futuro: LoRa real, firma de SOS (Dayan) y consumo de batería | D | P1 |
| T4.6 | **Entrega final: 18 dic** | T | P0 |

## Pendientes del README (no son de la app)

- [ ] Completar las referencias [5]–[8] del proyecto integrador.
- [ ] Agregar el correo @upt.pe al artículo de la Unidad II de Iker.
- [ ] Artículos de la Unidad II de Dayan, Abel y Cristhian (exposición: semana del 9 al 15 nov).

## Mejoras P2 (solo si sobra tiempo)

- `SUMMARY`: intercambiar identificadores antes de enviar, para no transmitir lo que el vecino ya tiene.
- Elegir como Group Owner al teléfono con más batería.
- Selector de destino con búsqueda por alias.
