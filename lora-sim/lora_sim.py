"""E5 (T3.6): tiempo en aire LoRa y colisiones ALOHA para un SOS de MALLA-SOS.

    python lora_sim.py
    python lora_sim.py --nodos 50 --por-hora 10 --bytes 60 --sf 9

Es una estimación fuera del APK: no hay hardware LoRa en el MVP (`spec.md` §8).
"""

import argparse
import math
import random

HEADER_BYTES = 19
MAX_SOS_BYTES = HEADER_BYTES + 254
# SOS típico: cabecera, 12 bytes fijos, dos longitudes, 20 de ubicación y 30 de mensaje.
TYPICAL_SOS_BYTES = HEADER_BYTES + 12 + 2 + 20 + 30


def airtime_s(
    payload_bytes,
    sf=7,
    bandwidth_hz=125_000,
    coding_rate=1,
    preamble=8,
    explicit_header=True,
    crc=True,
):
    """Fórmula de Semtech (AN1200.13). `coding_rate` 1 a 4 equivale a 4/5 a 4/8."""
    symbol_s = 2**sf / bandwidth_hz
    # La optimización para tasas bajas es obligatoria si el símbolo dura más de 16 ms.
    low_rate = 1 if symbol_s > 0.016 else 0
    numerator = (
        8 * payload_bytes
        - 4 * sf
        + 28
        + 16 * int(crc)
        - 20 * int(not explicit_header)
    )
    denominator = 4 * (sf - 2 * low_rate)
    payload_symbols = 8 + max(
        math.ceil(numerator / denominator) * (coding_rate + 4), 0
    )
    return (preamble + 4.25) * symbol_s + payload_symbols * symbol_s


def aloha_success(offered_load):
    """Probabilidad teórica de ALOHA puro sin captura: exp(-2G)."""
    return math.exp(-2 * offered_load)


def offered_load(nodes, per_hour, airtime):
    """G: tiempo en aire ofrecido por unidad de tiempo."""
    return nodes * per_hour * airtime / 3600


def simulate_aloha(nodes, per_hour, airtime, hours=200, seed=1):
    """Monte Carlo: llegadas de Poisson; dos tramas que se solapan se pierden ambas.

    Devuelve (tramas, colisionadas). Supone un canal y un SF, sin efecto captura.
    """
    rng = random.Random(seed)
    rate_s = nodes * per_hour / 3600
    duration = hours * 3600
    starts = []
    now = rng.expovariate(rate_s)
    while now < duration:
        starts.append(now)
        now += rng.expovariate(rate_s)
    collided = 0
    for index, start in enumerate(starts):
        hit_previous = index > 0 and start - starts[index - 1] < airtime
        hit_next = index + 1 < len(starts) and starts[index + 1] - start < airtime
        if hit_previous or hit_next:
            collided += 1
    return len(starts), collided


def report(nodes, per_hour, payload_bytes, only_sf=None):
    print(f"{nodes} nodos, {per_hour} tramas/h por nodo, {payload_bytes} bytes, BW 125 kHz, CR 4/5")
    print(f"{'SF':>3} {'aire (ms)':>10} {'G':>8} {'éxito teo.':>11} {'éxito sim.':>11} {'colisiones':>11}")
    for sf in [only_sf] if only_sf else range(7, 13):
        airtime = airtime_s(payload_bytes, sf)
        load = offered_load(nodes, per_hour, airtime)
        frames, collided = simulate_aloha(nodes, per_hour, airtime)
        rate = collided / frames if frames else 0.0
        print(
            f"{sf:>3} {airtime * 1000:>10.1f} {load:>8.4f} "
            f"{aloha_success(load):>11.3f} {1 - rate:>11.3f} {collided:>11}"
        )
    print()


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--nodos", type=int, default=50)
    parser.add_argument("--por-hora", type=float, default=10)
    parser.add_argument("--bytes", type=int)
    parser.add_argument("--sf", type=int, choices=range(7, 13))
    args = parser.parse_args()
    sizes = [args.bytes] if args.bytes else [TYPICAL_SOS_BYTES, MAX_SOS_BYTES]
    for size in sizes:
        report(args.nodos, args.por_hora, size, args.sf)


if __name__ == "__main__":
    main()
