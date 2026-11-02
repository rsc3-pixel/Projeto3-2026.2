#!/usr/bin/env bash
# =============================================================================
# Uma rodada de benchmarking de rede do Rota Vital (PI3-31).
#
# Roda, em ordem:
#   1. aquecimento do health ate a JVM responder (cold start de 20-30 s)
#   2. sonda TCP em repouso (rede sem carga, linha de base)
#   3. k6 com a carga, e em paralelo a sonda TCP durante as etapas medidas
#   (opcional) vmstat na VM por SSH, para mostrar CPU/memoria sob carga
#
# Tudo vai para benchmark/dados-brutos/<data>_<origem>_rep<N>/, junto de um
# meta.json com data, hora, origem e parametros. E essa pasta que o criterio
# "dados brutos guardados no repositorio" pede.
#
# USO (Linux, macOS, WSL, Git Bash ou GitHub Actions):
#
#   ORIGEM=notebook-recife REPETICAO=1 bash benchmark/coletar.sh
#
# Variaveis:
#   ORIGEM            obrigatoria. De onde sai a carga (ex.: github-actions,
#                     notebook-recife). Sem espaco.
#   REPETICAO         obrigatoria. 1, 2, 3...
#   BASE_URL          padrao https://rsc3-rotavital.duckdns.org
#   CANARIO_URL       padrao https://rsc3-flux.duckdns.org/
#   TAXA1, TAXA2      req/s das etapas (padrao 5 e 10)
#   DUR_AQUECIMENTO_S, DUR_ETAPA_S, DUR_REPOUSO_S   (padrao 120, 300, 60)
#   TELEMETRIA=1      liga o cenario de telemetria simulada
#   VM_SSH            opcional, ex.: usuario@IP. Se definido, coleta vmstat.
#
# ANTES DE RODAR: nao haja push na main durante a rodada. O deploy reinicia a
# aplicacao e zera o H2, e a rodada inteira fica contaminada.
#
# Espace as repeticoes em 20-30 min: VMs pequenas do GCP trabalham com
# creditos de CPU, e rodadas coladas pioram artificialmente.
# =============================================================================

set -euo pipefail

DIR_BENCH="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

: "${ORIGEM:?defina ORIGEM (ex.: ORIGEM=github-actions)}"
: "${REPETICAO:?defina REPETICAO (1, 2, 3...)}"

export BASE_URL="${BASE_URL:-https://rsc3-rotavital.duckdns.org}"
export CANARIO_URL="${CANARIO_URL:-https://rsc3-flux.duckdns.org/}"
export TAXA1="${TAXA1:-5}"
export TAXA2="${TAXA2:-10}"
export DUR_AQUECIMENTO_S="${DUR_AQUECIMENTO_S:-120}"
export DUR_ETAPA_S="${DUR_ETAPA_S:-300}"
export DUR_REPOUSO_S="${DUR_REPOUSO_S:-60}"
export TELEMETRIA="${TELEMETRIA:-0}"
DUR_SONDA_REPOUSO_S="${DUR_SONDA_REPOUSO_S:-60}"

if [[ "$ORIGEM" =~ [[:space:]/] ]]; then
    echo "ORIGEM nao pode ter espaco nem barra: '$ORIGEM'" >&2
    exit 1
fi

# Python: python3 no Linux/macOS, python no Windows.
PY="${PYTHON:-}"
if [[ -z "$PY" ]]; then
    if command -v python3 >/dev/null 2>&1 && python3 -c 'import sys' >/dev/null 2>&1; then PY=python3; else PY=python; fi
fi
command -v k6 >/dev/null 2>&1 || { echo "k6 nao encontrado. Veja docs/benchmark-rede.md." >&2; exit 1; }
command -v "$PY" >/dev/null 2>&1 || { echo "Python nao encontrado." >&2; exit 1; }

HOST="$(echo "$BASE_URL" | sed -E 's#^[a-z]+://##; s#[:/].*$##')"
PORTA=443
[[ "$BASE_URL" == http://* ]] && PORTA=80
[[ "$BASE_URL" =~ ://[^/]+:([0-9]+) ]] && PORTA="${BASH_REMATCH[1]}"

CARIMBO="$(date -u +%Y-%m-%d_%H%M)"
PASTA="$DIR_BENCH/dados-brutos/${CARIMBO}_${ORIGEM}_rep${REPETICAO}"
mkdir -p "$PASTA"
export SAIDA="$PASTA"

echo "== Rodada ${ORIGEM} rep${REPETICAO} -> ${PASTA#"$DIR_BENCH"/}"

# --- 1. Aquecimento ----------------------------------------------------------
# A primeira requisicao apos ociosidade leva 20-30 s e pode voltar 504. Isso
# nao e a rede: e a JVM carregando classes. Insistimos ate responder UP.
echo "-- aquecendo ${BASE_URL}/actuator/health"
for tentativa in $(seq 1 12); do
    codigo="$(curl -s -o /dev/null -w '%{http_code}' --max-time 35 "${BASE_URL}/actuator/health" || true)"
    if [[ "$codigo" == "200" ]]; then
        echo "   UP na tentativa ${tentativa}"
        break
    fi
    echo "   tentativa ${tentativa}: HTTP ${codigo}, aguardando..."
    sleep 5
    if [[ "$tentativa" == 12 ]]; then
        echo "A aplicacao nao respondeu UP em ~7 min. Abortando sem medir." >&2
        exit 1
    fi
done

# --- Metadados ---------------------------------------------------------------
"$PY" - "$PASTA/meta.json" <<EOF
import json, sys, platform, subprocess
from datetime import datetime, timezone
def k6v():
    try: return subprocess.run(["k6", "version"], capture_output=True, text=True).stdout.strip()
    except Exception: return None
meta = {
    "historia": "PI3-31",
    "origem": "${ORIGEM}",
    "repeticao": int("${REPETICAO}"),
    "inicio_utc": datetime.now(timezone.utc).isoformat(timespec="seconds"),
    "inicio_local": datetime.now().astimezone().isoformat(timespec="seconds"),
    "base_url": "${BASE_URL}",
    "canario_url": "${CANARIO_URL}",
    "host": "${HOST}",
    "porta_sonda": ${PORTA},
    "parametros": {
        "taxa1_rps": float("${TAXA1}"), "taxa2_rps": float("${TAXA2}"),
        "aquecimento_s": int("${DUR_AQUECIMENTO_S}"), "etapa_s": int("${DUR_ETAPA_S}"),
        "repouso_s": int("${DUR_REPOUSO_S}"), "sonda_repouso_s": int("${DUR_SONDA_REPOUSO_S}"),
        "telemetria": "${TELEMETRIA}" == "1",
    },
    "ferramentas": {"k6": k6v(), "python": platform.python_version(), "so": platform.platform()},
    "observacao": "Dados sinteticos. Telemetria de temperatura simulada.",
}
json.dump(meta, open(sys.argv[1], "w", encoding="utf-8"), indent=2, ensure_ascii=False)
EOF

# --- 2. Sonda em repouso -----------------------------------------------------
echo "-- sonda TCP em repouso (${DUR_SONDA_REPOUSO_S}s)"
"$PY" "$DIR_BENCH/sonda_tcp.py" --host "$HOST" --porta "$PORTA" \
    --duracao "$DUR_SONDA_REPOUSO_S" --saida "$PASTA/sonda-repouso.csv"

# --- vmstat na VM (opcional) -------------------------------------------------
DUR_TOTAL=$(( DUR_AQUECIMENTO_S + 2 * DUR_ETAPA_S + DUR_REPOUSO_S ))
PID_VMSTAT=""
if [[ -n "${VM_SSH:-}" ]]; then
    echo "-- vmstat em ${VM_SSH} durante ${DUR_TOTAL}s"
    # -t inclui o horario em cada linha; amostra a cada 5 s.
    ssh -o BatchMode=yes -o ConnectTimeout=10 "$VM_SSH" \
        "free -m; vmstat -t 5 $(( DUR_TOTAL / 5 + 2 ))" > "$PASTA/vmstat.txt" 2>&1 &
    PID_VMSTAT=$!
fi

# --- 3. Carga + sonda durante as etapas --------------------------------------
DUR_MEDIDA=$(( 2 * DUR_ETAPA_S ))
# exec: depois do sleep, o proprio processo vira a sonda, e o kill abaixo a
# alcanca se o k6 abortar antes da hora.
( sleep "$DUR_AQUECIMENTO_S"
  exec "$PY" "$DIR_BENCH/sonda_tcp.py" --host "$HOST" --porta "$PORTA" \
      --duracao "$DUR_MEDIDA" --saida "$PASTA/sonda-carga.csv" ) &
PID_SONDA=$!

echo "-- k6: ${DUR_TOTAL}s (aquecimento ${DUR_AQUECIMENTO_S}s, etapas de ${DUR_ETAPA_S}s a ${TAXA1} e ${TAXA2} req/s)"
set +e
K6_CSV_TIME_FORMAT=unix_milli k6 run --quiet \
    --out "csv=$PASTA/k6.csv" \
    "$DIR_BENCH/k6/carga-rede.js"
CODIGO_K6=$?
set -e

# Se o k6 abortou cedo (threshold), a sonda ainda pode estar rodando: encerra.
kill "$PID_SONDA" 2>/dev/null || true
wait "$PID_SONDA" 2>/dev/null || true
if [[ -n "$PID_VMSTAT" ]]; then kill "$PID_VMSTAT" 2>/dev/null || true; wait "$PID_VMSTAT" 2>/dev/null || true; fi

gzip -f "$PASTA/k6.csv"

"$PY" - "$PASTA/meta.json" "$CODIGO_K6" <<'EOF'
import json, sys
from datetime import datetime, timezone
caminho, codigo = sys.argv[1], int(sys.argv[2])
meta = json.load(open(caminho, encoding="utf-8"))
meta["fim_utc"] = datetime.now(timezone.utc).isoformat(timespec="seconds")
meta["k6_codigo_saida"] = codigo
# 99 = algum threshold com abortOnFail estourou: a rodada foi interrompida de
# proposito para proteger a VM. Os dados ate ali continuam validos e contam.
meta["abortada_por_threshold"] = codigo == 99
json.dump(meta, open(caminho, "w", encoding="utf-8"), indent=2, ensure_ascii=False)
EOF

echo "== Fim. k6 saiu com codigo ${CODIGO_K6}$([[ $CODIGO_K6 == 99 ]] && echo ' (abortado por threshold: veja resumo-k6.json)')"
echo "   Dados em ${PASTA#"$DIR_BENCH"/}"
echo "   Proximo passo: python3 benchmark/analisar_rede.py"
