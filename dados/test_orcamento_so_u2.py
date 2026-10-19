import json
import unittest
from decimal import Decimal as D

from orcamento_so_u2 import PASTA, calcular, custo_egress, dinheiro


class OrcamentoTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.precos = json.loads((PASTA / "precos-gcp-so-u2.json").read_text(encoding="utf-8"))

    def test_fronteiras_das_faixas_de_rede(self):
        faixas = self.precos["egress_premium_america_sul"]
        self.assertEqual(custo_egress(D(0), faixas), D(0))
        self.assertEqual(custo_egress(D(1024), faixas), D("194.56"))
        self.assertEqual(custo_egress(D(1025), faixas), D("194.74"))
        self.assertEqual(custo_egress(D(10241), faixas), D("1853.59"))
        with self.assertRaises(ValueError):
            custo_egress(D(-1), faixas)

    def test_backup_inclui_copias_excluidas_sem_duplicar_ativas(self):
        baixo, moderado, alto = calcular(self.precos)["cenarios"]
        self.assertEqual(baixo["backup_soft_delete_gib"], 0)
        self.assertEqual(moderado["backup_ativo_gib"] + moderado["backup_soft_delete_gib"], 50)
        self.assertEqual(alto["backup_ativo_gib"] + alto["backup_soft_delete_gib"], 600)

    def test_totais_preservam_fracoes_de_centavo(self):
        for cenario in calcular(self.precos)["cenarios"]:
            soma = sum(D(i["custo_sem_arredondar_usd"]) for i in cenario["itens"])
            self.assertEqual(cenario["total_usd"], dinheiro(soma))
            self.assertEqual(D(cenario["limite_planejamento_usd"]),
                             D(cenario["total_usd"]) + D(cenario["reserva_15_porcento_usd"]))

    def test_modelo_alto_nao_cobra_ipv4_para_banco_privado(self):
        alto = calcular(self.precos)["cenarios"][2]
        ips = [i for i in alto["itens"] if i["item"] == "IPv4 público em uso"]
        self.assertEqual(len(ips), 1)
        self.assertEqual(ips[0]["quantidade"], "730")
        self.assertEqual(ips[0]["custo_usd"], "3.65")


if __name__ == "__main__":
    unittest.main()
