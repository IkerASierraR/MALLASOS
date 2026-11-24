# Resultados de E5

Salida de `python lora_sim.py`. El tiempo en aire no depende del escenario. Los 50 nodos y las 10 tramas por hora son **valores de ejemplo**: el equipo debe fijarlos para el artículo y repetir el cálculo.

```text
50 nodos, 10 tramas/h por nodo, 83 bytes, BW 125 kHz, CR 4/5
 SF  aire (ms)        G  éxito teo.  éxito sim.  colisiones
  7      148.7   0.0207       0.960       0.960        4012
  8      266.8   0.0370       0.929       0.929        7090
  9      472.1   0.0656       0.877       0.877       12220
 10      862.2   0.1198       0.787       0.787       21222
 11     1888.3   0.2623       0.592       0.591       40709
 12     3448.8   0.4790       0.384       0.386       61218

50 nodos, 10 tramas/h por nodo, 273 bytes, BW 125 kHz, CR 4/5
 SF  aire (ms)        G  éxito teo.  éxito sim.  colisiones
  7      425.2   0.0591       0.889       0.889       11064
  8      748.0   0.1039       0.812       0.814       18569
  9     1332.2   0.1850       0.691       0.690       30879
 10     2418.7   0.3359       0.511       0.510       48774
 11     5328.9   0.7401       0.228       0.226       77134
 12     9674.8   1.3437       0.068       0.068       92838
```

## Lectura

- Cada aumento de SF casi duplica el tiempo en aire.
- En este ejemplo, la entrega por ALOHA cae de 96 % con SF7 a 38 % con SF12 para un SOS típico.
- Un SOS máximo con SF12 ocupa casi 10 s de canal: conviene un formato reducido para LoRa.
