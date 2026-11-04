#!/usr/bin/env python3
"""
Simulador de telemetria do Rota Vital (PI3-112).

>>> A TELEMETRIA E SIMULADA. Nao ha sensor, veiculo nem hardware real. <<<

Este programa faz o papel do sensor instalado no compartimento refrigerado
de cada veiculo: a cada intervalo, reporta temperatura e posicao (GPS) para a
API, pelo mesmo caminho que um sensor de verdade usaria:

    simulador --HTTPS/443--> Nginx (TLS) --HTTP/8081--> API Rota Vital

Por que um processo separado, e nao um agendador dentro da propria API: um
gerador interno nao passaria por rede nenhuma, e o objetivo da PI3-112 e
justamente colocar o CANAL de comunicacao no ar (e dar trafego real para o
benchmarking da PI3-31).

Ciclo de uma viagem (por veiculo)
---------------------------------
A API so aceita leitura de rota EM_TRANSITO, e so bolsa RESERVADA embarca.
Por isso cada veiculo percorre o fluxo completo do dominio:

    1. hemocentro registra uma bolsa sintetica (reposicao de estoque)
    2. hospital cria uma requisicao sintetica de 1 bolsa
    3. alocacao automatica (FEFO) reserva uma bolsa compativel
    4. rota do hemocentro da bolsa ate o hospital; embarque; saida
    5. leituras de temperatura + GPS a cada INTERVALO segundos
    6. chegada: rota CONCLUIDA; pausa; nova viagem

O passo 1 existe para o simulador nao esvaziar o estoque: cada viagem tira
uma bolsa e repoe outra. Como a FEFO escolhe a de validade mais proxima, as
bolsas da carga inicial saem primeiro e as repostas ficam no lugar delas.

Robustez
--------
O H2 e em memoria: cada deploy reinicia a API e apaga tudo. O simulador
trata isso como evento normal. Uma rota que some (404), ou que nao esta mais
em transito (409), encerra a viagem atual e comeca outra. Falha de rede ou
5xx conta como leitura perdida e o simulador segue no proximo intervalo.

So biblioteca padrao: roda no GitHub Actions, no notebook ou na VM, sem pip.

Uso
---
    python3 telemetria/simulador.py                       # 3 veiculos, para sempre
    python3 telemetria/simulador.py --duracao 3300 --saida leituras.jsonl
    python3 telemetria/simulador.py --base-url http://localhost:8080 --veiculos 1

Ctrl+C (ou SIGTERM) encerra as viagens abertas antes de sair.
"""

import argparse
import json
import math
import random
import signal
import sys
import time
import urllib.error
import urllib.request
from dataclasses import dataclass, field
from datetime import date, datetime, timedelta, timezone

AGENTE = "rota-vital-sensor-simulado/1.0 (PI3-112)"
MARCA = "SINTETICO - simulador de telemetria (PI3-112)"

# Faixas de transporte, iguais as do TipoHemocomponente da API. Usadas so
# para o modelo de temperatura saber em torno de que valor oscilar; quem
# decide se a leitura e alerta e sempre a API.
FAIXAS = {
    "CONCENTRADO_HEMACIAS": (1.0, 6.0),
    "PLASMA_FRESCO_CONGELADO": (-25.0, -18.0),
    "CONCENTRADO_PLAQUETAS": (20.0, 24.0),
    "CRIOPRECIPITADO": (-25.0, -18.0),
}
# Proporcao aproximada de cada componente nas viagens simuladas.
PESOS_COMPONENTE = {
    "CONCENTRADO_HEMACIAS": 60,
    "CONCENTRADO_PLAQUETAS": 20,
    "PLASMA_FRESCO_CONGELADO": 15,
    "CRIOPRECIPITADO": 5,
}
VOLUME_ML = {
    "CONCENTRADO_HEMACIAS": 300,
    "CONCENTRADO_PLAQUETAS": 250,
    "PLASMA_FRESCO_CONGELADO": 200,
    "CRIOPRECIPITADO": 50,
}
GRUPOS = ["O_POS", "A_POS", "B_POS", "AB_POS", "O_NEG", "A_NEG", "B_NEG", "AB_NEG"]
PESOS_GRUPO = [36, 34, 8, 3, 9, 6, 3, 1]  # distribuicao tipica, so para variar


# =============================================================================
# Modelos (sem rede): testaveis isoladamente em test_simulador.py
# =============================================================================

class ModeloTemperatura:
    """Temperatura do compartimento refrigerado ao longo da viagem.

    Um processo que volta para o centro da faixa (o refrigerador corrigindo)
    com ruido pequeno. De vez em quando acontece um "evento de porta aberta":
    a temperatura deriva para fora da faixa por algumas leituras e volta.
    E esse evento que gera os alertas de cadeia fria na API.

        T(i+1) = T(i) + k * (alvo - T(i)) + ruido

    k e a forca de correcao do refrigerador. Com k = 0,25 a temperatura volta
    a ~95% do alvo em ~10 leituras depois que a porta fecha.
    """

    CORRECAO = 0.25

    def __init__(self, componente, rng, prob_evento=0.02):
        self.minimo, self.maximo = FAIXAS[componente]
        self.centro = (self.minimo + self.maximo) / 2
        self.amplitude = self.maximo - self.minimo
        self.rng = rng
        self.prob_evento = prob_evento
        self.temperatura = self.centro + rng.gauss(0, self.amplitude * 0.05)
        self.leituras_restantes_evento = 0

    def proxima(self):
        if self.leituras_restantes_evento == 0 and self.rng.random() < self.prob_evento:
            self.leituras_restantes_evento = self.rng.randint(3, 6)

        if self.leituras_restantes_evento > 0:
            # Porta aberta: o alvo passa a ser o ambiente externo, acima da
            # faixa. Para os congelados, "acima" tambem e o lado perigoso.
            alvo = self.maximo + self.amplitude * 0.8
            self.leituras_restantes_evento -= 1
        else:
            alvo = self.centro

        ruido = self.rng.gauss(0, self.amplitude * 0.03)
        self.temperatura += self.CORRECAO * (alvo - self.temperatura) + ruido
        # A API recusa (400) o que um sensor real nao mediria.
        self.temperatura = max(-80.0, min(60.0, self.temperatura))
        return round(self.temperatura, 1)


def posicao_no_trajeto(origem, destino, fracao, rng=None, ruido_m=15.0):
    """Ponto do trajeto na fracao [0, 1] da viagem.

    Interpolacao em linha reta entre origem e destino (simplificacao: o
    simulador nao conhece a malha viaria). O ruido imita a imprecisao do GPS,
    da ordem de 15 m. Um grau de latitude tem ~111 km, entao 15 m sao
    ~0,000135 grau.
    """
    fracao = max(0.0, min(1.0, fracao))
    lat = origem[0] + (destino[0] - origem[0]) * fracao
    lon = origem[1] + (destino[1] - origem[1]) * fracao
    if rng is not None and ruido_m > 0:
        grau_lat = ruido_m / 111_000.0
        grau_lon = ruido_m / (111_000.0 * max(0.1, math.cos(math.radians(lat))))
        lat += rng.gauss(0, grau_lat)
        lon += rng.gauss(0, grau_lon)
    return round(lat, 6), round(lon, 6)


def sortear(rng, pesos):
    itens = list(pesos)
    return rng.choices(itens, weights=[pesos[i] for i in itens], k=1)[0]


# =============================================================================
# Cliente HTTP
# =============================================================================

class ErroApi(Exception):
    def __init__(self, status, corpo, contexto):
        super().__init__(f"{contexto}: HTTP {status} {corpo[:200]}")
        self.status = status
        self.contexto = contexto


class Api:
    def __init__(self, base_url, timeout):
        self.base = base_url.rstrip("/")
        self.timeout = timeout

    def chamar(self, metodo, caminho, corpo=None, contexto=""):
        """Devolve (json, ms). Levanta ErroApi para status >= 400 e
        OSError/URLError para falha de rede."""
        dados = None if corpo is None else json.dumps(corpo).encode("utf-8")
        req = urllib.request.Request(self.base + caminho, data=dados, method=metodo)
        req.add_header("User-Agent", AGENTE)
        req.add_header("Accept", "application/json")
        if dados is not None:
            req.add_header("Content-Type", "application/json")
        inicio = time.perf_counter()
        try:
            with urllib.request.urlopen(req, timeout=self.timeout) as resp:
                texto = resp.read().decode("utf-8")
                ms = (time.perf_counter() - inicio) * 1000
                return (json.loads(texto) if texto else None), ms
        except urllib.error.HTTPError as erro:
            texto = erro.read().decode("utf-8", "replace")
            raise ErroApi(erro.code, texto, contexto or f"{metodo} {caminho}") from None


# =============================================================================
# Veiculo: maquina de estados de uma viagem
# =============================================================================

def agora_local_iso(deslocamento_min=0):
    """LocalDateTime sem fuso, como a API espera."""
    return (datetime.now() + timedelta(minutes=deslocamento_min)).replace(microsecond=0).isoformat()


@dataclass
class Viagem:
    rota_id: str
    componente: str
    origem: tuple
    destino: tuple
    inicio: float
    duracao_s: float
    modelo: ModeloTemperatura
    leituras: int = 0


@dataclass
class Veiculo:
    nome: str
    viagem: Viagem = None
    proxima_acao: float = 0.0
    falhas_seguidas: int = 0
    estatisticas: dict = field(default_factory=lambda: {
        "viagens": 0, "leituras_enviadas": 0, "leituras_aceitas": 0,
        "leituras_com_alerta": 0, "leituras_perdidas": 0})


class Simulador:
    def __init__(self, args, log, saida):
        self.args = args
        self.api = Api(args.base_url, args.timeout)
        self.rng = random.Random(args.semente)
        self.log = log
        self.saida = saida
        self.veiculos = [Veiculo(f"VEICULO-SIM-{i + 1:02d}") for i in range(args.veiculos)]
        # Escalona o inicio para os veiculos nao mandarem tudo no mesmo segundo.
        for i, v in enumerate(self.veiculos):
            v.proxima_acao = time.monotonic() + i * (args.intervalo / max(1, args.veiculos))
        self.parar = False

    # --- Preparacao da viagem ------------------------------------------------

    def iniciar_viagem(self, v):
        hemocentros, _ = self.api.chamar("GET", "/api/v1/hemocentros", contexto="listar hemocentros")
        hospitais, _ = self.api.chamar("GET", "/api/v1/hospitais", contexto="listar hospitais")
        if not hemocentros or not hospitais:
            raise RuntimeError("API sem hemocentro ou sem hospital cadastrado")
        coord = {u["id"]: (u["endereco"]["latitude"], u["endereco"]["longitude"])
                 for u in hemocentros + hospitais if u.get("endereco")}

        componente = sortear(self.rng, PESOS_COMPONENTE)
        grupo = self.rng.choices(GRUPOS, weights=PESOS_GRUPO, k=1)[0]
        hemocentro = self.rng.choice(hemocentros)
        hospital = self.rng.choice(hospitais)

        # 1. Reposicao: o hemocentro registra uma bolsa sintetica.
        self.api.chamar("POST", "/api/v1/bolsas", {
            "hemocentroId": hemocentro["id"],
            "tipoHemocomponente": componente,
            "grupoSanguineo": grupo,
            "volumeMl": VOLUME_ML[componente],
            "dataColeta": date.today().isoformat(),
        }, "repor bolsa")

        # 2. Requisicao sintetica do hospital.
        requisicao, _ = self.api.chamar("POST", "/api/v1/requisicoes", {
            "hospitalId": hospital["id"],
            "prioridade": "ROTINA",
            "observacao": f"{MARCA} - {v.nome}",
            "itens": [{"tipoHemocomponente": componente, "grupoSanguineo": grupo,
                       "quantidadeSolicitada": 1}],
        }, "criar requisicao")
        item = requisicao["itens"][0]

        # 3. Alocacao automatica (FEFO + compatibilidade, regra da API).
        alocacao, _ = self.api.chamar(
            "POST", f"/api/v1/requisicoes/{requisicao['id']}/itens/{item['id']}/alocacoes/automatica",
            None, "alocar bolsa")
        bolsa, _ = self.api.chamar("GET", f"/api/v1/bolsas/{alocacao['bolsaId']}", contexto="buscar bolsa")

        # A bolsa escolhida pode estar em outro hemocentro: a rota sai de onde
        # ela esta, nao de onde a reposicao entrou.
        origem_id = bolsa["hemocentroId"]
        duracao_min = self.rng.uniform(self.args.viagem_min, self.args.viagem_max)

        # 4. Rota, embarque e saida.
        rota, _ = self.api.chamar("POST", "/api/v1/rotas", {
            "hemocentroOrigemId": origem_id,
            "hospitalDestinoId": hospital["id"],
            "previsaoSaida": agora_local_iso(),
            "previsaoChegada": agora_local_iso(duracao_min),
        }, "criar rota")
        self.api.chamar("POST", f"/api/v1/rotas/{rota['id']}/bolsas", {"bolsaId": bolsa["id"]}, "embarcar")
        self.api.chamar("PATCH", f"/api/v1/rotas/{rota['id']}", {"status": "EM_TRANSITO"}, "sair em transito")

        v.viagem = Viagem(
            rota_id=rota["id"],
            componente=bolsa["tipoHemocomponente"],
            origem=coord.get(origem_id, (-8.05, -34.90)),
            destino=coord.get(hospital["id"], (-8.05, -34.90)),
            inicio=time.monotonic(),
            duracao_s=duracao_min * 60,
            modelo=ModeloTemperatura(bolsa["tipoHemocomponente"], self.rng, self.args.prob_evento),
        )
        v.estatisticas["viagens"] += 1
        self.log(f"{v.nome} saiu: rota {rota['id'][:8]} {origem_id} -> {hospital['id']} "
                 f"({bolsa['tipoHemocomponente']}, ~{duracao_min:.1f} min)")

    # --- Leitura -----------------------------------------------------------------

    def enviar_leitura(self, v):
        via = v.viagem
        fracao = (time.monotonic() - via.inicio) / via.duracao_s
        lat, lon = posicao_no_trajeto(via.origem, via.destino, fracao, self.rng)
        temperatura = via.modelo.proxima()
        registro = {
            "ts_utc": datetime.now(timezone.utc).isoformat(timespec="milliseconds"),
            "veiculo": v.nome, "rota_id": via.rota_id, "componente": via.componente,
            "temperaturaCelsius": temperatura, "latitude": lat, "longitude": lon,
        }
        v.estatisticas["leituras_enviadas"] += 1
        try:
            resposta, ms = self.api.chamar(
                "POST", f"/api/v1/rotas/{via.rota_id}/temperaturas",
                {"temperaturaCelsius": temperatura, "latitude": lat, "longitude": lon},
                "enviar leitura")
            v.estatisticas["leituras_aceitas"] += 1
            alerta = not resposta.get("dentroDaFaixa", True)
            if alerta:
                v.estatisticas["leituras_com_alerta"] += 1
            registro.update(status=201, ms=round(ms, 1), dentro_da_faixa=not alerta)
            via.leituras += 1
            if alerta:
                self.log(f"{v.nome} ALERTA cadeia fria: {temperatura} C ({via.componente})")
        except ErroApi as erro:
            registro.update(status=erro.status)
            v.estatisticas["leituras_perdidas"] += 1
            if erro.status in (404, 409):
                # Rota sumiu (API reiniciou e o H2 zerou) ou nao esta mais em
                # transito. Nao adianta insistir: comeca outra viagem.
                self.log(f"{v.nome} rota {via.rota_id[:8]} indisponivel (HTTP {erro.status}); nova viagem")
                v.viagem = None
            else:
                self.log(f"{v.nome} leitura recusada: {erro}")
        except (urllib.error.URLError, OSError) as erro:
            registro.update(status=0, erro=str(erro)[:120])
            v.estatisticas["leituras_perdidas"] += 1
            self.log(f"{v.nome} falha de rede: {erro}")
        if self.saida:
            self.saida.write(json.dumps(registro, ensure_ascii=False) + "\n")
            self.saida.flush()

    def concluir(self, v, motivo):
        via = v.viagem
        v.viagem = None
        try:
            self.api.chamar("PATCH", f"/api/v1/rotas/{via.rota_id}", {"status": "CONCLUIDA"}, "concluir rota")
            self.log(f"{v.nome} chegou ({motivo}): rota {via.rota_id[:8]}, {via.leituras} leituras")
        except (ErroApi, urllib.error.URLError, OSError) as erro:
            # A rota pode ter sumido com um reinicio da API; nada a desfazer.
            self.log(f"{v.nome} nao conseguiu concluir a rota {via.rota_id[:8]}: {erro}")

    # --- Laco principal ------------------------------------------------------

    def passo(self, v):
        agora = time.monotonic()
        try:
            if v.viagem is None:
                self.iniciar_viagem(v)
                v.falhas_seguidas = 0
                v.proxima_acao = agora + self.args.intervalo
                return
            if agora - v.viagem.inicio >= v.viagem.duracao_s:
                self.concluir(v, "fim do trajeto")
                # Descarga e retorno do veiculo antes da proxima viagem.
                v.proxima_acao = agora + self.rng.uniform(0.5, 2.0) * self.args.pausa
                return
            self.enviar_leitura(v)
            v.proxima_acao = agora + self.args.intervalo
        except (ErroApi, urllib.error.URLError, OSError, RuntimeError, KeyError, ValueError) as erro:
            # Falha ao preparar a viagem (API reiniciando, sem estoque
            # compativel = 409 na alocacao...). Espera cada vez mais, ate 5 min.
            v.falhas_seguidas += 1
            espera = min(300, 15 * 2 ** (v.falhas_seguidas - 1))
            self.log(f"{v.nome} nao conseguiu preparar a viagem ({erro}); nova tentativa em {espera}s")
            v.viagem = None
            v.proxima_acao = agora + espera

    def rodar(self):
        fim = time.monotonic() + self.args.duracao if self.args.duracao > 0 else math.inf
        while not self.parar and time.monotonic() < fim:
            v = min(self.veiculos, key=lambda x: x.proxima_acao)
            espera = v.proxima_acao - time.monotonic()
            if espera > 0:
                time.sleep(min(espera, max(0.0, fim - time.monotonic()), 1.0))
                continue
            self.passo(v)
        for v in self.veiculos:
            if v.viagem is not None:
                self.concluir(v, "simulador encerrado")
        return self.resumo()

    def resumo(self):
        total = {k: sum(v.estatisticas[k] for v in self.veiculos) for k in self.veiculos[0].estatisticas}
        self.log("resumo: " + ", ".join(f"{k}={n}" for k, n in total.items()))
        return total


def main(argv=None):
    p = argparse.ArgumentParser(description="Simulador de telemetria (SIMULADA) do Rota Vital - PI3-112")
    p.add_argument("--base-url", default="https://rsc3-rotavital.duckdns.org")
    p.add_argument("--veiculos", type=int, default=3, help="veiculos simultaneos (padrao 3)")
    p.add_argument("--intervalo", type=float, default=30.0, help="segundos entre leituras de um veiculo")
    p.add_argument("--viagem-min", type=float, default=20.0, help="duracao minima da viagem, em minutos")
    p.add_argument("--viagem-max", type=float, default=40.0, help="duracao maxima da viagem, em minutos")
    p.add_argument("--pausa", type=float, default=60.0,
                   help="pausa media entre viagens de um veiculo, em segundos (varia de 0,5x a 2x)")
    p.add_argument("--prob-evento", type=float, default=0.02,
                   help="probabilidade, por leitura, de um evento de porta aberta")
    p.add_argument("--duracao", type=float, default=0, help="segundos de execucao; 0 = ate ser interrompido")
    p.add_argument("--timeout", type=float, default=20.0, help="timeout de cada requisicao, em segundos")
    p.add_argument("--semente", type=int, default=None, help="semente aleatoria, para reproduzir uma execucao")
    p.add_argument("--saida", help="arquivo JSONL com uma linha por leitura enviada")
    args = p.parse_args(argv)
    if args.veiculos < 1 or args.intervalo <= 0 or args.viagem_min <= 0 or args.viagem_max < args.viagem_min or args.pausa < 0:
        p.error("parametros invalidos: veiculos >= 1, intervalo > 0, 0 < viagem-min <= viagem-max")

    def log(msg):
        print(f"{datetime.now().strftime('%Y-%m-%d %H:%M:%S')} {msg}", flush=True)

    saida = open(args.saida, "a", encoding="utf-8") if args.saida else None
    sim = Simulador(args, log, saida)

    def interromper(*_):
        sim.parar = True
    signal.signal(signal.SIGINT, interromper)
    signal.signal(signal.SIGTERM, interromper)

    log(f"simulador iniciado: {args.veiculos} veiculo(s), leitura a cada {args.intervalo:g}s, "
        f"destino {args.base_url}. TELEMETRIA SIMULADA.")
    try:
        sim.rodar()
    finally:
        if saida:
            saida.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
