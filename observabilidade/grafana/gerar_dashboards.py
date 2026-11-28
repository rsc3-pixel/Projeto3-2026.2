"""Gera os dashboards do Grafana versionados em observabilidade/grafana/dashboards/.

    python3 observabilidade/grafana/gerar_dashboards.py

Os JSON sao gerados, nao editados a mao: assim os dois dashboards seguem o
mesmo padrao (fonte de dados, cores, limites) e a revisao no PR e deste
arquivo. Depois de rodar, reinicie o Grafana (docker compose restart grafana)
ou espere o provisioning recarregar.
"""
import json
import os

DS = {"type": "prometheus", "uid": "prometheus-rotavital"}
LOKI = {"type": "loki", "uid": "loki-rotavital"}
# Filtro pela tag application (enunciado R1), escolhida na variavel do topo.
API = 'application=~"$application"'
HTTP = 'application=~"$application", uri!~"/actuator.*"'
RI = "$__rate_interval"
panels = []
pid = [0]
DESTINO = os.path.join(os.path.dirname(os.path.abspath(__file__)), "dashboards")

def nid():
    pid[0] += 1
    return pid[0]

def alvo(expr, legenda="", ref="A", instant=False):
    t = {"datasource": DS, "expr": expr, "refId": ref, "legendFormat": legenda or "__auto"}
    if instant:
        t["instant"] = True; t["range"] = False
    return t

def limiares(*passos):
    s = [{"color": "green", "value": None}]
    for valor, cor in passos:
        s.append({"color": cor, "value": valor})
    return {"mode": "absolute", "steps": s}

def row(titulo, y):
    panels.append({"type": "row", "title": titulo, "id": nid(), "collapsed": False,
                   "gridPos": {"h": 1, "w": 24, "x": 0, "y": y}, "panels": []})

def stat(titulo, expr, x, y, w, unidade="short", th=None, desc="", mapeamento=None, decimais=None, cor_fundo=True):
    p = {"type": "stat", "title": titulo, "id": nid(), "datasource": DS, "description": desc,
         "gridPos": {"h": 4, "w": w, "x": x, "y": y},
         "targets": [alvo(expr)],
         "options": {"reduceOptions": {"calcs": ["lastNotNull"], "fields": "", "values": False},
                     "colorMode": "background" if cor_fundo else "value", "graphMode": "none",
                     "textMode": "value", "justifyMode": "center", "orientation": "auto"},
         "fieldConfig": {"defaults": {"unit": unidade, "thresholds": th or limiares(),
                                      "color": {"mode": "thresholds"}, "mappings": mapeamento or []},
                         "overrides": []}}
    if decimais is not None:
        p["fieldConfig"]["defaults"]["decimals"] = decimais
    panels.append(p)

def serie(titulo, alvos, x, y, w, h=8, unidade="short", desc="", empilhar=False, minimo=None, th=None, overrides=None):
    d = {"unit": unidade, "custom": {"drawStyle": "line", "lineWidth": 2, "fillOpacity": 15 if empilhar else 5,
                                     "showPoints": "never", "spanNulls": True,
                                     "stacking": {"mode": "normal" if empilhar else "none", "group": "A"}},
         "color": {"mode": "palette-classic"}}
    if minimo is not None:
        d["min"] = minimo
    if th:
        d["thresholds"] = th
        d["custom"]["thresholdsStyle"] = {"mode": "dashed"}
    panels.append({"type": "timeseries", "title": titulo, "id": nid(), "datasource": DS, "description": desc,
                   "gridPos": {"h": h, "w": w, "x": x, "y": y},
                   "targets": [alvo(e, l, chr(65 + i)) for i, (e, l) in enumerate(alvos)],
                   "options": {"legend": {"displayMode": "list", "placement": "bottom", "showLegend": True},
                               "tooltip": {"mode": "multi", "sort": "desc"}},
                   "fieldConfig": {"defaults": d, "overrides": overrides or []}})

def barras(titulo, expr, legenda, x, y, w, h=8, th=None, desc="", unidade="short"):
    panels.append({"type": "bargauge", "title": titulo, "id": nid(), "datasource": DS, "description": desc,
                   "gridPos": {"h": h, "w": w, "x": x, "y": y},
                   "targets": [alvo(expr, legenda, instant=True)],
                   "options": {"orientation": "horizontal", "displayMode": "gradient", "showUnfilled": True,
                               "valueMode": "color", "namePlacement": "left", "sizing": "auto",
                               "reduceOptions": {"calcs": ["lastNotNull"], "fields": "", "values": False}},
                   "fieldConfig": {"defaults": {"unit": unidade, "min": 0, "thresholds": th or limiares(),
                                                "color": {"mode": "thresholds"}}, "overrides": []}})

# ---------------------------------------------------------------- visao geral
y = 0
panels.append({"type": "text", "title": "", "id": nid(), "gridPos": {"h": 2, "w": 24, "x": 0, "y": y},
               "options": {"mode": "markdown", "content":
                   "**Rota Vital — projeto acadêmico.** Dados sintéticos (LGPD). A telemetria de temperatura e GPS é "
                   "**simulada** (PI3-112), sem sensor ou veículo real. Compatibilidade ABO/Rh e faixas de cadeia fria são didáticas."}})
y += 2
row("Visão geral", y); y += 1
stat("API", 'up{job="rota-vital"}', 0, y, 4,
     mapeamento=[{"type": "value", "options": {"1": {"text": "NO AR", "color": "green", "index": 0},
                                               "0": {"text": "FORA", "color": "red", "index": 1}}}],
     th=limiares(), desc="Se o Prometheus conseguiu ler /actuator/prometheus na última coleta.")
stat("Requisições por segundo", f'sum(rate(http_server_requests_seconds_count{{{HTTP}}}[{RI}]))', 4, y, 5,
     unidade="reqps", decimais=2, cor_fundo=False, desc="Sem contar /actuator (a própria coleta do Prometheus).")
stat("Latência p95", f'histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{{{HTTP}}}[{RI}])))',
     9, y, 5, unidade="s", th=limiares((0.5, "orange"), (1, "red")),
     desc="95% das requisições respondem abaixo deste tempo, medido no servidor (sem a rede até o cliente). Limite de 500 ms de SO U2.")
stat("Erros 5xx", f'sum(rate(http_server_requests_seconds_count{{{HTTP}, status=~"5.."}}[{RI}])) / '
                  f'sum(rate(http_server_requests_seconds_count{{{HTTP}}}[{RI}]))',
     14, y, 5, unidade="percentunit", decimais=2, th=limiares((0.01, "red")),
     desc="Fração das respostas com status 5xx. Limite de 1% de SO U2.")
stat("Tempo no ar", f'process_uptime_seconds{{{API}}}', 19, y, 5, unidade="s", cor_fundo=False,
     desc="Desde o último início da JVM. Zera a cada deploy ou reinício.")
y += 4

# ---------------------------------------------------------- hemocomponentes
row("Hemocomponentes", y); y += 1
barras("Bolsas disponíveis por tipo", 'sum by (tipo) (rotavital_estoque_bolsas{application=~"$application", status="DISPONIVEL"})', "{{tipo}}",
       0, y, 9, th=limiares(), desc="Estoque com status DISPONIVEL. Atualizado a cada 30 s.")
barras("Disponíveis que vencem em até 3 dias", 'sum by (tipo) (rotavital_estoque_vencendo{application=~"$application"})', "{{tipo}}",
       9, y, 9, th=limiares((1, "orange")),
       desc="Candidatas a remanejamento antes de virar descarte. O FEFO prioriza estas na alocação.")
stat("Vencidas ainda DISPONÍVEIS", 'sum(rotavital_estoque_vencidas_disponiveis{application=~"$application"})', 18, y, 6,
     th=limiares((1, "red")), desc="O FEFO nunca aloca bolsa vencida, mas ela precisa ser descartada do estoque.")
panels[-1]["gridPos"]["h"] = 8
y += 8
serie("Estoque por status", [('sum by (status) (rotavital_estoque_bolsas{application=~"$application"})', "{{status}}")], 0, y, 12,
      empilhar=True, minimo=0, desc="Todas as bolsas, de todos os tipos, por status.")
serie("Leituras de temperatura por minuto (telemetria simulada)",
      [('sum by (resultado) (rate(rotavital_telemetria_leituras_total{application=~"$application"}[$__rate_interval])) * 60', "{{resultado}}")],
      12, y, 12, empilhar=True, minimo=0,
      desc="Leituras enviadas pelo simulador (PI3-112): dentro da faixa ou com excursão para algum componente a bordo.",
      overrides=[{"matcher": {"id": "byName", "options": "excursao"},
                  "properties": [{"id": "color", "value": {"mode": "fixed", "fixedColor": "red"}}]},
                 {"matcher": {"id": "byName", "options": "dentro_da_faixa"},
                  "properties": [{"id": "color", "value": {"mode": "fixed", "fixedColor": "green"}}]}])
y += 8
barras("Excursões de temperatura na última hora, por componente",
       'sum by (componente) (increase(rotavital_telemetria_excursoes_total{application=~"$application"}[1h]))', "{{componente}}",
       0, y, 12, th=limiares((1, "red")),
       desc="Quantas leituras ficaram fora da faixa de cada componente a bordo. Uma leitura em remessa mista pode contar para mais de um.")
serie("Alocações por resultado (por minuto)",
      [('sum by (resultado) (rate(rotavital_alocacoes_total{application=~"$application"}[$__rate_interval])) * 60', "{{resultado}}"),
       ('sum(rate(rotavital_alocacoes_repeticoes_total{application=~"$application"}[$__rate_interval])) * 60', "repetições")],
      12, y, 12, minimo=0,
      desc="Manual e automática (FEFO). conflito_concorrencia: perdeu a disputa pela bolsa nas 3 tentativas (PI3-108). recusada: regra de negócio negou.")
y += 8
serie("Tempo de alocação de bolsa (média e p95)",
      [('sum by (modo) (rate(rotavital_alocacoes_duracao_seconds_sum{application=~"$application"}[$__rate_interval]))'
        ' / sum by (modo) (rate(rotavital_alocacoes_duracao_seconds_count{application=~"$application"}[$__rate_interval]))',
        "média {{modo}}"),
       ('histogram_quantile(0.95, sum by (le, modo) (rate(rotavital_alocacoes_duracao_seconds_bucket{application=~"$application"}[$__rate_interval])))',
        "p95 {{modo}}")],
      0, y, 12, unidade="s", minimo=0,
      desc="Timer de negócio: do pedido de alocação à resposta, incluindo as repetições por concorrência.")
panels.append({"type": "logs", "title": "Logs de erro e alerta da aplicação (Loki)", "id": nid(), "datasource": LOKI,
               "description": "Linhas WARN/ERROR enviadas pelo agente do OpenTelemetry. Expanda uma linha: o campo trace_id abre o trace no Tempo.",
               "gridPos": {"h": 8, "w": 12, "x": 12, "y": y},
               "targets": [{"datasource": LOKI, "refId": "A",
                            "expr": '{service_name=~"$application"} | severity_text=~"ERROR|WARN"'}],
               "options": {"showTime": True, "wrapLogMessage": True, "sortOrder": "Descending",
                           "enableLogDetails": True, "prettifyLogMessage": False, "dedupStrategy": "none"}})
y += 8

# --------------------------------------------------------------------- HTTP
row("HTTP", y); y += 1
serie("Requisições por segundo, por endpoint",
      [(f'sum by (method, uri) (rate(http_server_requests_seconds_count{{{HTTP}}}[{RI}]))', "{{method}} {{uri}}")],
      0, y, 12, unidade="reqps", minimo=0)
serie("Latência p95 por endpoint",
      [(f'histogram_quantile(0.95, sum by (le, uri) (rate(http_server_requests_seconds_bucket{{{HTTP}}}[{RI}])))', "{{uri}}")],
      12, y, 12, unidade="s", minimo=0, th=limiares((0.5, "red")),
      desc="Tempo no servidor. A latência vista pelo usuário soma a rede (cerca de 200 ms até Oregon, PI3-113).")
y += 8
serie("Respostas por resultado",
      [(f'sum by (outcome) (rate(http_server_requests_seconds_count{{{HTTP}}}[{RI}]))', "{{outcome}}")],
      0, y, 12, unidade="reqps", empilhar=True, minimo=0,
      desc="SUCCESS = 2xx, CLIENT_ERROR = 4xx (inclui o 409 esperado de conflito de alocação), SERVER_ERROR = 5xx.",
      overrides=[{"matcher": {"id": "byName", "options": "SERVER_ERROR"},
                  "properties": [{"id": "color", "value": {"mode": "fixed", "fixedColor": "red"}}]},
                 {"matcher": {"id": "byName", "options": "CLIENT_ERROR"},
                  "properties": [{"id": "color", "value": {"mode": "fixed", "fixedColor": "orange"}}]},
                 {"matcher": {"id": "byName", "options": "SUCCESS"},
                  "properties": [{"id": "color", "value": {"mode": "fixed", "fixedColor": "green"}}]}])
serie("Erros por endpoint e status",
      [(f'sum by (uri, status) (rate(http_server_requests_seconds_count{{{HTTP}, status=~"[45].."}}[{RI}]))', "{{status}} {{uri}}")],
      12, y, 12, unidade="reqps", minimo=0)
y += 8

# ---------------------------------------------------------------- JVM e banco
row("JVM e banco de dados", y); y += 1
serie("Memória heap", [(f'sum(jvm_memory_used_bytes{{{API}, area="heap"}})', "usada"),
                       (f'sum(jvm_memory_committed_bytes{{{API}, area="heap"}})', "reservada"),
                       (f'sum(jvm_memory_max_bytes{{{API}, area="heap"}} > 0)', "máximo (-Xmx)")],
      0, y, 8, unidade="bytes", minimo=0)
serie("Pausas de GC (fração do tempo)",
      [(f'sum by (action) (rate(jvm_gc_pause_seconds_sum{{{API}}}[{RI}]))', "{{action}}")],
      8, y, 8, unidade="percentunit", minimo=0,
      desc="Fração do tempo com a aplicação parada pelo coletor de lixo. Pausas longas aparecem como picos de latência.")
serie("CPU", [(f'process_cpu_usage{{{API}}}', "processo (JVM)"), (f'system_cpu_usage{{{API}}}', "máquina")],
      16, y, 8, unidade="percentunit", minimo=0)
y += 8
serie("Conexões com o banco (pool Hikari)",
      [(f'sum(hikaricp_connections_active{{{API}}})', "ativas"),
       (f'sum(hikaricp_connections_idle{{{API}}})', "ociosas"),
       (f'sum(hikaricp_connections_pending{{{API}}})', "esperando"),
       (f'sum(hikaricp_connections_max{{{API}}})', "máximo")],
      0, y, 12, minimo=0,
      desc="'esperando' acima de zero indica pool esgotado: requisições paradas aguardando conexão (ex.: carga em lote, PI3-30).")
serie("Threads da JVM", [(f'sum by (state) (jvm_threads_states_threads{{{API}}})', "{{state}}")],
      12, y, 12, empilhar=True, minimo=0)

VARIAVEL_APP = {
    "name": "application", "label": "Aplicação", "type": "query", "datasource": DS,
    "query": {"query": "label_values(http_server_requests_seconds_count, application)", "refId": "A"},
    "definition": "label_values(http_server_requests_seconds_count, application)",
    "refresh": 2, "includeAll": True, "multi": True,
    # ".+" e nao ".*": o Loki recusa seletor que casa com vazio.
    "allValue": ".+",
    "current": {"selected": True, "text": ["All"], "value": ["$__all"]},
}


def dashboard(uid, titulo, descricao, tags, paineis, variaveis):
    return {
        "uid": uid, "title": titulo, "description": descricao, "tags": tags,
        "timezone": "browser", "editable": True, "graphTooltip": 1, "refresh": "15s",
        "time": {"from": "now-1h", "to": "now"},
        "timepicker": {"refresh_intervals": ["15s", "30s", "1m", "5m"]},
        "schemaVersion": 39, "version": 1,
        "templating": {"list": variaveis},
        "annotations": {"list": [{"builtIn": 1, "datasource": {"type": "grafana", "uid": "-- Grafana --"},
                                  "enable": True, "hide": True, "iconColor": "rgba(0, 211, 255, 1)",
                                  "name": "Annotations & Alerts", "type": "dashboard"}]},
        "links": [{"title": "Outros dashboards", "type": "dashboards", "tags": ["rota-vital"],
                   "asDropdown": True, "includeVars": False, "keepTime": True}],
        "panels": paineis,
    }


def gravar(nome, dash):
    caminho = os.path.join(DESTINO, nome)
    with open(caminho, "w", encoding="utf-8") as arquivo:
        json.dump(dash, arquivo, ensure_ascii=False, indent=2)
        arquivo.write("\n")
    print(f"{nome}: {len(dash['panels'])} painéis")


gravar("rota-vital.json", dashboard(
    "rota-vital", "Rota Vital — API e hemocomponentes",
    "Aplicação: métricas do Spring Boot Actuator (Micrometer) coletadas pelo Prometheus e logs no Loki. "
    "Telemetria simulada, dados sintéticos.",
    ["rota-vital", "aplicacao", "spring-boot"], panels, [VARIAVEL_APP]))

# ======================================================= dashboard de infraestrutura
panels = []
pid[0] = 0
NODE = 'job="node-exporter"'
CONT = 'job="cadvisor", name=~"rotavital-.*"'
y = 0
panels.append({"type": "text", "title": "", "id": nid(), "gridPos": {"h": 2, "w": 24, "x": 0, "y": y},
               "options": {"mode": "markdown", "content":
                   "**Infraestrutura do ambiente em container.** Host pelo Node Exporter, contêineres pelo cAdvisor. "
                   "No Docker Desktop (Windows/macOS) o *host* é a VM Linux do Docker, não o computador."}})
y += 2
row("Alvos do Prometheus", y); y += 1
panels.append({"type": "stat", "title": "Alvos coletados (1 = UP)", "id": nid(), "datasource": DS,
               "description": "Um quadro por job do prometheus.yml. Todos devem estar verdes.",
               "gridPos": {"h": 4, "w": 24, "x": 0, "y": y},
               "targets": [alvo("max by (job) (up)", "{{job}}", instant=True)],
               "options": {"reduceOptions": {"calcs": ["lastNotNull"], "fields": "", "values": False},
                           "colorMode": "background", "graphMode": "none", "textMode": "value_and_name",
                           "justifyMode": "center", "orientation": "vertical"},
               "fieldConfig": {"defaults": {"thresholds": {"mode": "absolute", "steps": [
                   {"color": "red", "value": None}, {"color": "green", "value": 1}]},
                   "color": {"mode": "thresholds"},
                   "mappings": [{"type": "value", "options": {"1": {"text": "UP", "index": 0},
                                                              "0": {"text": "DOWN", "index": 1}}}]},
                   "overrides": []}})
y += 4

row("Host (Node Exporter)", y); y += 1
stat("CPU em uso", f'100 * (1 - avg(rate(node_cpu_seconds_total{{{NODE}, mode="idle"}}[{RI}])))', 0, y, 6,
     unidade="percent", decimais=1, th=limiares((70, "orange"), (90, "red")))
stat("Memória em uso", f'100 * (1 - node_memory_MemAvailable_bytes{{{NODE}}} / node_memory_MemTotal_bytes{{{NODE}}})',
     6, y, 6, unidade="percent", decimais=1, th=limiares((80, "orange"), (90, "red")))
stat("Disco em uso (/)", f'100 * (1 - node_filesystem_avail_bytes{{{NODE}, mountpoint="/"}} / node_filesystem_size_bytes{{{NODE}, mountpoint="/"}})',
     12, y, 6, unidade="percent", decimais=1, th=limiares((70, "orange"), (85, "red")))
stat("Load average (1 min)", f'node_load1{{{NODE}}}', 18, y, 6, decimais=2, cor_fundo=False)
y += 4
serie("CPU por modo", [(f'sum by (mode) (rate(node_cpu_seconds_total{{{NODE}, mode!="idle"}}[{RI}]))', "{{mode}}")],
      0, y, 12, empilhar=True, minimo=0, desc="Segundos de CPU por segundo, somando os núcleos.")
serie("Memória", [(f'node_memory_MemTotal_bytes{{{NODE}}} - node_memory_MemAvailable_bytes{{{NODE}}}', "em uso"),
                  (f'node_memory_MemAvailable_bytes{{{NODE}}}', "disponível")],
      12, y, 12, unidade="bytes", empilhar=True, minimo=0)
y += 8
serie("Rede do host", [(f'sum(rate(node_network_receive_bytes_total{{{NODE}, device!~"lo|veth.*|docker.*|br-.*"}}[{RI}]))', "recebido"),
                       (f'sum(rate(node_network_transmit_bytes_total{{{NODE}, device!~"lo|veth.*|docker.*|br-.*"}}[{RI}]))', "enviado")],
      0, y, 8, unidade="Bps", minimo=0)
serie("Disco: leitura e escrita", [(f'sum(rate(node_disk_read_bytes_total{{{NODE}}}[{RI}]))', "leitura"),
                                   (f'sum(rate(node_disk_written_bytes_total{{{NODE}}}[{RI}]))', "escrita")],
      8, y, 8, unidade="Bps", minimo=0)
serie("Load average", [(f'node_load1{{{NODE}}}', "1 min"), (f'node_load5{{{NODE}}}', "5 min"),
                       (f'node_load15{{{NODE}}}', "15 min")], 16, y, 8, minimo=0)
y += 8

row("Contêineres (cAdvisor)", y); y += 1
serie("CPU por contêiner", [(f'sum by (name) (rate(container_cpu_usage_seconds_total{{{CONT}}}[{RI}]))', "{{name}}")],
      0, y, 12, unidade="percentunit", minimo=0, desc="Fração de um núcleo (1 = 100% de um núcleo).")
serie("Memória por contêiner (working set)", [(f'sum by (name) (container_memory_working_set_bytes{{{CONT}}})', "{{name}}")],
      12, y, 12, unidade="bytes", minimo=0,
      desc="A memória que o kernel considera em uso pelo contêiner: é a que conta para o limite e para o OOM.")
y += 8
serie("Rede por contêiner (recebido)", [(f'sum by (name) (rate(container_network_receive_bytes_total{{{CONT}}}[{RI}]))', "{{name}}")],
      0, y, 12, unidade="Bps", minimo=0)
barras("Memória atual por contêiner", f'sum by (name) (container_memory_working_set_bytes{{{CONT}}})', "{{name}}",
       12, y, 12, unidade="bytes", th=limiares((512 * 1024 * 1024, "orange")))
y += 8

gravar("infraestrutura.json", dashboard(
    "rota-vital-infra", "Rota Vital — Infraestrutura (host e contêineres)",
    "Infraestrutura: Node Exporter (host) e cAdvisor (contêineres), coletados pelo Prometheus.",
    ["rota-vital", "infraestrutura"], panels, []))
