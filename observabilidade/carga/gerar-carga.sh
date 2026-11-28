#!/bin/sh
# Gerador de carga HTTP para a demonstracao (servico "carga-http").
#
# Mantem os graficos com dados durante a correcao: consultas de leitura,
# calculo de rota (Dijkstra, varios spans) e, de proposito, casos de erro:
#   - 404: recurso inexistente
#   - 400: corpo invalido
#   - 500: /api/v1/demonstracao/erro, que so existe no perfil docker
# Os erros geram logs de ERROR/WARN com trace_id, usados para mostrar a
# navegacao log -> trace no Grafana.
#
# Dados sinteticos. Nenhuma requisicao escreve dado real.
set -u

API="${API_URL:-http://api:8080}"
PAUSA="${PAUSA_S:-1}"

echo "Aguardando a API em ${API}..."
until curl -sf -o /dev/null "${API}/api/v1/hemocentros"; do sleep 3; done
echo "Gerando carga (pausa de ${PAUSA}s entre rodadas)."

rodada=0
while true; do
  rodada=$((rodada + 1))
  curl -s -o /dev/null "${API}/api/v1/hemocentros"
  curl -s -o /dev/null "${API}/api/v1/bolsas?status=DISPONIVEL"
  curl -s -o /dev/null "${API}/api/v1/indicadores/estoque-por-tipo"
  curl -s -o /dev/null "${API}/api/v1/indicadores/validade"
  curl -s -o /dev/null "${API}/api/v1/rotas/calcular?origemId=HC01&destinoId=HO06"

  # A cada 5 rodadas, um 404 e um 400; a cada 20, um 500.
  if [ $((rodada % 5)) -eq 0 ]; then
    curl -s -o /dev/null "${API}/api/v1/bolsas/BOLSA-QUE-NAO-EXISTE"
    curl -s -o /dev/null -X POST -H 'Content-Type: application/json' \
      -d '{"tipo":"INVALIDO"}' "${API}/api/v1/bolsas"
  fi
  if [ $((rodada % 20)) -eq 0 ]; then
    curl -s -o /dev/null "${API}/api/v1/demonstracao/erro"
  fi

  sleep "${PAUSA}"
done
