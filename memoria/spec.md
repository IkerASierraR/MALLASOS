# spec.md: MALLA-SOS (MVP)

> Especificación funcional del MVP de la app Android **MALLA-SOS**.
> Documentos relacionados: `plan.md` (cómo se construye), `tasks.md` (qué se hace y quién), `progress.md` (avance) y `design.md` (pantallas para Stitch).
> Curso SI-882 Redes y Comunicación de Datos I, UPT, 2026-II. Entrega final: **18 dic 2026**.

> **Cambio de concepto (24 nov):** la app pasa a ser la malla de teléfonos de la red híbrida del Proyecto Integrador. Hay tres roles (Herido, Ayudante y Rescatista) y el Rescatista es la pasarela hacia LoRa: envía los reportes por Bluetooth LE a una placa ESP32 + LoRa, y otra placa los recibe en el puesto, conectada por serial a una computadora. El formato de los datos LoRa queda pendiente. Las secciones 3 y 5 ya lo reflejan; el resto se irá ajustando.

---

## 1. Visión

Una app Android que permite enviar mensajes de auxilio (SOS) entre teléfonos cercanos **sin Internet ni red celular**:

- Usa **Wi-Fi Direct** para que los teléfonos se conecten directamente entre sí.
- Usa una capa de mensajería **tolerante a interrupciones (DTN)**: guarda, transporta y reenvía cada SOS de teléfono en teléfono hasta que llega a un puesto de coordinación.

La app es además el **instrumento de medición** del proyecto: registra cada evento y permite correr los ensayos E1 a E4 que responden las hipótesis H1 y H2.

## 2. Problema

- En un sismo o huaico en Tacna se cortan la energía y la red celular, o esta se satura.
- Los teléfonos siguen funcionando, pero casi toda la mensajería depende de un servidor en Internet.
- Personas a pocos metros, por ejemplo en un albergue, quedan incomunicadas.
- Un SOS enviado cuando no hay nadie cerca se pierde.

**Escenario delimitado:** albergue o edificio evacuado, recreado en el campus UPT. No se cubre la ciudad.

## 3. Usuarios y roles

Todos los teléfonos ejecutan la misma app. Hay **tres roles**, que se eligen en la bienvenida:

| Rol | Quién lo usa | Puede | Group Owner |
| --- | --- | --- | --- |
| **Herido** | Persona afectada (A) | Crear SOS; llevar y reenviar mensajes de otros; ver su cola y sus vecinos | Intent elegible de 0 a 3 (por defecto 0): casi nunca sostiene el grupo y ahorra batería |
| **Ayudante** | Voluntarios que buscan personas (B, C) | Lo mismo que el Herido | Intent elegible de 0 a 15 (por defecto 7) |
| **Rescatista** | Rescatista o puesto de avanzada (D) | Todo lo anterior, más: **recibir los SOS dirigidos a "cualquier rescatista"** (`FFFFFFFF`), enviar automáticamente su ACK final, ver el Panel del Rescatista, marcar SOS como "Atendido" y **enviar reportes por LoRa** al puesto | Intent 15 fijo. **Crea su propio grupo** y lo mantiene encendido: los demás se unen a él y puede atender a varios a la vez |

Herido y Ayudante siguen el ciclo de contactos (buscar, conectar, intercambiar y salir) y prefieren a los grupos ya abiertos, es decir, a los Rescatistas. El Ayudante y el Rescatista conservan los códigos de HELLO de los antiguos modos Normal (0) y Puesto (1).

**Equipo de pruebas:** no es un rol de la app. Cualquier integrante entra a la **configuración oculta** para cambiar parámetros de los ensayos y exportar el CSV.

### Permisos por modo

| Acción | Herido | Ayudante | Rescatista |
| --- | :-: | :-: | :-: |
| Crear y enviar SOS | ✓ | ✓ | ✓ |
| Llevar y reenviar mensajes ajenos | ✓ | ✓ | ✓ |
| Enviar ACK salto a salto | ✓ | ✓ | ✓ |
| Enviar ACK final a un SOS dirigido a su ID | ✓ | ✓ | ✓ |
| Enviar ACK final a un SOS dirigido a `FFFFFFFF` | ✗ | ✗ | ✓ |
| Ver el Panel del Rescatista y marcar "Atendido" | ✗ | ✗ | ✓ |
| Crear su propio grupo y atender a varios a la vez | ✗ | ✗ | ✓ |
| Enviar reportes por LoRa (pantalla "Conexión LoRa") | ✗ | ✗ | ✓ |
| Ajustar su intent de Group Owner (pantalla Vecinos) | 0–3 | 0–15 | fijo en 15 |
| Entrar a la configuración oculta | ✓ | ✓ | ✓ |

## 4. Flujo principal (camino mínimo)

1. **A** abre la app y crea un SOS. Por defecto va a "cualquier puesto". El SOS **se guarda** en la cola, aunque no haya nadie cerca.
2. El servicio en primer plano busca vecinos continuamente, también con la pantalla apagada.
3. **B** pasa cerca. Ocurre lo siguiente:
   - A y B se conectan por Wi-Fi Direct e intercambian un HELLO.
   - A le pasa sus pendientes, **primero los SOS**.
   - B responde con un ACK salto a salto. A conserva su copia.
4. B se desconecta y **lleva el SOS** consigo.
5. B se encuentra con **D** (modo Puesto) y le entrega el SOS. D hace tres cosas:
   - envía un ACK salto a salto a B;
   - genera un **ACK final**, que se propaga por la red igual que un mensaje;
   - muestra el SOS en el Panel.
6. Cada teléfono que recibe el ACK final **borra su copia** del SOS y lo marca como entregado. A ve su SOS como "Entregado".
7. Opcionalmente, el operador de D marca el SOS como "Atendido". Esta marca es solo visual y local.

Variante: si A elige como destino un teléfono concreto, el ACK final lo envía ese teléfono y no un puesto.

## 5. Reglas del negocio

**R1. Destino**
- Por defecto, el destino es `FFFFFFFF` ("cualquier puesto").
- También se puede elegir un nodo concreto de la lista de nodos conocidos.

**R2. ACK final**
- Lo envía solo el destinatario final.
- Si el destino es `FFFFFFFF`, solo un teléfono en modo Puesto puede enviarlo.
- Se envía **automáticamente al recibir el SOS**, sin intervención humana.

**R3. ACK salto a salto**
- Todo nodo que recibe un mensaje completo y válido responde con un ACK salto a salto, incluso si el mensaje es un duplicado.
- En modo **DTN propuesto**, quien envía da el salto por bueno solo al recibir ese ACK. Si no llega, reintenta.
- En modo **DTN básico**, quien envía da el salto por bueno apenas escribe el mensaje en el socket, sin esperar confirmación.

**R4. Copias**
- Quien reenvía un SOS **conserva su copia** hasta que ocurra una de estas tres cosas:
  - recibe el ACK final;
  - vence el tiempo de vida;
  - la cola se llena; en ese caso se descartan primero los mensajes normales y luego los más antiguos.

**R5. Duplicados**
- El identificador único es el ID de origen (4 bytes) más el número de secuencia (2 bytes).
- Si llega un mensaje con un identificador ya visto, no se guarda de nuevo, pero se responde con su ACK salto a salto.

**R6. Prioridad**
- En modo **prioridad estricta**, el orden de envío es:
  1. mensajes de control (ACK final);
  2. SOS;
  3. mensajes normales.
- En modo **orden de llegada**, los SOS y los normales salen en el orden en que entraron a la cola.

**R7. Tiempo de vida**
- Por defecto es de 24 h desde la creación. Al vencer, el mensaje se descarta y queda registrado como `EXPIRED`.

**R8. Saltos**
- Por defecto, cada SOS admite 5 saltos. Cada reenvío descuenta uno.
- Con 0 saltos restantes, el mensaje no se reenvía, salvo que el vecino sea el destinatario final.

**R9. Rojo reservado**
- El rojo se usa **solo para SOS**.
- Ningún estado se comunica únicamente con color: siempre va acompañado de un icono y un texto.

**R10. Registro**
- Todo evento de red y de mensaje se registra para exportarlo a CSV. Esto aplica siempre, no solo durante las pruebas.

## 6. Contenido de un SOS

| Campo | Regla |
| --- | --- |
| Tipo de emergencia | Obligatorio: Herido, Atrapado, Médico, Incendio u Otro |
| Personas afectadas | Obligatorio, de 1 a 99 (por defecto 1) |
| Ubicación GPS | Automática si hay una posición disponible (sin Internet); si no, se envía vacía |
| Ubicación en texto | **Siempre se pide**, hasta 40 caracteres (por ejemplo, "Pabellón B, piso 2") |
| Texto libre | Opcional, hasta 100 caracteres |
| Batería | Automática (%) |
| Destino | Por defecto, "Cualquier puesto"; opcionalmente, un nodo conocido |
| Tiempo de vida y saltos | 24 h y 5 saltos (configurables solo en la configuración oculta) |

**Identidad del nodo:** el ID de 4 bytes se genera aleatoriamente al instalar la app y nunca vale `FFFFFFFF` ni `00000000`. El alias lo escribe el usuario y tiene entre 2 y 20 caracteres.

## 7. Hipótesis y preguntas que la app debe permitir medir

| Código | Enunciado | Qué necesita la app |
| --- | --- | --- |
| **H1** | Si el enlace se corta durante el envío, la capa DTN propuesta (con ACK y reintentos) entrega más SOS que un DTN básico sin confirmaciones. Se prueba con McNemar, α = 0,05, en al menos 30 corridas. | Interruptor DTN propuesto / básico; registro de entrega por mensaje |
| **H2** | Con tráfico mixto, la prioridad estricta reduce el retardo mediano de los SOS frente al orden de llegada. Se prueba con Wilcoxon, α = 0,05, en al menos 30 corridas. | Interruptor de cola; generador de tráfico normal; marcas de tiempo |
| **PE1** | ¿Cuáles son los tiempos de Wi-Fi Direct (T0 a T3) y su alcance? | Registro de T0 a T3; número para ser Group Owner configurable; pantalla de Vecinos |
| **PE4** | ¿Cómo se comporta la entrega con 1, 2 y 3 saltos? | Reenvío por contactos sucesivos; conteo de saltos en el registro |
| **PE5** | ¿Cuál es el tiempo en aire de LoRa y cuántas colisiones hay? | Se calcula fuera de la app (Python) |

## 8. Alcance del MVP

### Entra

- Conexión Wi-Fi Direct: búsqueda, conexión, Group Owner configurable y desconexión al terminar el intercambio.
- Formato binario común (cabecera de 19 bytes) sobre sockets TCP.
- Capa DTN: cola en Room, ACK salto a salto y final, reintentos, tabla de duplicados, tiempo de vida y saltos.
- Prioridad estricta o por orden de llegada.
- Reenvío multisalto por contactos sucesivos.
- Servicio en primer plano con notificación fija.
- Modos Normal y Puesto.
- 8 pantallas (ver `design.md`).
- Configuración oculta con 5 controles:
  1. DTN propuesto o básico;
  2. cola con prioridad o por orden de llegada;
  3. número para ser Group Owner (0, 7, 15 o automático);
  4. generador de tráfico normal;
  5. exportar CSV.
- Registro de eventos y exportación a CSV.
- Interfaz solo en español, con tema claro y oscuro.

### No entra (trabajo futuro documentado)

- Hardware LoRa y puente Bluetooth–LoRa. E5 se resuelve con un script de Python fuera de la app.
- Firma o autenticación de SOS. Queda para la Unidad III (Dayan). En el MVP solo existe el cifrado propio del grupo Wi-Fi Direct.
- Chat entre personas. Los mensajes normales son solo tráfico de prueba.
- Mapas o cartografía sin conexión.
- Medición de consumo de batería.
- Pruebas con un nodo malicioso.
- iOS y otros idiomas.
- Servidor o sincronización en la nube.

### Versión más pequeña útil

Tres teléfonos (A, B y D en modo Puesto) en los que:

- A crea un SOS sin nadie cerca;
- B lo recoge y lo entrega a D;
- D lo muestra y confirma;
- A ve "Entregado";
- el CSV registra todo el recorrido.

## 9. Restricciones

- **Android 8.0 (API 26) o superior.** Al menos 3 teléfonos de marcas distintas.
- Kotlin con Jetpack Compose. Presupuesto: S/ 0.
- Android no permite que un teléfono esté en dos grupos Wi-Fi Direct a la vez, por eso el multisalto se hace por contactos sucesivos en la capa de aplicación.
- Madurez esperada: TRL 4, es decir, un prototipo validado en un entorno controlado.

## 10. Criterios de aceptación del MVP

1. Un SOS creado sin vecinos se mantiene en la cola tras cerrar y reabrir la app, y tras reiniciar el teléfono.
2. Se completan los recorridos A→D, A→B→D y A→B→C→D por contactos sucesivos.
3. El ACK final de D borra las copias en B y C y marca el SOS de A como "Entregado".
4. Un SOS recibido dos veces aparece una sola vez en el Panel del Puesto.
5. Con prioridad estricta activa, ningún mensaje normal sale antes que un SOS que ya estaba en la cola.
6. El CSV exportado contiene T0 a T3 y todos los eventos de cada mensaje, con la etiqueta de la corrida.
7. El intercambio continúa con la pantalla apagada mientras el servicio está activo.
8. Funciona en los teléfonos del equipo, desde API 26.

## 11. Decisiones abiertas

- **D1. Cómo cortar el enlace en E2 de forma repetible.**
  - Opción recomendada: en la configuración oculta, dentro del bloque DTN, agregar "Cortar enlace tras N bytes", que cierra el socket a mitad de un mensaje.
  - Alternativa: apagar el Wi-Fi del receptor a una señal acordada, que es menos repetible.
  - Debe decidirlo el equipo antes del 30 de noviembre.
- **D2. Confirmar con el docente** que los temas de las Unidades II y III cumplen el punto e de la rúbrica.
