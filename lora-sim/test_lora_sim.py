import unittest

from lora_sim import (
    aloha_success,
    airtime_s,
    offered_load,
    simulate_aloha,
)


class AirtimeTest(unittest.TestCase):
    def test_valor_de_referencia_sf7(self):
        # 13 bytes, SF7, BW 125 kHz, CR 4/5, preámbulo 8, cabecera explícita y CRC.
        self.assertAlmostEqual(airtime_s(13, sf=7) * 1000, 46.336, places=3)

    def test_valor_de_referencia_sf12_usa_tasa_baja(self):
        # Sin la optimización de tasa baja el resultado sería menor.
        self.assertAlmostEqual(airtime_s(13, sf=12) * 1000, 1155.072, places=3)

    def test_sube_con_el_factor_de_dispersion_y_el_tamano(self):
        self.assertLess(airtime_s(50, sf=7), airtime_s(50, sf=8))
        self.assertLess(airtime_s(50, sf=9), airtime_s(200, sf=9))

    def test_un_sf_mayor_cuesta_casi_el_doble(self):
        ratio = airtime_s(100, sf=9) / airtime_s(100, sf=8)
        self.assertGreater(ratio, 1.7)
        self.assertLess(ratio, 2.2)


class AlohaTest(unittest.TestCase):
    def test_carga_ofrecida(self):
        self.assertAlmostEqual(offered_load(3600, 1, 0.1), 0.1)

    def test_sin_carga_no_hay_perdidas(self):
        self.assertEqual(aloha_success(0), 1)

    def test_el_maximo_teorico_de_aloha_puro(self):
        # El caudal G·exp(-2G) alcanza 1/(2e) con G = 0,5.
        self.assertAlmostEqual(0.5 * aloha_success(0.5), 0.184, places=3)

    def test_la_simulacion_sigue_a_la_teoria(self):
        airtime = 1.0
        for load in (0.05, 0.2, 0.5):
            per_hour = load * 3600 / (10 * airtime)
            frames, collided = simulate_aloha(10, per_hour, airtime, hours=300)
            self.assertAlmostEqual(1 - collided / frames, aloha_success(load), delta=0.01)

    def test_la_simulacion_es_repetible(self):
        self.assertEqual(simulate_aloha(5, 60, 0.5, seed=3), simulate_aloha(5, 60, 0.5, seed=3))


if __name__ == "__main__":
    unittest.main()
