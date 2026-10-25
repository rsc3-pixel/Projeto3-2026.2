#!/usr/bin/env python3
"""
Analise do benchmarking de rede do Rota Vital (PI3-31).

Le cada rodada em benchmark/dados-brutos/ (meta.json, k6.csv.gz, sonda-*.csv)
e produz:

  benchmark/resumo-rede.json   tudo o que o painel e o relatorio (PI3-113) usam
  benchmark/resumo-rede.csv    uma linha por rodada, para planilha

Com --publicar, copia o JSON para src/main/resources/static/rede/, de onde o
painel de rede (painel-rede.html) o le depois do proximo deploy.

So biblioteca padrao: roda em qualquer maquina com Python 3.9+, sem pip.

Metricas e definicoes (as mesmas de docs/benchmark-rede.md)
-----------------------------------------------------------
Latencia (ms)    http_req_duration do k6: do envio do primeiro byte ao
                 recebimento do ultimo, visto pelo cliente. Reportada em
                 percentis p50/p90/p95/p99 e maximo; a media aparece so como
                 referencia.
Vazao (req/s)    requisicoes concluidas na etapa / duracao observada da
                 etapa. Tambem em KB/s (bytes recebidos).
Perda (%)        sonda TCP: tentativas de conexao sem resposta em 1 s.
                 Aplicacao: requisicoes sem resposta HTTP (status 0 no k6:
                 timeout, conexao recusada ou resetada).
Jitter (ms)      media de |x(i) - x(i-1)| entre amostras consecutivas (ideia
                 da RFC 3550). Calculado sobre o RTT da sonda TCP (rede) e
                 sobre a latencia do /actuator/health em taxa fixa (aplicacao).
Erros (%)        respostas 4xx e 5xx sobre o total, separadas. O 409 aparece
                 a parte: na API ele e conflito de negocio/concorrencia, nao
                 falha de rede.

Percentil: interpolacao linear entre as amostras ordenadas (o mesmo metodo
padrao do numpy e do Excel PERCENTIL.INC).
"""

import argparse
import csv
import gzip
import json
import shutil
import statistics
import sys
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path

RAIZ = Path(__file__).resolve().parent
PERCENTIS = (50, 90, 95, 99)
FASES_MEDIDAS = ("etapa1", "etapa2")


# --- Estatistica basica -------------------------------------------------------

def percentil(ordenados, p):
    """Percentil com interpolacao linear. 'ordenados' precisa estar ordenado."""
    if not ordenados:
        return None
    if len(ordenados) == 1:
        return ordenados[0]
    posicao = (len(ordenados) - 1) * p / 100.0
    baixo = int(posicao)
    alto = min(baixo + 1, len(ordenados) - 1)
    fracao = posicao - baixo
    return ordenados[baixo] + (ordenados[alto] - ordenados[baixo]) * fracao


def resumo_latencia(valores):
    if not valores:
        return {"n": 0}
    ordenados = sorted(valores)
    saida = {"n": len(ordenados)}
    for p in PERCENTIS:
        saida[f"p{p}"] = arred(percentil(ordenados, p))
    saida["max"] = arred(ordenados[-1])
    saida["min"] = arred(ordenados[0])
    saida["media"] = arred(statistics.fmean(ordenados))
    return saida


def jitter(serie):
    """Media das diferencas absolutas entre amostras consecutivas."""
    if len(serie) < 2:
        return None
    return arred(statistics.fmean(abs(b - a) for a, b in zip(serie, serie[1:])))


def arred(x, casas=2):
    return None if x is None else round(float(x), casas)


def pct(parte, total):
    return arred(100.0 * parte / total, 3) if total else None


# --- Leitura de uma rodada ----------------------------------------------------

def tags_extras(texto):
    """'fase=etapa1&grupo=app' -> {'fase': 'etapa1', 'grupo': 'app'}"""
    if not texto:
        return {}
    return dict(par.split("=", 1) for par in texto.split("&") if "=" in par)


def fase_por_tempo(t_rel_s, p):
    """Fase de uma amostra das sondas (health, canario) pelo instante."""
    a, e = p["aquecimento_s"], p["etapa_s"]
    if t_rel_s < a:
        return "aquecimento"
    if t_rel_s < a + e:
        return "etapa1"
    if t_rel_s < a + 2 * e:
        return "etapa2"
    return "repouso"


def classe_status(status):
    if status == 0:
        return "transporte"
    if status == 409:
        return "409"
    if 200 <= status < 300:
        return "2xx"
    if 300 <= status < 400:
        return "3xx"
    if 400 <= status < 500:
        return "4xx"
    if status >= 500:
        return "5xx"
    return "outro"


def ler_k6(caminho):
    """Devolve as amostras de requisicao e os contadores por cenario."""
    abrir = gzip.open if caminho.suffix == ".gz" else open
    requisicoes = []
    bytes_por_fase = defaultdict(float)
    descartadas_por_fase = defaultdict(float)

    with abrir(caminho, "rt", encoding="utf-8", newline="") as arquivo:
        for linha in csv.DictReader(arquivo):
            metrica = linha["metric_name"]
            extras = tags_extras(linha.get("extra_tags"))
            if metrica == "http_req_duration":
                nome = linha["name"]
                if nome in ("setup", "teardown"):
                    continue
                requisicoes.append({
                    "ts": int(linha["timestamp"]),
                    "ms": float(linha["metric_value"]),
                    "status": int(linha["status"] or 0),
                    "nome": nome,
                    "cenario": linha["scenario"],
                    "fase": extras.get("fase"),
                    "grupo": extras.get("grupo"),
                })
            elif metrica == "data_received" and extras.get("fase"):
                bytes_por_fase[extras["fase"]] += float(linha["metric_value"])
            elif metrica == "dropped_iterations":
                descartadas_por_fase[extras.get("fase") or linha["scenario"]] += float(linha["metric_value"])

    return requisicoes, bytes_por_fase, descartadas_por_fase


def ler_sonda(caminho):
    if not caminho.exists():
        return None
    rtts, total, perdidas, motivos = [], 0, 0, defaultdict(int)
    with open(caminho, encoding="utf-8", newline="") as arquivo:
        for linha in csv.DictReader(arquivo):
            total += 1
            if linha["status"] == "ok":
                rtts.append(float(linha["rtt_ms"]))
            else:
                perdidas += 1
                motivos[linha["status"]] += 1
    resumo = resumo_latencia(rtts)
    resumo.update({
        "tentativas": total,
        "perdidas": perdidas,
        "perda_pct": pct(perdidas, total),
        "motivos_perda": dict(motivos),
        "jitter_ms": jitter(rtts),
    })
    return resumo


def analisar_rodada(pasta):
    meta = json.loads((pasta / "meta.json").read_text(encoding="utf-8"))
    p = meta["parametros"]
    csv_k6 = pasta / "k6.csv.gz"
    if not csv_k6.exists():
        csv_k6 = pasta / "k6.csv"
    requisicoes, bytes_por_fase, descartadas = ler_k6(csv_k6)
    if not requisicoes:
        raise ValueError("CSV do k6 sem nenhuma requisicao")

    t0 = min(r["ts"] for r in requisicoes)
    for r in requisicoes:
        if r["grupo"] in ("sonda", "canario"):
            r["fase"] = fase_por_tempo((r["ts"] - t0) / 1000.0, p)

    rodada = {
        "id": pasta.name,
        "origem": meta["origem"],
        "repeticao": meta["repeticao"],
        "inicio_local": meta.get("inicio_local"),
        "inicio_utc": meta.get("inicio_utc"),
        "abortada_por_threshold": meta.get("abortada_por_threshold", False),
        "parametros": p,
        "fases": {},
    }

    # --- Fases medidas da aplicacao ---
    fases_app = list(FASES_MEDIDAS) + (["telemetria"] if p.get("telemetria") else [])
    for fase in fases_app:
        app = [r for r in requisicoes if r["grupo"] == "app" and r["fase"] == fase]
        if not app:
            continue
        primeiro = min(r["ts"] for r in app)
        ultimo = max(r["ts"] for r in app)
        configurada = p["etapa_s"] * (2 if fase == "telemetria" else 1)
        # Duracao observada (cobre rodada abortada), limitada a configurada.
        duracao = min(configurada, max(1.0, (ultimo - primeiro) / 1000.0))

        classes = defaultdict(int)
        for r in app:
            classes[classe_status(r["status"])] += 1
        total = len(app)

        por_endpoint = defaultdict(list)
        for r in app:
            por_endpoint[r["nome"]].append(r)
        endpoints = {}
        for nome, lista in sorted(por_endpoint.items()):
            c = defaultdict(int)
            for r in lista:
                c[classe_status(r["status"])] += 1
            endpoints[nome] = {
                "latencia_ms": resumo_latencia([r["ms"] for r in lista if r["status"] != 0]),
                "erro_4xx_pct": pct(c["4xx"] + c["409"], len(lista)),
                "erro_5xx_pct": pct(c["5xx"], len(lista)),
                "falha_transporte_pct": pct(c["transporte"], len(lista)),
            }

        health = [r["ms"] for r in sorted(
            (r for r in requisicoes if r["grupo"] == "sonda" and r["fase"] == fase and r["status"] == 200),
            key=lambda r: r["ts"])]

        rodada["fases"][fase] = {
            "duracao_s": arred(duracao, 1),
            "requisicoes": total,
            "vazao_rps": arred(total / duracao),
            "vazao_kbps": arred(bytes_por_fase.get(fase, 0.0) / 1024.0 / duracao),
            "iteracoes_descartadas": int(descartadas.get(fase, 0)),
            # Latencia so das que tiveram resposta: falha de transporte nao
            # tem tempo de resposta, entra na taxa de perda da aplicacao.
            "latencia_ms": resumo_latencia([r["ms"] for r in app if r["status"] != 0]),
            "erros": {
                "2xx_pct": pct(classes["2xx"], total),
                "4xx_pct": pct(classes["4xx"] + classes["409"], total),
                "409_pct": pct(classes["409"], total),
                "5xx_pct": pct(classes["5xx"], total),
                "perda_aplicacao_pct": pct(classes["transporte"], total),
                "contagem": dict(classes),
            },
            "health": {
                "latencia_ms": resumo_latencia(health),
                "jitter_ms": jitter(health),
            },
            "endpoints": endpoints,
        }

    # --- Sonda TCP (rede) ---
    rodada["sonda_tcp"] = {
        "repouso": ler_sonda(pasta / "sonda-repouso.csv"),
        "carga": ler_sonda(pasta / "sonda-carga.csv"),
    }

    # --- Canario: a aplicacao vizinha antes, durante e depois da carga ---
    momentos = {"antes": ("aquecimento",), "durante": FASES_MEDIDAS, "depois": ("repouso",)}
    canario = {}
    for momento, fases in momentos.items():
        lista = [r for r in requisicoes if r["grupo"] == "canario" and r["fase"] in fases]
        ok = [r["ms"] for r in lista if 0 < r["status"] < 500]
        canario[momento] = {
            **resumo_latencia(ok),
            "falhas": len(lista) - len(ok),
        }
    rodada["canario"] = canario
    return rodada


# --- Agregacao entre repeticoes -----------------------------------------------

def caminho(dados, *chaves):
    for chave in chaves:
        if not isinstance(dados, dict) or chave not in dados:
            return None
        dados = dados[chave]
    return dados


# (rotulo, unidade, caminho dentro da rodada)
INDICADORES = [
    ("latencia_p50_etapa1", "ms", ("fases", "etapa1", "latencia_ms", "p50")),
    ("latencia_p95_etapa1", "ms", ("fases", "etapa1", "latencia_ms", "p95")),
    ("latencia_p99_etapa1", "ms", ("fases", "etapa1", "latencia_ms", "p99")),
    ("latencia_p50_etapa2", "ms", ("fases", "etapa2", "latencia_ms", "p50")),
    ("latencia_p95_etapa2", "ms", ("fases", "etapa2", "latencia_ms", "p95")),
    ("latencia_p99_etapa2", "ms", ("fases", "etapa2", "latencia_ms", "p99")),
    ("vazao_etapa1", "req/s", ("fases", "etapa1", "vazao_rps")),
    ("vazao_etapa2", "req/s", ("fases", "etapa2", "vazao_rps")),
    ("vazao_kbps_etapa2", "KB/s", ("fases", "etapa2", "vazao_kbps")),
    ("erro_4xx_etapa2", "%", ("fases", "etapa2", "erros", "4xx_pct")),
    ("erro_5xx_etapa2", "%", ("fases", "etapa2", "erros", "5xx_pct")),
    ("perda_aplicacao_etapa2", "%", ("fases", "etapa2", "erros", "perda_aplicacao_pct")),
    ("jitter_health_etapa2", "ms", ("fases", "etapa2", "health", "jitter_ms")),
    ("rtt_p50_repouso", "ms", ("sonda_tcp", "repouso", "p50")),
    ("rtt_p50_carga", "ms", ("sonda_tcp", "carga", "p50")),
    ("rtt_p95_carga", "ms", ("sonda_tcp", "carga", "p95")),
    ("perda_rede_repouso", "%", ("sonda_tcp", "repouso", "perda_pct")),
    ("perda_rede_carga", "%", ("sonda_tcp", "carga", "perda_pct")),
    ("jitter_rede_repouso", "ms", ("sonda_tcp", "repouso", "jitter_ms")),
    ("jitter_rede_carga", "ms", ("sonda_tcp", "carga", "jitter_ms")),
    ("canario_p95_antes", "ms", ("canario", "antes", "p95")),
    ("canario_p95_durante", "ms", ("canario", "durante", "p95")),
    ("canario_p95_depois", "ms", ("canario", "depois", "p95")),
]


def agregar(rodadas):
    por_origem = defaultdict(list)
    for r in rodadas:
        por_origem[r["origem"]].append(r)
    agregado = {}
    for origem, lista in sorted(por_origem.items()):
        bloco = {"repeticoes": len(lista), "indicadores": {}}
        for rotulo, unidade, chaves in INDICADORES:
            valores = [v for v in (caminho(r, *chaves) for r in lista) if v is not None]
            if not valores:
                continue
            bloco["indicadores"][rotulo] = {
                "unidade": unidade,
                "media": arred(statistics.fmean(valores), 3),
                # Desvio entre repeticoes: mostra se a medicao e reprodutivel.
                "desvio": arred(statistics.stdev(valores), 3) if len(valores) > 1 else None,
                "min": arred(min(valores), 3),
                "max": arred(max(valores), 3),
                "valores": [arred(v, 3) for v in valores],
            }
        agregado[origem] = bloco
    return agregado


def escrever_csv(rodadas, destino):
    with open(destino, "w", newline="", encoding="utf-8") as arquivo:
        escritor = csv.writer(arquivo)
        escritor.writerow(["rodada", "origem", "repeticao", "inicio_local", "abortada"]
                          + [f"{rotulo} ({unidade})" for rotulo, unidade, _ in INDICADORES])
        for r in rodadas:
            escritor.writerow([r["id"], r["origem"], r["repeticao"], r["inicio_local"],
                               r["abortada_por_threshold"]]
                              + [caminho(r, *chaves) for _, _, chaves in INDICADORES])


# --- Principal ----------------------------------------------------------------

def main():
    ap = argparse.ArgumentParser(description="Consolida as rodadas do benchmarking de rede (PI3-31).")
    ap.add_argument("--dados", type=Path, default=RAIZ / "dados-brutos")
    ap.add_argument("--saida", type=Path, default=RAIZ / "resumo-rede.json")
    ap.add_argument("--publicar", action="store_true",
                    help="copia o JSON para src/main/resources/static/rede/ (painel)")
    args = ap.parse_args()

    pastas = sorted(p for p in args.dados.glob("*") if (p / "meta.json").exists())
    if not pastas:
        sys.exit(f"Nenhuma rodada em {args.dados}. Rode benchmark/coletar.sh primeiro.")

    rodadas = []
    for pasta in pastas:
        try:
            rodadas.append(analisar_rodada(pasta))
            print(f"ok    {pasta.name}")
        except Exception as erro:  # rodada incompleta nao derruba as outras
            print(f"pulei {pasta.name}: {erro}", file=sys.stderr)

    resumo = {
        "gerado_em": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "historia": "PI3-31",
        "aviso": ("Projeto academico. Dados sinteticos; telemetria de temperatura e GPS simulada. "
                  "Medicoes de uma VM compartilhada de pequeno porte, nao representam ambiente hospitalar real."),
        "definicoes": {
            "latencia": "http_req_duration do k6, visto pelo cliente, em ms; percentis por interpolacao linear",
            "vazao": "requisicoes concluidas / duracao observada da etapa (req/s); bytes recebidos (KB/s)",
            "perda_rede": "sonda TCP na porta 443: % de handshakes sem resposta em 1 s",
            "perda_aplicacao": "% de requisicoes sem resposta HTTP (timeout, recusa, reset)",
            "jitter": "media de |x(i) - x(i-1)| entre amostras consecutivas (RFC 3550), em ms",
            "erros": "% de respostas 4xx e 5xx sobre o total; 409 contado em 4xx e destacado a parte",
        },
        "rodadas": rodadas,
        "agregado_por_origem": agregar(rodadas),
    }

    args.saida.write_text(json.dumps(resumo, indent=2, ensure_ascii=False), encoding="utf-8")
    escrever_csv(rodadas, args.saida.with_suffix(".csv"))
    print(f"\n{len(rodadas)} rodada(s) -> {args.saida} e {args.saida.with_suffix('.csv').name}")

    if args.publicar:
        destino = RAIZ.parent / "src" / "main" / "resources" / "static" / "rede" / "resumo-rede.json"
        destino.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(args.saida, destino)
        print(f"publicado em {destino.relative_to(RAIZ.parent)} (entra no ar no proximo deploy)")


if __name__ == "__main__":
    main()
