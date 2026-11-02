"""
Testes do simulador de telemetria (PI3-112). Telemetria SIMULADA.

    python3 -m unittest telemetria/test_simulador.py -v

Testam os modelos (temperatura e GPS) sem rede. O ciclo completo contra a API
e verificado rodando o simulador contra a aplicacao local (ver
docs/telemetria-simulada.md).
"""

import random
import statistics
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from simulador import FAIXAS, ModeloTemperatura, posicao_no_trajeto  # noqa: E402


class ModeloTemperaturaTest(unittest.TestCase):

    def test_sem_eventos_fica_dentro_da_faixa(self):
        # Sem porta aberta, o refrigerador mantem a carga na faixa.
        for componente, (minimo, maximo) in FAIXAS.items():
            with self.subTest(componente=componente):
                modelo = ModeloTemperatura(componente, random.Random(7), prob_evento=0.0)
                leituras = [modelo.proxima() for _ in range(2000)]
                self.assertTrue(all(minimo <= t <= maximo for t in leituras),
                                f"{componente}: saiu da faixa sem evento")

    def test_oscila_em_torno_do_centro(self):
        modelo = ModeloTemperatura("CONCENTRADO_HEMACIAS", random.Random(1), prob_evento=0.0)
        leituras = [modelo.proxima() for _ in range(2000)]
        self.assertAlmostEqual(statistics.fmean(leituras), 3.5, delta=0.3)

    def test_evento_de_porta_aberta_gera_excursao_e_volta(self):
        modelo = ModeloTemperatura("CONCENTRADO_HEMACIAS", random.Random(3), prob_evento=1.0)
        modelo.proxima()  # dispara o evento
        modelo.prob_evento = 0.0
        durante = [modelo.proxima() for _ in range(6)]
        self.assertGreater(max(durante), 6.0, "evento deveria passar do limite superior")
        depois = [modelo.proxima() for _ in range(30)]
        self.assertTrue(1.0 <= depois[-1] <= 6.0, "deveria voltar a faixa quando a porta fecha")

    def test_eventos_sao_raros_no_padrao(self):
        modelo = ModeloTemperatura("CONCENTRADO_HEMACIAS", random.Random(11))
        fora = sum(1 for _ in range(5000) if not 1.0 <= modelo.proxima() <= 6.0)
        # ~2% de chance por leitura, cada evento dura 3-6 leituras e mais
        # algumas para voltar: bem menos de um terco fora da faixa.
        self.assertLess(fora / 5000, 0.30)
        self.assertGreater(fora, 0, "com eventos ligados, algum alerta deve aparecer")

    def test_nunca_sai_do_intervalo_plausivel_do_sensor(self):
        # A API recusa (400) leituras fora de -80 a 60.
        modelo = ModeloTemperatura("CONCENTRADO_PLAQUETAS", random.Random(5), prob_evento=1.0)
        self.assertTrue(all(-80 <= modelo.proxima() <= 60 for _ in range(1000)))

    def test_mesma_semente_mesma_sequencia(self):
        a = ModeloTemperatura("PLASMA_FRESCO_CONGELADO", random.Random(42))
        b = ModeloTemperatura("PLASMA_FRESCO_CONGELADO", random.Random(42))
        self.assertEqual([a.proxima() for _ in range(50)], [b.proxima() for _ in range(50)])


class PosicaoNoTrajetoTest(unittest.TestCase):
    ORIGEM = (-8.0476, -34.8770)
    DESTINO = (-8.1100, -34.9100)

    def test_extremos(self):
        self.assertEqual(posicao_no_trajeto(self.ORIGEM, self.DESTINO, 0.0), self.ORIGEM)
        self.assertEqual(posicao_no_trajeto(self.ORIGEM, self.DESTINO, 1.0), self.DESTINO)

    def test_meio_do_caminho(self):
        lat, lon = posicao_no_trajeto(self.ORIGEM, self.DESTINO, 0.5)
        self.assertAlmostEqual(lat, (-8.0476 - 8.1100) / 2, places=6)
        self.assertAlmostEqual(lon, (-34.8770 - 34.9100) / 2, places=6)

    def test_fracao_fora_de_0_a_1_e_limitada(self):
        # Viagem que atrasou: o veiculo fica no destino, nao passa dele.
        self.assertEqual(posicao_no_trajeto(self.ORIGEM, self.DESTINO, 1.7), self.DESTINO)
        self.assertEqual(posicao_no_trajeto(self.ORIGEM, self.DESTINO, -0.2), self.ORIGEM)

    def test_ruido_de_gps_e_da_ordem_de_metros(self):
        rng = random.Random(9)
        lat0, lon0 = posicao_no_trajeto(self.ORIGEM, self.DESTINO, 0.5)
        for _ in range(500):
            lat, lon = posicao_no_trajeto(self.ORIGEM, self.DESTINO, 0.5, rng)
            # 100 m ~ 0,0009 grau: com ruido de 15 m (desvio), 6 desvios ainda cabem.
            self.assertLess(abs(lat - lat0), 0.0009)
            self.assertLess(abs(lon - lon0), 0.0009)


if __name__ == "__main__":
    unittest.main()
