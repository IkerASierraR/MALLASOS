"""T3.7: une los CSV de los teléfonos, corrige relojes y calcula las pruebas de H1, H2, E1 y E4.

    python analisis.py datos/*.csv

Etiquetas de corrida esperadas (`plan.md` §6): `E2-propuesto-07`, `E3-fifo-12`, `E4-ABD-03`,
`E1-Samsung-GO7-10m-04`. El número final empareja las corridas de E2 y E3.
"""

import csv
import glob
import re
import statistics
import sys
from collections import defaultdict, deque

from scipy.stats import binomtest, wilcoxon

ALPHA = 0.05


def load(paths):
    """Lee los CSV en una lista de eventos, sin repetir un evento exportado dos veces."""
    seen = set()
    events = []
    for path in paths:
        with open(path, newline="", encoding="utf-8") as file:
            for row in csv.DictReader(file):
                key = tuple(row.values())
                if key in seen:
                    continue
                seen.add(key)
                row["ts_ms"] = int(row["ts_ms"])
                events.append(row)
    events.sort(key=lambda e: e["ts_ms"])
    return events


def clock_offsets(events, reference):
    """Desfase de cada reloj respecto a `reference`, a partir de los `CLOCK_OFFSET`.

    `extra` vale reloj del vecino menos reloj local. Se usa la mediana de cada par y se recorre el
    grafo de contactos desde la referencia. Un nodo sin camino hasta ella no aparece.
    """
    samples = defaultdict(list)
    for event in events:
        if event["event"] == "CLOCK_OFFSET" and event["peer_id"] and event["extra"]:
            samples[(event["node_id"], event["peer_id"])].append(int(event["extra"]))
    neighbors = defaultdict(dict)
    for (node, peer), values in samples.items():
        median = statistics.median(values)
        neighbors[node][peer] = median
        neighbors[peer].setdefault(node, -median)
    offsets = {reference: 0}
    pending = deque([reference])
    while pending:
        node = pending.popleft()
        for peer, delta in neighbors[node].items():
            if peer not in offsets:
                offsets[peer] = offsets[node] + delta
                pending.append(peer)
    return offsets


def apply_offsets(events, offsets):
    """Añade `ts_ref`, la hora en el reloj de referencia. Sin desfase conocido queda en `None`."""
    for event in events:
        offset = offsets.get(event["node_id"])
        event["ts_ref"] = None if offset is None else event["ts_ms"] - offset
    return events


def run_number(label):
    match = re.search(r"(\d+)$", label)
    return int(match.group(1)) if match else None


def sos_outcomes(events):
    """Resultado de cada SOS: {corrida: [(uid, entregado, retardo_ms o None), ...]} por creación."""
    created = {}
    delivered = {}
    for event in events:
        uid = event["uid"]
        if event["event"] == "CREATED" and event["extra"] == "SOS":
            created[uid] = event
        elif event["event"] == "DELIVERED_FINAL" and uid not in delivered:
            delivered[uid] = event
    runs = defaultdict(list)
    for uid, start in sorted(created.items(), key=lambda item: item[1]["ts_ms"]):
        end = delivered.get(uid)
        delay = None
        if end is not None and start.get("ts_ref") is not None and end.get("ts_ref") is not None:
            delay = end["ts_ref"] - start["ts_ref"]
        runs[start["run_label"]].append((uid, end is not None, delay))
    return runs


def paired_runs(runs, prefix, first, second):
    """Empareja por número las corridas `prefix-first-N` y `prefix-second-N`."""
    pairs = {}
    for label, outcomes in runs.items():
        match = re.fullmatch(rf"{prefix}-(\w+)-(\d+)", label, re.IGNORECASE)
        if match and match.group(1).lower() in (first, second):
            pairs.setdefault(int(match.group(2)), {})[match.group(1).lower()] = outcomes
    return {n: p for n, p in sorted(pairs.items()) if first in p and second in p}


def mcnemar(pairs, first, second):
    """Prueba exacta de McNemar sobre SOS emparejados por posición en la corrida (H1)."""
    only_first = only_second = both = neither = 0
    for pair in pairs.values():
        for a, b in zip(pair[first], pair[second]):
            if a[1] and b[1]:
                both += 1
            elif a[1]:
                only_first += 1
            elif b[1]:
                only_second += 1
            else:
                neither += 1
    discordant = only_first + only_second
    p = binomtest(only_first, discordant, 0.5).pvalue if discordant else 1.0
    return {
        "pares": both + neither + discordant,
        "ambos": both,
        first: only_first,
        second: only_second,
        "ninguno": neither,
        "p": p,
    }


def wilcoxon_delays(pairs, first, second):
    """Wilcoxon sobre la mediana del retardo de SOS por corrida emparejada (H2)."""
    a, b = [], []
    for pair in pairs.values():
        first_delays = [o[2] for o in pair[first] if o[2] is not None]
        second_delays = [o[2] for o in pair[second] if o[2] is not None]
        if first_delays and second_delays:
            a.append(statistics.median(first_delays))
            b.append(statistics.median(second_delays))
    result = {"pares": len(a), "p": None}
    if a:
        result[f"mediana_{first}"] = statistics.median(a)
        result[f"mediana_{second}"] = statistics.median(b)
    differences = [x - y for x, y in zip(a, b)]
    if any(differences):
        result["p"] = float(wilcoxon(a, b).pvalue)
    return result


def link_timings(events):
    """T0 a T3 de cada grupo formado por un teléfono que inició la conexión (E1)."""
    rows = []
    by_node = defaultdict(list)
    for event in events:
        by_node[event["node_id"]].append(event)
    for node_events in by_node.values():
        t0 = t1 = t2 = None
        for event in node_events:
            name = event["event"]
            if name == "DISCOVERY_START":
                t0, t1 = event["ts_ms"], None
            elif name == "PEER_FOUND" and t0 is not None and t1 is None:
                t1 = event["ts_ms"]
            elif name == "CONNECT_REQUEST":
                t2 = event["ts_ms"]
            elif name == "GROUP_FORMED":
                if None not in (t0, t1, t2):
                    rows.append(
                        {
                            "run_label": event["run_label"],
                            "go_intent": event["go_intent"],
                            "t0_t1": t1 - t0,
                            "t1_t2": t2 - t1,
                            "t2_t3": event["ts_ms"] - t2,
                            "total": event["ts_ms"] - t0,
                        }
                    )
                t0 = t1 = t2 = None
            elif name == "SESSION_END":
                t0 = t1 = t2 = None
    return rows


def main(argv):
    paths = [path for pattern in argv for path in glob.glob(pattern)]
    if not paths:
        sys.exit(__doc__)
    events = load(paths)
    nodes = sorted({e["node_id"] for e in events})
    reference = nodes[0]
    offsets = clock_offsets(events, reference)
    apply_offsets(events, offsets)
    print(f"{len(events)} eventos de {len(nodes)} teléfonos; reloj de referencia {reference}")
    missing = [n for n in nodes if n not in offsets]
    if missing:
        print(f"AVISO: sin CLOCK_OFFSET hasta {missing}; sus retardos no se calculan")

    runs = sos_outcomes(events)
    verdict = lambda p: "n/d" if p is None else ("significativo" if p < ALPHA else "no significativo")

    h1 = mcnemar(paired_runs(runs, "E2", "propuesto", "basico"), "propuesto", "basico")
    print(f"\nH1 (McNemar exacta, bilateral): {h1} -> {verdict(h1['p'])}")

    h2 = wilcoxon_delays(paired_runs(runs, "E3", "prioridad", "fifo"), "prioridad", "fifo")
    print(f"H2 (Wilcoxon, bilateral, ms): {h2} -> {verdict(h2['p'])}")

    print("\nE4 por recorrido:")
    routes = defaultdict(list)
    for label, outcomes in runs.items():
        if label.upper().startswith("E4-"):
            routes[label.rsplit("-", 1)[0]].extend(outcomes)
    for route, outcomes in sorted(routes.items()):
        delays = [o[2] for o in outcomes if o[2] is not None]
        rate = sum(o[1] for o in outcomes) / len(outcomes)
        median = statistics.median(delays) if delays else None
        print(f"  {route}: {len(outcomes)} SOS, entrega {rate:.0%}, mediana {median} ms")

    print("\nE1 por etiqueta (medianas en ms):")
    groups = defaultdict(list)
    for row in link_timings(events):
        groups[(row["run_label"].rsplit("-", 1)[0], row["go_intent"])].append(row)
    for (label, intent), rows in sorted(groups.items()):
        medians = {k: statistics.median(r[k] for r in rows) for k in ("t0_t1", "t1_t2", "t2_t3", "total")}
        print(f"  {label} GO={intent} n={len(rows)}: {medians}")


if __name__ == "__main__":
    main(sys.argv[1:])
