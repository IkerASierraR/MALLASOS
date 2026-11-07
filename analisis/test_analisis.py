import csv
import os
import tempfile
import unittest

import analisis

FIELDS = "ts_ms,run_label,node_id,event,uid,peer_id,hops_left,dtn_mode,queue_mode,go_intent,extra".split(",")


def event(ts, label, node, name, uid="", peer="", extra="", go="AUTO"):
    return {
        "ts_ms": ts,
        "run_label": label,
        "node_id": node,
        "event": name,
        "uid": uid,
        "peer_id": peer,
        "hops_left": "",
        "dtn_mode": "PROPUESTO",
        "queue_mode": "PRIORIDAD",
        "go_intent": go,
        "extra": extra,
    }


def sos_run(label, uid, delivered, created_at=0, delay=1000):
    events = [event(created_at, label, "A", "CREATED", uid, extra="SOS")]
    if delivered:
        events.append(event(created_at + delay, label, "D", "DELIVERED_FINAL", uid))
    return events


class LoadTest(unittest.TestCase):
    def test_une_archivos_y_descarta_exportaciones_repetidas(self):
        with tempfile.TemporaryDirectory() as folder:
            paths = []
            for name in ("a.csv", "a_otra_vez.csv"):
                path = os.path.join(folder, name)
                with open(path, "w", newline="", encoding="utf-8") as file:
                    writer = csv.DictWriter(file, FIELDS)
                    writer.writeheader()
                    writer.writerow(event(5, "E2-basico-01", "A", "SENT"))
                paths.append(path)
            events = analisis.load(paths)
        self.assertEqual(1, len(events))
        self.assertEqual(5, events[0]["ts_ms"])


class ClockTest(unittest.TestCase):
    def test_propaga_el_desfase_por_vecinos_intermedios(self):
        events = [
            event(0, "", "A", "CLOCK_OFFSET", peer="B", extra="100"),
            event(0, "", "B", "CLOCK_OFFSET", peer="C", extra="-30"),
        ]
        self.assertEqual({"A": 0, "B": 100, "C": 70}, analisis.clock_offsets(events, "A"))

    def test_usa_la_mediana_de_cada_par(self):
        events = [
            event(0, "", "A", "CLOCK_OFFSET", peer="B", extra=value)
            for value in ("10", "12", "500")
        ]
        self.assertEqual(12, analisis.clock_offsets(events, "A")["B"])

    def test_el_retardo_se_corrige_con_el_desfase(self):
        events = [
            event(1_000, "E3-fifo-01", "A", "CREATED", "s", extra="SOS"),
            event(5_500, "E3-fifo-01", "D", "DELIVERED_FINAL", "s"),
        ]
        analisis.apply_offsets(events, {"A": 0, "D": 2_000})
        self.assertEqual(
            [("s", True, 2_500)],
            analisis.sos_outcomes(events)["E3-fifo-01"],
        )

    def test_sin_desfase_no_inventa_retardo(self):
        events = sos_run("E3-fifo-01", "s", True)
        analisis.apply_offsets(events, {"A": 0})
        self.assertEqual([("s", True, None)], analisis.sos_outcomes(events)["E3-fifo-01"])


class HypothesisTest(unittest.TestCase):
    def outcomes(self, results):
        events = []
        for number, (proposed, basic) in enumerate(results, start=1):
            events += sos_run(f"E2-propuesto-{number:02}", f"p{number}", proposed)
            events += sos_run(f"E2-basico-{number:02}", f"b{number}", basic)
        analisis.apply_offsets(events, {"A": 0, "D": 0})
        return analisis.sos_outcomes(events)

    def test_ignora_normales_y_corridas_sin_pareja(self):
        events = sos_run("E2-propuesto-01", "x", True)
        events.append(event(0, "E2-propuesto-02", "A", "CREATED", "n", extra="NORMAL"))
        pairs = analisis.paired_runs(analisis.sos_outcomes(events), "E2", "propuesto", "basico")
        self.assertEqual({}, pairs)

    def test_mcnemar_cuenta_discordantes(self):
        runs = self.outcomes([(True, False)] * 8 + [(True, True)] * 3 + [(False, False)] * 2)
        result = analisis.mcnemar(
            analisis.paired_runs(runs, "E2", "propuesto", "basico"), "propuesto", "basico"
        )
        self.assertEqual(13, result["pares"])
        self.assertEqual(8, result["propuesto"])
        self.assertEqual(0, result["basico"])
        # Exacta bilateral con 8 discordantes hacia un lado: 2 · 0,5^8.
        self.assertAlmostEqual(0.0078125, result["p"])

    def test_mcnemar_sin_discordantes_no_es_significativa(self):
        runs = self.outcomes([(True, True)] * 5)
        result = analisis.mcnemar(
            analisis.paired_runs(runs, "E2", "propuesto", "basico"), "propuesto", "basico"
        )
        self.assertEqual(1.0, result["p"])

    def test_wilcoxon_detecta_el_retardo_menor(self):
        events = []
        for number in range(1, 11):
            events += sos_run(f"E3-prioridad-{number:02}", f"p{number}", True, delay=1_000 + number)
            events += sos_run(f"E3-fifo-{number:02}", f"f{number}", True, delay=5_000 + 3 * number)
        analisis.apply_offsets(events, {"A": 0, "D": 0})
        pairs = analisis.paired_runs(analisis.sos_outcomes(events), "E3", "prioridad", "fifo")
        result = analisis.wilcoxon_delays(pairs, "prioridad", "fifo")
        self.assertEqual(10, result["pares"])
        self.assertLess(result["p"], 0.05)
        self.assertLess(result["mediana_prioridad"], result["mediana_fifo"])

    def test_wilcoxon_sin_diferencias_no_da_valor_p(self):
        events = sos_run("E3-prioridad-01", "p", True) + sos_run("E3-fifo-01", "f", True)
        analisis.apply_offsets(events, {"A": 0, "D": 0})
        pairs = analisis.paired_runs(analisis.sos_outcomes(events), "E3", "prioridad", "fifo")
        self.assertIsNone(analisis.wilcoxon_delays(pairs, "prioridad", "fifo")["p"])


class LinkTimingsTest(unittest.TestCase):
    def test_usa_el_ultimo_ciclo_antes_de_conectar(self):
        rows = analisis.link_timings(
            [
                event(0, "E1-x-01", "A", "DISCOVERY_START"),
                event(1_000, "E1-x-01", "A", "DISCOVERY_START"),
                event(1_200, "E1-x-01", "A", "PEER_FOUND"),
                event(1_500, "E1-x-01", "A", "CONNECT_REQUEST"),
                event(1_900, "E1-x-01", "A", "GROUP_FORMED", go="7"),
            ]
        )
        self.assertEqual(1, len(rows))
        self.assertEqual(
            {"t0_t1": 200, "t1_t2": 300, "t2_t3": 400, "total": 900},
            {k: rows[0][k] for k in ("t0_t1", "t1_t2", "t2_t3", "total")},
        )
        self.assertEqual("7", rows[0]["go_intent"])

    def test_omite_grupos_que_inicio_el_vecino(self):
        rows = analisis.link_timings(
            [
                event(0, "E1-x-01", "A", "DISCOVERY_START"),
                event(500, "E1-x-01", "A", "GROUP_FORMED"),
            ]
        )
        self.assertEqual([], rows)


if __name__ == "__main__":
    unittest.main()
