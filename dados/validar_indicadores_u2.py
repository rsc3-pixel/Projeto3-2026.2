"""Calcula os indicadores da EST U2 sobre dados_sinteticos.json e grava o esperado para o backend.

Na raiz: python dados/validar_indicadores_u2.py

Serve de referencia independente para IndicadorServico (PI3-103 e PI3-32): o teste
IndicadorServicoDadosSinteticosTest carrega o mesmo dataset no servico Java e confere,
numero a numero, com o arquivo gerado aqui (dados/indicadores_u2_esperado.json).
So biblioteca padrao: nada de numpy ou scipy, para o calculo ficar legivel e auditavel.
"""

import json
import math
from collections import defaultdict
from datetime import date, timedelta
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[1]
DADOS = RAIZ / "dados" / "dados_sinteticos.json"
ESPERADO = RAIZ / "dados" / "indicadores_u2_esperado.json"
PAINEL_CPP = RAIZ / "dados" / "painel_consolidado_pi3_32.json"

# Data de referencia implicita no dataset: validade - dias_ate_vencer.
REFERENCIA = date(2026, 9, 17)

GRUPOS = ["A_POS", "A_NEG", "B_POS", "B_NEG", "AB_POS", "AB_NEG", "O_POS", "O_NEG"]
COMPONENTES = ["CONCENTRADO_HEMACIAS", "PLASMA_FRESCO_CONGELADO", "CONCENTRADO_PLAQUETAS", "CRIOPRECIPITADO"]

# Mesma tabela de com.rotavital.compatibilidade.MatrizCompatibilidade: receptor -> doadores aceitos.
HEMACIAS = {
    "O_NEG": {"O_NEG"},
    "O_POS": {"O_NEG", "O_POS"},
    "A_NEG": {"O_NEG", "A_NEG"},
    "A_POS": {"O_NEG", "O_POS", "A_NEG", "A_POS"},
    "B_NEG": {"O_NEG", "B_NEG"},
    "B_POS": {"O_NEG", "O_POS", "B_NEG", "B_POS"},
    "AB_NEG": {"O_NEG", "A_NEG", "B_NEG", "AB_NEG"},
    "AB_POS": set(GRUPOS),
}
PLASMA = {
    "O_NEG": set(GRUPOS),
    "O_POS": set(GRUPOS),
    "A_NEG": {"A_NEG", "A_POS", "AB_NEG", "AB_POS"},
    "A_POS": {"A_NEG", "A_POS", "AB_NEG", "AB_POS"},
    "B_NEG": {"B_NEG", "B_POS", "AB_NEG", "AB_POS"},
    "B_POS": {"B_NEG", "B_POS", "AB_NEG", "AB_POS"},
    "AB_NEG": {"AB_NEG", "AB_POS"},
    "AB_POS": {"AB_NEG", "AB_POS"},
}


def compativel(doador, receptor, componente):
    tabela = PLASMA if componente in ("PLASMA_FRESCO_CONGELADO", "CRIOPRECIPITADO") else HEMACIAS
    return doador in tabela[receptor]


def r1(valor):
    """Arredonda como o Math.round do Java (meio para cima), a uma casa."""
    return math.floor(valor * 10.0 + 0.5) / 10.0


def r2(valor):
    return math.floor(valor * 100.0 + 0.5) / 100.0


def d(texto):
    return date.fromisoformat(texto)


# ---------------------------------------------------------------------------
# Distribuicoes (mesma recorrencia de com.rotavital.estatistica.ModeloDemanda)
# ---------------------------------------------------------------------------

def poisson_cdf(lam, k):
    if k < 0:
        return 0.0
    if lam <= 0:
        return 1.0
    log_termo = -lam
    cdf = math.exp(log_termo)
    for i in range(1, k + 1):
        log_termo += math.log(lam) - math.log(i)
        cdf += math.exp(log_termo)
    return min(cdf, 1.0)


def binomial_negativa_cdf(media, variancia, k):
    if k < 0:
        return 0.0
    if media <= 0:
        return 1.0
    if variancia <= media:
        return poisson_cdf(media, k)
    r = media * media / (variancia - media)
    p = r / (r + media)
    log_1mp = math.log1p(-p)
    log_termo = r * math.log(p)
    cdf = math.exp(log_termo)
    for i in range(1, k + 1):
        log_termo += math.log(i - 1 + r) - math.log(i) + log_1mp
        cdf += math.exp(log_termo)
    return min(cdf, 1.0)


def excedente(cdf):
    return max(0.0, min(1.0, 1.0 - cdf))


def variancia_do_horizonte(serie):
    n = len(serie)
    if n < 2:
        return -1.0
    media = sum(serie) / n
    s2 = sum((v - media) ** 2 for v in serie) / (n - 1)
    return n * s2


def classificar_risco(probabilidade, cobertura):
    def abaixo(limite):
        return cobertura is not None and cobertura < limite
    if probabilidade >= 0.80 or abaixo(70):
        return "CRITICO"
    if probabilidade >= 0.50 or abaixo(90):
        return "ALTO"
    if probabilidade >= 0.20 or abaixo(100):
        return "MODERADO"
    return "BAIXO"


# ---------------------------------------------------------------------------
# Descarte (PI3-103)
# ---------------------------------------------------------------------------

def vencidas_com_demanda_compativel(bolsas, requisicoes):
    itens = [(d(req["data"]), it["componente"], it["tipo_sanguineo"])
             for req in requisicoes for it in req["itens"]]
    evitaveis = set()
    for b in bolsas:
        if not b["vencida"]:
            continue
        coleta, validade = d(b["data_coleta"]), d(b["data_validade"])
        for data, componente, receptor in itens:
            if componente == b["componente"] and coleta <= data <= validade \
                    and compativel(b["tipo_sanguineo"], receptor, componente):
                evitaveis.add(b["codigo"])
                break
    return evitaveis


def resumir_por_componente(bolsas, evitaveis):
    linhas = []
    for comp in COMPONENTES:
        do_comp = [b for b in bolsas if b["componente"] == comp]
        vencidas = [b for b in do_comp if b["vencida"]]
        evitavel = sum(1 for b in vencidas if b["codigo"] in evitaveis)
        taxa = 100.0 * len(vencidas) / len(do_comp) if do_comp else 0.0
        pct_evitavel = 100.0 * evitavel / len(vencidas) if vencidas else 0.0
        linhas.append({"componente": comp, "total": len(do_comp), "vencidas": len(vencidas),
                       "taxa": r1(taxa), "evitavel_fefo": evitavel, "pct_evitavel": r1(pct_evitavel)})
    return sorted(linhas, key=lambda l: -l["taxa"])


def inicio_periodo(data, granularidade):
    if granularidade == "SEMANA":
        return data - timedelta(days=data.weekday())
    return data.replace(day=1)


def fim_periodo(inicio, granularidade):
    if granularidade == "SEMANA":
        return inicio + timedelta(days=6)
    proximo = (inicio.replace(day=28) + timedelta(days=4)).replace(day=1)
    return proximo - timedelta(days=1)


def rotulo_periodo(inicio, granularidade):
    if granularidade == "SEMANA":
        ano, semana, _ = inicio.isocalendar()
        return "%d-W%02d" % (ano, semana)
    return inicio.strftime("%Y-%m")


def descarte_por_periodo(bolsas, evitaveis, granularidade):
    grupos = defaultdict(list)
    for b in bolsas:
        grupos[inicio_periodo(d(b["data_coleta"]), granularidade)].append(b)
    saida = []
    for inicio in sorted(grupos):
        do_periodo = grupos[inicio]
        vencidas = sum(1 for b in do_periodo if b["vencida"])
        saida.append({
            "periodo": rotulo_periodo(inicio, granularidade),
            "inicio": inicio.isoformat(),
            "fim": fim_periodo(inicio, granularidade).isoformat(),
            "total": len(do_periodo),
            "vencidas": vencidas,
            "taxa": r1(100.0 * vencidas / len(do_periodo)),
            "por_componente": resumir_por_componente(do_periodo, evitaveis),
        })
    return saida


# ---------------------------------------------------------------------------
# Desabastecimento (PI3-102 / PI3-32)
# ---------------------------------------------------------------------------

def desabastecimento(bolsas, requisicoes):
    datas = sorted(d(r["data"]) for r in requisicoes)
    inicio, fim = datas[0], datas[-1]
    dias = (fim - inicio).days + 1

    serie = {g: [0] * dias for g in GRUPOS}
    demanda = {g: 0 for g in GRUPOS}
    for req in requisicoes:
        indice = (d(req["data"]) - inicio).days
        for it in req["itens"]:
            serie[it["tipo_sanguineo"]][indice] += it["quantidade"]
            demanda[it["tipo_sanguineo"]] += it["quantidade"]

    estoque = {g: 0 for g in GRUPOS}
    for b in bolsas:
        if not b["vencida"]:
            estoque[b["tipo_sanguineo"]] += 1

    linhas = []
    for g in GRUPOS:
        media = float(demanda[g])
        variancia = variancia_do_horizonte(serie[g])
        if variancia < 0:
            variancia = media
        indice = variancia / media if media > 0 else 1.0
        p_bn = excedente(binomial_negativa_cdf(media, variancia, estoque[g]))
        p_poisson = excedente(poisson_cdf(media, estoque[g]))
        cobertura = 100.0 * estoque[g] / demanda[g] if demanda[g] > 0 else None
        linhas.append({
            "tipo": g,
            "estoque_valido": estoque[g],
            "demanda": demanda[g],
            "variancia": r1(variancia),
            "indice_dispersao": r2(indice),
            "cobertura": r1(cobertura) if cobertura is not None else None,
            "deficit": max(0, demanda[g] - estoque[g]),
            "probabilidade": r1(p_bn * 100.0),
            "probabilidade_poisson": r1(p_poisson * 100.0),
            "risco": classificar_risco(p_bn, cobertura),
            "_p": p_bn,
        })
    linhas.sort(key=lambda l: (-l["_p"], GRUPOS.index(l["tipo"])))
    for l in linhas:
        del l["_p"]
    return {"inicio": inicio.isoformat(), "fim": fim.isoformat(), "dias_observados": dias, "por_tipo": linhas}


# ---------------------------------------------------------------------------

def main():
    base = json.loads(DADOS.read_text(encoding="utf-8"))
    bolsas, requisicoes = base["bolsas"], base["requisicoes"]

    for b in bolsas:
        assert b["vencida"] == (d(b["data_validade"]) < REFERENCIA), "data de referencia nao bate com o dataset"

    evitaveis = vencidas_com_demanda_compativel(bolsas, requisicoes)
    esperado = {
        "data_referencia": REFERENCIA.isoformat(),
        "bolsas": len(bolsas),
        "requisicoes": len(requisicoes),
        "descarte_por_componente": resumir_por_componente(bolsas, evitaveis),
        "descarte_por_periodo": {
            "SEMANA": descarte_por_periodo(bolsas, evitaveis, "SEMANA"),
            "MES": descarte_por_periodo(bolsas, evitaveis, "MES"),
        },
        "desabastecimento": desabastecimento(bolsas, requisicoes),
    }
    ESPERADO.write_text(json.dumps(esperado, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    print("Descarte por componente (referencia %s)" % REFERENCIA)
    for l in esperado["descarte_por_componente"]:
        print("  %-26s %4d total %4d vencidas %6.1f%%  evitavel FEFO %3d (%5.1f%% das vencidas)" % (
            l["componente"], l["total"], l["vencidas"], l["taxa"], l["evitavel_fefo"], l["pct_evitavel"]))

    print("\nDescarte por semana de coleta")
    for l in esperado["descarte_por_periodo"]["SEMANA"]:
        print("  %s (%s a %s) %4d coletadas %4d vencidas %6.1f%%" % (
            l["periodo"], l["inicio"], l["fim"], l["total"], l["vencidas"], l["taxa"]))

    des = esperado["desabastecimento"]
    print("\nDesabastecimento: requisicoes de %s a %s (%d dias)" % (des["inicio"], des["fim"], des["dias_observados"]))
    cpp = {}
    if PAINEL_CPP.exists():
        painel = json.loads(PAINEL_CPP.read_text(encoding="utf-8"))
        cpp = {t["tipo_sanguineo"]: t["probabilidade_desabastecimento"] * 100.0
               for t in painel["painel_consolidado"]["desabastecimento_por_tipo"]}
    print("  %-7s %6s %6s %9s %7s %8s %8s  %s" % ("tipo", "tem", "pedido", "var/media", "P(BN)", "Poisson", "C++", "risco"))
    for l in des["por_tipo"]:
        ref_cpp = "%7.1f%%" % cpp[l["tipo"]] if l["tipo"] in cpp else "    -   "
        print("  %-7s %6d %6d %9.2f %6.1f%% %7.1f%% %s  %s" % (
            l["tipo"], l["estoque_valido"], l["demanda"], l["indice_dispersao"],
            l["probabilidade"], l["probabilidade_poisson"], ref_cpp, l["risco"]))
    print("\nEsperado gravado em %s" % ESPERADO.relative_to(RAIZ))


if __name__ == "__main__":
    main()
