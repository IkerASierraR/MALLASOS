# lora-sim

Cálculo del tiempo en aire LoRa y simulación ALOHA (ensayo E5, tarea T3.6). Se ejecuta con Python 3, sin dependencias, fuera del APK.

```bash
python lora_sim.py
python lora_sim.py --nodos 50 --por-hora 10 --bytes 83 --sf 9
python -m unittest
```

- **Tiempo en aire:** fórmula de Semtech (AN1200.13), con BW 125 kHz, CR 4/5, preámbulo de 8 símbolos, cabecera explícita y CRC. Aplica la optimización de tasa baja si el símbolo dura más de 16 ms.
- **Colisiones:** ALOHA puro con llegadas de Poisson, un canal y un SF, sin efecto captura. La simulación se compara con el valor teórico `exp(-2G)`.
- **Tamaños:** por defecto, un SOS típico de 83 bytes y el máximo de 273 bytes (19 de cabecera y 254 de contenido).

Los 50 nodos y las 10 tramas por hora son valores de ejemplo: ajústalos al escenario del artículo. El resultado no considera ciclo de trabajo, alcance ni regulación local.
