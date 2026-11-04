#!/usr/bin/env bash
# deploy-angular.sh
#
# Builda o frontend Angular e implanta no servidor de producao.
#
# USO
#   ./infra/deploy-angular.sh [usuario@host]
#
# EXEMPLOS
#   ./infra/deploy-angular.sh                        # usa DEPLOY_HOST do ambiente
#   ./infra/deploy-angular.sh pi@rsc3-rotavital.duckdns.org
#   DEPLOY_HOST=pi@rsc3-rotavital.duckdns.org ./infra/deploy-angular.sh
#
# PRE-REQUISITOS (maquina local)
#   - Node.js >= 18 (recomendado: nvm com v24)
#   - bun  ou  npm  instalado
#   - ssh configurado para o servidor (chave sem senha, ou ssh-agent ativo)
#
# PRE-REQUISITOS (servidor)
#   - /var/www/rota-vital-angular/  deve existir e pertencer ao usuario de deploy
#       sudo mkdir -p /var/www/rota-vital-angular
#       sudo chown $USER:$USER /var/www/rota-vital-angular
#   - Nginx configurado com infra/nginx-rota-vital.conf (location /app/)
#
# O QUE ESTE SCRIPT FAZ
#   1. Verifica pre-requisitos
#   2. Instala dependencias (bun install ou npm ci)
#   3. Builda com ng build --configuration=production
#   4. Faz rsync do dist/ para o servidor (--delete remove arquivos obsoletos)
#   5. Recarrega o Nginx no servidor (sem derrubar conexoes em andamento)

set -euo pipefail

# ---------------------------------------------------------------------------
# Configuracao
# ---------------------------------------------------------------------------

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
ANGULAR_DIR="${REPO_ROOT}/frontend/angular"
# Angular CLI 17+ com @angular/build: output em dist/<project>/browser/
DIST_DIR="${ANGULAR_DIR}/dist/rota-vital-web/browser"
REMOTE_DIR="/var/www/rota-vital-angular/"

# Host pode vir do argumento ou da variavel de ambiente
DEPLOY_HOST="${1:-${DEPLOY_HOST:-}}"

# ---------------------------------------------------------------------------
# Cores
# ---------------------------------------------------------------------------
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
info()    { echo -e "${GREEN}[deploy]${NC} $*"; }
warn()    { echo -e "${YELLOW}[aviso ]${NC} $*"; }
die()     { echo -e "${RED}[erro  ]${NC} $*" >&2; exit 1; }

# ---------------------------------------------------------------------------
# 1. Verificacoes
# ---------------------------------------------------------------------------
info "Verificando pre-requisitos..."

[[ -d "${ANGULAR_DIR}" ]] || die "Diretorio Angular nao encontrado: ${ANGULAR_DIR}"

if [[ -z "${DEPLOY_HOST}" ]]; then
    die "Informe o host de deploy:\n  $0 usuario@host\n  ou: export DEPLOY_HOST=usuario@host"
fi

# Node: precisa de v18+
if command -v node &>/dev/null; then
    NODE_MAJOR=$(node -e "process.stdout.write(process.version.split('.')[0].replace('v',''))")
    (( NODE_MAJOR >= 18 )) || die "Node.js >= 18 necessario (encontrado: $(node -v))"
else
    die "Node.js nao encontrado. Instale com nvm: nvm install 24"
fi

# Gerenciador de pacotes: bun > npm
if command -v bun &>/dev/null; then
    PKG_MANAGER="bun"
    INSTALL_CMD="bun install --frozen-lockfile"
    info "Usando bun $(bun --version)"
else
    PKG_MANAGER="npm"
    INSTALL_CMD="npm ci"
    warn "bun nao encontrado, usando npm. Para instalar o bun: curl -fsSL https://bun.sh/install | bash"
fi

# ng CLI local
NG_BIN="${ANGULAR_DIR}/node_modules/@angular/cli/bin/ng.js"

# SSH acessivel?
info "Testando conexao SSH com ${DEPLOY_HOST}..."
ssh -o BatchMode=yes -o ConnectTimeout=8 "${DEPLOY_HOST}" "echo ok" &>/dev/null \
    || die "Nao foi possivel conectar via SSH em ${DEPLOY_HOST}.\nVerifique sua chave SSH ou execute: ssh-add ~/.ssh/id_rsa"

# ---------------------------------------------------------------------------
# 2. Instalar dependencias
# ---------------------------------------------------------------------------
info "Instalando dependencias (${PKG_MANAGER})..."
(cd "${ANGULAR_DIR}" && ${INSTALL_CMD})

# ---------------------------------------------------------------------------
# 3. Build de producao
# ---------------------------------------------------------------------------
info "Buildando Angular (--configuration=production)..."
(cd "${ANGULAR_DIR}" && node "${NG_BIN}" build --configuration=production)

[[ -d "${DIST_DIR}" ]] \
    || die "Build concluido mas dist/ nao encontrado em: ${DIST_DIR}"

DIST_SIZE=$(du -sh "${DIST_DIR}" | cut -f1)
info "Build OK — ${DIST_SIZE} em ${DIST_DIR}"

# ---------------------------------------------------------------------------
# 4. Sincronizar com o servidor
# ---------------------------------------------------------------------------
info "Sincronizando ${DIST_DIR}/ → ${DEPLOY_HOST}:${REMOTE_DIR} ..."

# --delete  remove arquivos antigos no servidor (evita bundles obsoletos)
# --checksum compara conteudo, nao so timestamp (mais seguro em VMs)
# -z        comprime em transito
rsync -avz --checksum --delete \
    "${DIST_DIR}/" \
    "${DEPLOY_HOST}:${REMOTE_DIR}"

# ---------------------------------------------------------------------------
# 5. Recarregar Nginx
# ---------------------------------------------------------------------------
info "Recarregando Nginx no servidor..."
ssh "${DEPLOY_HOST}" "sudo nginx -t && sudo systemctl reload nginx" \
    && info "Nginx recarregado com sucesso." \
    || warn "Falha ao recarregar Nginx. Execute manualmente: ssh ${DEPLOY_HOST} 'sudo systemctl reload nginx'"

# ---------------------------------------------------------------------------
# Pronto
# ---------------------------------------------------------------------------
echo ""
info "Deploy concluido!"
info "Frontend disponivel em: https://rsc3-rotavital.duckdns.org/app/"
