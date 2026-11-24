# Trabajo futuro (borrador de T4.5)

> Borrador para el artículo. Lo revisa Dayan y lo completa con los resultados de los ensayos.

MALLA-SOS es un prototipo TRL 4. Estas líneas quedaron fuera del MVP (`memoria/spec.md` §8).

## LoRa real

Hoy E5 solo estima el tiempo en aire y las colisiones (`lora-sim/`). El siguiente paso es un puente Bluetooth–LoRa que amplíe el alcance entre albergues. Antes hay que resolver:

- **Tamaño:** un SOS máximo ocupa 273 bytes, y varias combinaciones de LoRa no admiten tramas tan largas. Hará falta un formato reducido.
- **Colisiones:** ALOHA puro se degrada pronto con factores de dispersión altos. Conviene evaluar escucha previa o ventanas de transmisión.
- **Regulación:** hay que revisar el ciclo de trabajo y la banda permitidos en Perú.

## Firma de los SOS

La app no autentica al emisor: el grupo Wi-Fi Direct cifra el enlace, pero cualquier teléfono con la app puede crear un SOS o un ACK final falso. Un ACK final falso borraría copias legítimas de la red. La línea propuesta es firmar SOS y ACK finales con claves por dispositivo o por organización, y medir el coste en bytes y en tiempo. El análisis de amenazas de capa 2 y las pruebas con un nodo malicioso van en esta misma línea.

## Consumo de batería

El servicio mantiene un `PARTIAL_WAKE_LOCK` y busca vecinos cada 15 s. No se midió su consumo. Un estudio futuro debería medirlo por modelo y evaluar ciclos de búsqueda adaptativos.

## Mejoras del protocolo

- **`SUMMARY` con contenido:** intercambiar identificadores antes de enviar, para no transmitir lo que el vecino ya tiene.
- **Group Owner:** elegir como dueño del grupo al teléfono con más batería.
- **Filtro de vecinos:** hoy se intenta conectar con cualquier dispositivo Wi-Fi Direct cercano. El descubrimiento de servicios evitaría contactar con impresoras o televisores.
