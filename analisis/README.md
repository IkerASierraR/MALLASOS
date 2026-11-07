# analisis

Une los CSV exportados por los teléfonos, corrige los relojes y calcula las pruebas de H1 y H2, además de las tablas de E1 y E4 (tarea T3.7). Se ejecuta con Python 3 y `scipy`, fuera del APK.

```bash
pip install -r requirements.txt
python analisis.py datos/*.csv
python -m unittest
```

## Etiquetas de corrida

El script reconoce los ensayos por la etiqueta escrita en la configuración oculta. El número final empareja las corridas.

| Ensayo | Etiqueta | Ejemplo |
| --- | --- | --- |
| E1 | `E1-<grupo>-<n>` | `E1-Samsung-GO7-10m-04` |
| E2 | `E2-propuesto-<n>`, `E2-basico-<n>` | `E2-propuesto-07` |
| E3 | `E3-prioridad-<n>`, `E3-fifo-<n>` | `E3-fifo-12` |
| E4 | `E4-<recorrido>-<n>` | `E4-ABD-03` |

Todos los teléfonos deben usar la misma etiqueta en cada corrida.

## Criterios

- **Entrega:** un SOS cuenta como entregado si hay `DELIVERED_FINAL`. Los SOS se reconocen por `extra=SOS` en `CREATED`.
- **H1:** McNemar exacta sobre SOS emparejados por posición dentro de la corrida.
- **H2:** Wilcoxon sobre la mediana del retardo de cada corrida emparejada. El retardo va de `CREATED` a `DELIVERED_FINAL`.
- **Pruebas bilaterales**, con α = 0,05. El sentido del efecto se lee en los conteos y las medianas.
- **Relojes:** se usa la mediana de `CLOCK_OFFSET` por par de teléfonos. Incluye la demora del HELLO. Un teléfono sin camino de contactos hasta la referencia no aporta retardos.
