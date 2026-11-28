#!/bin/sh
# Cria (ou atualiza) o usuario de leitura do Grafana, papel Viewer.
#
# Roda uma vez na subida, no servico "grafana-usuarios" do compose.yaml. O
# Grafana nao tem provisioning de usuario por arquivo, entao isto usa a API
# de administracao. Login e senha vem do .env (GRAFANA_VIEWER_USER e
# GRAFANA_VIEWER_PASSWORD), que nunca vai para o repositorio.
#
# Idempotente: se o usuario ja existe, so redefine a senha e o papel.
set -eu

GRAFANA="http://grafana:3000"
ADMIN="admin:${GRAFANA_ADMIN_PASSWORD}"

echo "Aguardando o Grafana..."
until curl -sf "${GRAFANA}/api/health" > /dev/null; do sleep 2; done

json_senha=$(printf '%s' "${GRAFANA_VIEWER_PASSWORD}" | sed 's/\\/\\\\/g; s/"/\\"/g')

resposta=$(curl -s -u "${ADMIN}" -H 'Content-Type: application/json' \
  -X POST "${GRAFANA}/api/admin/users" \
  -d "{\"name\":\"Leitura (avaliacao)\",\"login\":\"${GRAFANA_VIEWER_USER}\",\"password\":\"${json_senha}\",\"OrgId\":1}")
id=$(printf '%s' "${resposta}" | sed -n 's/.*"id":\([0-9]*\).*/\1/p')

if [ -z "${id}" ]; then
  # Ja existia: busca o id e alinha a senha com o .env atual.
  id=$(curl -s -u "${ADMIN}" "${GRAFANA}/api/users/lookup?loginOrEmail=${GRAFANA_VIEWER_USER}" \
    | sed -n 's/^{"id":\([0-9]*\).*/\1/p')
  [ -n "${id}" ] || { echo "Nao foi possivel criar nem encontrar o usuario: ${resposta}"; exit 1; }
  curl -sf -u "${ADMIN}" -H 'Content-Type: application/json' \
    -X PUT "${GRAFANA}/api/admin/users/${id}/password" \
    -d "{\"password\":\"${json_senha}\"}" > /dev/null
fi

# Papel Viewer na organizacao principal: ve dashboards e o Explore, nao edita.
curl -sf -u "${ADMIN}" -H 'Content-Type: application/json' \
  -X PATCH "${GRAFANA}/api/orgs/1/users/${id}" -d '{"role":"Viewer"}' > /dev/null

echo "Usuario '${GRAFANA_VIEWER_USER}' pronto, papel Viewer (id ${id})."
