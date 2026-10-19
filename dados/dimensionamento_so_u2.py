"""Memória de cálculo dos cenários SO U2; premissas, não benchmark.

Executar na raiz: python dados/dimensionamento_so_u2.py
Somente biblioteca padrão; não faz chamadas à aplicação.
"""

import json
from pathlib import Path


def calcular():
    resultado = []
    for nome, usuarios, rotas, intervalo, dias in [
        ("baixo", 5, 2, 30, 1),
        ("moderado", 25, 20, 15, 30),
        ("alto", 100, 100, 5, 30),
    ]:
        # 80% ações com 1 chamada + 20% aberturas do painel com 6 chamadas.
        negocio = usuarios / 10 * (0.8 + 0.2 * 6)
        telemetria = rotas / intervalo
        # Rajada: dobro da taxa de negócio + todas as rotas enviando em 1 s.
        pico = 2 * negocio + rotas
        leituras_dia = telemetria * 8 * 3600
        resultado.append({
            "cenario": nome,
            "sessoes_ativas": usuarios,
            "rotas_em_transito": rotas,
            "intervalo_leitura_segundos": intervalo,
            "retencao_dias": dias,
            "rps_negocio": negocio,
            "rps_telemetria": telemetria,
            "rps_total": negocio + telemetria,
            "rps_rajada_um_segundo": pico,
            "leituras_dia_8h": int(leituras_dia),
            "leituras_retidas": int(leituras_dia * dias),
            "telemetria_gib_estimados": leituras_dia * dias * 3 * 1024 / 1024**3,
            "cpu_app_nucleos_estimados": negocio * 0.05 + telemetria * 0.01,
            "cpu_app_nucleos_rajada": 2 * negocio * 0.05 + rotas * 0.01,
            "trafego_mbit_s_estimado": (negocio + telemetria) * 10 * 1024 * 8 / 1_000_000,
            "requisicoes_em_voo_media_alvo": (negocio + telemetria) * 0.2,
        })
    return {
        "natureza": "Estimativas de planejamento; não são resultados de teste de carga.",
        "premissas": {
            "segundos_entre_acoes_por_usuario": 10,
            "chamadas_medias_por_acao": 2,
            "horas_ativas_por_dia": 8,
            "bytes_por_leitura_com_indices_e_folga": 3072,
            "cpu_segundos_por_requisicao_negocio": 0.05,
            "cpu_segundos_por_leitura": 0.01,
            "bytes_medios_por_troca_http": 10240,
            "tempo_medio_resposta_alvo_segundos": 0.2,
        },
        "cenarios": resultado,
    }


if __name__ == "__main__":
    destino = Path(__file__).with_name("dimensionamento-so-u2.json")
    destino.write_text(json.dumps(calcular(), ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("Premissas e contas gravadas em dados/dimensionamento-so-u2.json")
