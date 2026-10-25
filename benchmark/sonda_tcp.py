#!/usr/bin/env python3
"""
Sonda TCP: mede RTT, perda e jitter de rede ate o servidor (PI3-31).

Por que TCP e nao ping (ICMP)
-----------------------------
O ping seria o caminho classico, mas tem dois problemas aqui:

1. Os runners do GitHub Actions (hospedados na Azure) nao deixam sair ICMP:
   o ping falharia 100% e pareceria perda total.
2. ICMP pode ser tratado com prioridade diferente do trafego real da
   aplicacao pelos roteadores no caminho.

Abrir uma conexao TCP na porta 443 (a mesma que o navegador usa) mede o
handshake SYN -> SYN-ACK, que custa exatamente uma ida e volta (1 RTT). O
tempo do connect() e, portanto, uma estimativa do RTT pelo mesmo caminho do
trafego HTTPS. Nao precisa de root nem de nada instalado no servidor.

Quem responde o SYN e o kernel da VM, nao o Nginx nem a JVM: a sonda isola a
REDE. A latencia HTTP do k6 mede rede + Nginx + aplicacao; a diferenca entre
as duas aponta onde o tempo esta sendo gasto.

Definicoes usadas
-----------------
- RTT: tempo do connect() em ms.
- Perda: % de tentativas sem resposta dentro do timeout. Com timeout de 1 s,
  um SYN perdido aparece como perda: o primeiro reenvio do SYN pelo sistema
  operacional so acontece apos ~1 s (Linux) ou ~3 s (Windows).
- Jitter: media de |RTT(i) - RTT(i-1)| entre amostras consecutivas bem
  sucedidas, a mesma ideia do jitter entre pacotes da RFC 3550. Calculado
  pelo analisar_rede.py, nao aqui: este script so grava o dado bruto.

Uso
---
    python3 sonda_tcp.py --host rsc3-rotavital.duckdns.org --duracao 60 \\
        --saida dados-brutos/.../sonda-repouso.csv
"""

import argparse
import csv
import socket
import sys
import time
from datetime import datetime, timezone


def medir(endereco, timeout):
    """Abre e fecha uma conexao TCP. Devolve (rtt_ms, status)."""
    inicio = time.perf_counter()
    try:
        with socket.create_connection(endereco, timeout=timeout):
            fim = time.perf_counter()
        return (fim - inicio) * 1000.0, "ok"
    except socket.timeout:
        return None, "timeout"
    except OSError as erro:
        # Conexao recusada, rede inalcancavel etc. Conta como perda, mas com
        # o motivo registrado para nao confundir com congestionamento.
        return None, f"erro:{erro.__class__.__name__}"


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--host", required=True)
    p.add_argument("--porta", type=int, default=443)
    p.add_argument("--duracao", type=float, default=60.0, help="segundos de coleta")
    p.add_argument("--intervalo", type=float, default=0.2, help="segundos entre tentativas")
    p.add_argument("--timeout", type=float, default=1.0, help="segundos ate considerar perda")
    p.add_argument("--saida", required=True, help="arquivo CSV de saida")
    args = p.parse_args()

    # Resolve o DNS uma vez so. Sem isso, cada tentativa mediria tambem a
    # consulta DNS, que nao e o que queremos.
    try:
        ip = socket.getaddrinfo(args.host, args.porta, proto=socket.IPPROTO_TCP)[0][4][0]
    except socket.gaierror as erro:
        sys.exit(f"Nao foi possivel resolver {args.host}: {erro}")
    endereco = (ip, args.porta)

    fim_coleta = time.monotonic() + args.duracao
    proxima = time.monotonic()
    seq = 0
    ok = perdidas = 0

    with open(args.saida, "w", newline="", encoding="utf-8") as arquivo:
        escritor = csv.writer(arquivo)
        escritor.writerow(["seq", "timestamp_utc", "host", "ip", "porta", "rtt_ms", "status"])
        while time.monotonic() < fim_coleta:
            carimbo = datetime.now(timezone.utc).isoformat(timespec="milliseconds")
            rtt, status = medir(endereco, args.timeout)
            escritor.writerow([seq, carimbo, args.host, ip, args.porta,
                               "" if rtt is None else f"{rtt:.3f}", status])
            if rtt is None:
                perdidas += 1
            else:
                ok += 1
            seq += 1
            # Ritmo fixo: a proxima tentativa e agendada a partir do inicio
            # da anterior, nao do fim, para o intervalo nao esticar quando o
            # RTT sobe.
            proxima += args.intervalo
            espera = proxima - time.monotonic()
            if espera > 0:
                time.sleep(espera)

    total = ok + perdidas
    print(f"sonda {args.host}:{args.porta} -> {total} tentativas, {perdidas} perdidas "
          f"({100.0 * perdidas / total if total else 0:.2f}%)")


if __name__ == "__main__":
    main()
