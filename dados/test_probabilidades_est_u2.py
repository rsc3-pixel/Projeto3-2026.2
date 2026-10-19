"""Casos de fronteira do evento de déficit e da regra de vencimento."""

import unittest

from probabilidades_est_u2 import analisar_descarte, probabilidade_vencimento, reamostrar

COMP = "CONCENTRADO_HEMACIAS"


def bolsa(vencida=False, dias=0):
    return {"tipo_sanguineo": "A_POS", "componente": COMP,
            "vencida": vencida, "dias_ate_vencer": dias}


def pedido(*quantidades):
    return {"itens": [{"tipo_sanguineo": "A_POS", "componente": COMP,
                       "quantidade": n} for n in quantidades]}


def hemacias_a(resultado):
    return next(r for r in resultado if r["tipo"] == "A_POS" and r["componente"] == COMP)


class ProbabilidadesTest(unittest.TestCase):
    def test_demanda_igual_estoque_nao_e_deficit(self):
        resultado = hemacias_a(reamostrar([bolsa()], [pedido(1)], repeticoes=10))
        self.assertEqual(resultado["probabilidade_deficit"], 0)
        self.assertEqual(resultado["deficit_medio_bolsas"], 0)

    def test_soma_itens_repetidos_e_exclui_bolsa_vencida(self):
        resultado = hemacias_a(reamostrar([bolsa(), bolsa(True, -1)], [pedido(1, 2)], repeticoes=10))
        self.assertEqual(resultado["demanda_original"], 3)
        self.assertEqual(resultado["estoque_valido"], 1)
        self.assertEqual(resultado["probabilidade_deficit"], 1)
        self.assertEqual(resultado["deficit_medio_bolsas"], 2)

    def test_teorica_respeita_limites_inclusivos(self):
        self.assertEqual(probabilidade_vencimento(5, 60), 55 / 61)
        self.assertEqual(probabilidade_vencimento(42, 60), 18 / 61)
        self.assertEqual(probabilidade_vencimento(60, 60), 0)
        self.assertEqual(probabilidade_vencimento(365, 60), 0)

    def test_janela_sete_dias_inclui_zero_e_sete(self):
        resultado = analisar_descarte([bolsa(dias=0), bolsa(dias=7), bolsa(dias=8), bolsa(True, -1)])[0]
        self.assertEqual(resultado["probabilidade_empirica_vencida"], 1 / 4)
        self.assertEqual(resultado["probabilidade_ate_7_dias_dada_valida"], 2 / 3)

    def test_bootstrap_tem_variacao_e_e_reproduzivel(self):
        args = ([bolsa(), bolsa()], [pedido(1), pedido(3)])
        a = reamostrar(*args, repeticoes=1000, semente=1)
        self.assertEqual(a, reamostrar(*args, repeticoes=1000, semente=1))
        # Duas retiradas: apenas (1, 1) não excede o estoque 2; P(D > 2) = 3/4.
        self.assertAlmostEqual(hemacias_a(a)["probabilidade_deficit"], 0.75, delta=0.05)


if __name__ == "__main__":
    unittest.main()
