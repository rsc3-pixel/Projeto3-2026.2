"""Recalcula o baseline CRISP-DM e exporta evidências da EST U1.

Na raiz: python dados/evidencias_est_u1.py
Gráficos: Matplotlib (ver requirements-est-u1.txt).
"""

import json
from datetime import date, timedelta
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt

import analise as a
from premissas import JANELA_COLETA_DIAS, SEMENTE


RAIZ = Path(__file__).resolve().parents[1]
DESTINO = RAIZ / "docs" / "img" / "est-u1"
REFERENCIA = date(2026, 10, 7)
COMPONENTES = {
    "CONCENTRADO_PLAQUETAS": "Plaquetas",
    "CONCENTRADO_HEMACIAS": "Hemácias",
    "PLASMA_FRESCO_CONGELADO": "Plasma",
    "CRIOPRECIPITADO": "Crioprecipitado",
}


def arredondar(valor):
    if isinstance(valor, dict):
        return {k: arredondar(v) for k, v in valor.items()}
    return round(valor, 1) if isinstance(valor, float) else valor


def calcular():
    bolsas = a.gerar_bolsas(REFERENCIA)
    reqs = a.gerar_requisicoes(REFERENCIA)
    vencidas = sum(b["vencida"] for b in bolsas)
    return {
        "total_bolsas": len(bolsas),
        "vencidas": vencidas,
        "validas": len(bolsas) - vencidas,
        "por_tipo": a.estoque_por_tipo_sanguineo(bolsas),
        "descarte": a.taxa_descarte_por_componente(bolsas),
        "dispersao": a.dispersao_validade(bolsas),
        "disp_comp": a.dispersao_validade_por_componente(bolsas),
        "criticas7": len(a.bolsas_proximas_do_vencimento(bolsas)),
        "cobertura": a.cobertura_por_tipo(bolsas, reqs),
        "geo_estoque": a.estoque_por_cidade(bolsas),
        "geo_demanda": a.demanda_por_cidade(reqs),
        "total_req": len(reqs),
        "total_demandado": sum(r["total_bolsas"] for r in reqs),
    }


def salvar(fig, nome):
    fig.text(0.02, 0.025,
             "Fonte: dados/analise.py | Dados sintéticos | Referência: 07/10/2026 | Sementes: 2026 e 2027",
             fontsize=8, color="#475569")
    fig.tight_layout(rect=(0, 0.07, 1, 1))
    fig.savefig(DESTINO / nome, dpi=160, facecolor="white")
    plt.close(fig)


def graficos(dados):
    plt.rcParams.update({"font.size": 11, "axes.spines.top": False,
                         "axes.spines.right": False})
    itens = sorted(dados["cobertura"].items(), key=lambda item: item[1]["cobertura"])
    tipos = [tipo.replace("_POS", "+").replace("_NEG", "−") for tipo, _ in itens]
    valores = [d["cobertura"] for _, d in itens]
    fig, ax = plt.subplots(figsize=(10, 6))
    barras = ax.barh(tipos, valores, color=["#b91c1c" if v < 100 else "#0f766e" for v in valores])
    ax.bar_label(barras, labels=[f"{v:.1f}%".replace(".", ",") for v in valores], padding=5,
                 bbox={"facecolor": "white", "edgecolor": "none", "pad": 1})
    ax.axvline(100, color="#334155", linestyle="--", label="Referência: 100%")
    ax.invert_yaxis()
    ax.set(xlim=(0, 145), xlabel="Estoque válido / demanda acumulada (%)",
           title="Cobertura por tipo sanguíneo\nComponentes agregados; sem alocação por compatibilidade")
    ax.legend(loc="upper right")
    salvar(fig, "cobertura-por-tipo.png")

    medidas = [dados["dispersao"]] + [dados["disp_comp"][c] for c in COMPONENTES]
    nomes = ["Geral"] + list(COMPONENTES.values())
    fig, ax = plt.subplots(figsize=(11, 6))
    for deslocamento, chave, rotulo, cor in [
        (-0.25, "media", "Média", "#1d4ed8"),
        (0, "mediana", "Mediana", "#0f766e"),
        (0.25, "desvio", "Desvio padrão amostral", "#b45309"),
    ]:
        valores = [d[chave] for d in medidas]
        barras = ax.bar([i + deslocamento for i in range(len(nomes))], valores,
                        width=0.25, label=rotulo, color=cor)
        ax.bar_label(barras, labels=[f"{v:.1f}".replace(".", ",") for v in valores],
                     padding=3, fontsize=8)
    ax.set_xticks(range(len(nomes)), [f"{nome}\nn = {d['n']}" for nome, d in zip(nomes, medidas)])
    ax.set(ylabel="Dias", ylim=(0, 405), title="Dias restantes até o vencimento\nSomente bolsas válidas na data de referência")
    ax.legend(loc="upper left")
    salvar(fig, "validade-media-desvio.png")


def main():
    dados = calcular()
    baseline = json.loads((RAIZ / "dados" / "resultados.json").read_text(encoding="utf-8"))
    # Confere todas as métricas do snapshot, na precisão publicada (uma casa).
    if arredondar(dados) != arredondar(baseline):
        raise ValueError("Resultados divergem de dados/resultados.json; revisar antes de publicar.")
    DESTINO.mkdir(parents=True, exist_ok=True)
    evidencia = {
        "data_referencia": REFERENCIA.isoformat(),
        "inicio_janela": (REFERENCIA - timedelta(days=JANELA_COLETA_DIAS)).isoformat(),
        "sementes": {"bolsas": SEMENTE, "requisicoes": SEMENTE + 1},
        "validacao": "Todas as métricas coincidem com resultados.json a uma casa decimal.",
        "resultados": dados,
    }
    (DESTINO / "resultados-verificados.json").write_text(
        json.dumps(evidencia, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    graficos(dados)
    print(evidencia["validacao"])
    print("Evidencias geradas em docs/img/est-u1/.")


if __name__ == "__main__":
    main()
