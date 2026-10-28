"""Orçamento reproduzível com preços consultados, sem acessar conta ou criar recursos.

python dados/orcamento_so_u2.py         # exporta JSON e CSV
python dados/orcamento_so_u2.py --check # confere artefatos sem modificá-los
"""

import argparse
import csv
import io
import json
from decimal import Decimal, ROUND_CEILING, ROUND_HALF_UP
from pathlib import Path

from dimensionamento_so_u2 import calcular as calcular_carga

D = Decimal
PASTA = Path(__file__).resolve().parent


def dinheiro(valor):
    return str(valor.quantize(D("0.01"), rounding=ROUND_HALF_UP))


def custo_egress(gib, faixas):
    if gib < 0:
        raise ValueError("Tráfego não pode ser negativo.")
    total, anterior = D(0), D(0)
    for faixa in faixas:
        limite = D(faixa["ate_gib"]) if faixa["ate_gib"] is not None else gib
        volume = max(D(0), min(gib, limite) - anterior)
        total += volume * D(faixa["usd_por_gib"])
        anterior = limite
        if gib <= limite:
            break
    return total


def calcular(precos):
    cargas = {c["cenario"]: c for c in calcular_carga()["cenarios"]}
    tarifas = precos["tarifas"]
    horas = D(precos["horas_mes"])
    resultados = []
    for cenario in precos["cenarios"]:
        itens = []

        def adicionar(descricao, quantidade, tarifa):
            valor = tarifas[tarifa]
            bruto = D(quantidade) * D(valor["usd"])
            itens.append({"item": descricao, "quantidade": str(quantidade),
                          "unidade": valor["unidade"], "tarifa_usd": valor["usd"],
                          "custo_usd": dinheiro(bruto), "custo_sem_arredondar_usd": str(bruto),
                          "fonte": precos["fontes"][valor["fonte"]]})

        for vm in cenario["vms"]:
            if vm["tipo"] == "e2-micro":
                adicionar("VM e2-micro (host integral)", horas, "e2_micro_hora")
            else:
                adicionar(f"CPU {vm['papel']} ({vm['tipo']})", horas * vm["vcpu"], "e2_custom_cpu_hora")
                adicionar(f"RAM {vm['papel']} ({vm['tipo']})", horas * vm["memoria_gib"], "e2_custom_memoria_hora")
        for tipo in ("standard", "balanced"):
            if cenario[f"disco_{tipo}_gib"]:
                adicionar(f"Disco pd-{tipo}", horas * cenario[f"disco_{tipo}_gib"], f"pd_{tipo}_gib_hora")
        adicionar("IPv4 público em uso", horas * cenario["ipv4_publicos"], "ipv4_hora")
        backup = precos["backup"]
        ativos = cenario["dump_gib"] * backup["copias_ativas"]
        excluidos = cenario["dump_gib"] * backup["soft_delete_dias"] * backup["dumps_por_dia"]
        if cenario["dump_gib"]:
            adicionar("Backup: cópias ativas", horas * ativos, "gcs_standard_gib_hora")
            adicionar("Backup: cópias em soft delete", horas * excluidos, "gcs_standard_gib_hora")
            adicionar("Backup: operações classe A", D(backup["operacoes_a_mes"]) / 1000, "gcs_classe_a_1000")
            adicionar("Backup: operações classe B", D(backup["operacoes_b_mes"]) / 1000, "gcs_classe_b_1000")
        carga = cargas[cenario["nome"]]
        rps = D(carga["sessoes_ativas"]) * 2 / 10 + D(carga["rotas_em_transito"]) / carga["intervalo_leitura_segundos"]
        gib = rps * D(precos["horas_trafego_dia"]) * 3600 * D(precos["dias_trafego_mes"]) * 10240 / D(1024**3)
        gib_com_folga = gib * D(precos["fator_trafego"])
        gib_cotados = gib_com_folga.to_integral_value(rounding=ROUND_CEILING)
        rede = custo_egress(gib_cotados, precos["egress_premium_america_sul"])
        itens.append({"item": "Saída Premium para América do Sul", "quantidade": str(gib_cotados),
                      "unidade": "GiB", "tarifa_usd": "por faixas", "custo_usd": dinheiro(rede),
                      "custo_sem_arredondar_usd": str(rede), "fonte": precos["fontes"]["rede"]})
        bruto = sum(D(i["custo_sem_arredondar_usd"]) for i in itens)
        # Arredondar só o total evita perder operações de valor inferior a um centavo.
        total = D(dinheiro(bruto))
        reserva = D(dinheiro(total * D(precos["margem_planejamento"])))
        resultados.append({"cenario": cenario["nome"], "itens": itens,
                           "backup_ativo_gib": ativos, "backup_soft_delete_gib": excluidos,
                           "trafego_gib_base": str(gib), "trafego_gib_com_folga": str(gib_com_folga),
                           "trafego_gib_cotado": str(gib_cotados),
                           "total_usd": dinheiro(total), "reserva_15_porcento_usd": dinheiro(reserva),
                           "limite_planejamento_usd": dinheiro(total + reserva)})
    return {"data_consulta": precos["data_consulta"], "moeda": precos["moeda"],
            "regiao": precos["regiao_cotada"], "horas_mes": precos["horas_mes"],
            "natureza": "Estimativa bruta, não fatura; cotas gratuitas e impostos não aplicados.",
            "cenarios": resultados}


def exportar(resultado):
    buffer = io.StringIO(newline="")
    campos = ["cenario", "item", "quantidade", "unidade", "tarifa_usd", "custo_usd", "custo_sem_arredondar_usd", "fonte"]
    writer = csv.DictWriter(buffer, fieldnames=campos, lineterminator="\n")
    writer.writeheader()
    for cenario in resultado["cenarios"]:
        for item in cenario["itens"]:
            writer.writerow({"cenario": cenario["cenario"], **item})
    return {"orcamento-so-u2-resultados.json": json.dumps(resultado, ensure_ascii=False, indent=2) + "\n",
            "orcamento-so-u2.csv": buffer.getvalue()}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    precos = json.loads((PASTA / "precos-gcp-so-u2.json").read_text(encoding="utf-8"))
    resultado = calcular(precos)
    for nome, conteudo in exportar(resultado).items():
        destino = PASTA / nome
        if args.check:
            if not destino.exists() or destino.read_text(encoding="utf-8") != conteudo:
                raise SystemExit(f"Artefato desatualizado: {nome}")
        else:
            destino.write_text(conteudo, encoding="utf-8")
    for c in resultado["cenarios"]:
        print(f"{c['cenario']}: USD {c['total_usd']}; com reserva: USD {c['limite_planejamento_usd']}")


if __name__ == "__main__":
    main()
