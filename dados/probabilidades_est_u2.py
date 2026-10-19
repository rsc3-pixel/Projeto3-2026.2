"""Análise complementar EST U2 sobre dados sintéticos; biblioteca padrão.

Bootstrap de requisições inteiras contra estoque válido fixo. Não simula
atendimento, compatibilidade, reposição, passagem do tempo ou descarte real.
Execução na raiz: python dados/probabilidades_est_u2.py
"""

import json
import random
from collections import Counter
from datetime import date
from pathlib import Path

from gerar_bolsas import gerar_bolsas
from gerar_requisicoes import gerar_requisicoes
from premissas import COMPONENTES, DISTRIBUICAO_TIPO_SANGUINEO, JANELA_COLETA_DIAS, SEMENTE

REFERENCIA = date(2026, 10, 19)
REPETICOES = 5000
SEMENTE_BOOTSTRAP = 20261019
DESTINO = Path(__file__).resolve().parent / "resultados-probabilidades-est-u2.json"


def probabilidade_vencimento(validade, janela):
    """P(idade > validade), com idade inteira uniforme de 0 a janela inclusive."""
    if validade < 0 or janela < 0:
        raise ValueError("Validade e janela devem ser não negativas.")
    return max(0, janela - validade) / (janela + 1)


def reamostrar(bolsas, requisicoes, repeticoes=REPETICOES, semente=SEMENTE_BOOTSTRAP):
    if not requisicoes or repeticoes < 1:
        raise ValueError("São necessárias requisições e ao menos uma repetição.")
    grupos = [(tipo, comp) for comp in COMPONENTES for tipo in DISTRIBUICAO_TIPO_SANGUINEO]
    # Os totais por componente são diagnósticos agregados; não permitem substituição ABO/Rh.
    chaves = grupos + [("TODOS", comp) for comp in COMPONENTES]
    indice = {chave: i for i, chave in enumerate(chaves)}
    estoque = Counter((b["tipo_sanguineo"], b["componente"])
                      for b in bolsas if not b["vencida"])
    disponivel = [estoque[chave] if chave[0] != "TODOS" else
                  sum(estoque[g] for g in grupos if g[1] == chave[1]) for chave in chaves]
    vetores = []
    observado = [0] * len(chaves)
    for req in requisicoes:
        vetor = Counter()
        for item in req["itens"]:
            for chave in [(item["tipo_sanguineo"], item["componente"]), ("TODOS", item["componente"])]:
                vetor[indice[chave]] += item["quantidade"]
        vetores.append(list(vetor.items()))
        for i, quantidade in vetor.items():
            observado[i] += quantidade
    rng = random.Random(semente)
    faltas = [0] * len(chaves)
    deficit = [0] * len(chaves)
    for _ in range(repeticoes):
        demanda = [0] * len(chaves)
        for _ in requisicoes:
            for i, quantidade in rng.choice(vetores):
                demanda[i] += quantidade
        for i, quantidade in enumerate(demanda):
            falta = max(0, quantidade - disponivel[i])
            faltas[i] += int(falta > 0)
            deficit[i] += falta
    return [{
        "tipo": tipo, "componente": comp,
        "estoque_valido": disponivel[i], "demanda_original": observado[i],
        "cenarios_com_deficit": faltas[i],
        "probabilidade_deficit": faltas[i] / repeticoes,
        "deficit_medio_bolsas": deficit[i] / repeticoes,
    } for i, (tipo, comp) in enumerate(chaves)]


def analisar_descarte(bolsas):
    resultado = []
    for comp, premissa in COMPONENTES.items():
        conjunto = [b for b in bolsas if b["componente"] == comp]
        validas = [b for b in conjunto if not b["vencida"]]
        vencidas = len(conjunto) - len(validas)
        proximas = sum(b["dias_ate_vencer"] <= 7 for b in validas)
        resultado.append({
            "componente": comp, "total": len(conjunto), "vencidas": vencidas,
            "validas": len(validas), "validas_ate_7_dias": proximas,
            "probabilidade_empirica_vencida": vencidas / len(conjunto) if conjunto else None,
            "probabilidade_teorica_vencida": probabilidade_vencimento(
                premissa["validade_dias"], JANELA_COLETA_DIAS),
            "probabilidade_ate_7_dias_dada_valida": proximas / len(validas) if validas else None,
        })
    return resultado


def main():
    bolsas = gerar_bolsas(REFERENCIA)
    requisicoes = gerar_requisicoes(REFERENCIA)
    resultado = {
        "dados_sinteticos": True, "data_referencia": REFERENCIA.isoformat(),
        "sementes": {"bolsas": SEMENTE, "requisicoes": SEMENTE + 1, "bootstrap": SEMENTE_BOOTSTRAP},
        "repeticoes": REPETICOES, "requisicoes_por_cenario": len(requisicoes),
        "metodo": "Bootstrap com reposição de requisições inteiras; estoque válido fixo; déficit quando D > S.",
        "limites": "Sem alocação, substituição ABO/Rh, evolução temporal, reposição, rotas ou temperatura. Vencimento não é descarte realizado.",
        "desabastecimento": reamostrar(bolsas, requisicoes),
        "descarte_por_vencimento": analisar_descarte(bolsas),
    }
    DESTINO.write_text(json.dumps(resultado, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("Resultados gravados em dados/resultados-probabilidades-est-u2.json")
    for linha in resultado["desabastecimento"]:
        if linha["tipo"] == "TODOS" or linha["componente"] == "CONCENTRADO_HEMACIAS":
            print(linha)
    for linha in resultado["descarte_por_vencimento"]:
        print(linha)


if __name__ == "__main__":
    main()
