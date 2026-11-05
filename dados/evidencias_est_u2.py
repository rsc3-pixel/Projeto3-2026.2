"""Gera gráficos SVG da W14 e audita os resultados das entregas PI3-102/103.

Python 3, biblioteca padrão. --check confere os artefatos sem alterá-los.
"""
import argparse
import json
from pathlib import Path
from xml.sax.saxutils import escape

import validar_indicadores_u2 as v

RAIZ = Path(__file__).resolve().parents[1]
DESTINO = RAIZ / 'docs/img/est-u2'
NOMES = {'CONCENTRADO_PLAQUETAS': 'Plaquetas', 'CONCENTRADO_HEMACIAS': 'Hemácias',
         'PLASMA_FRESCO_CONGELADO': 'Plasma', 'CRIOPRECIPITADO': 'Crioprecipitado'}


def conferir():
    base = json.loads(v.DADOS.read_text(encoding='utf-8'))
    esperado = json.loads(v.ESPERADO.read_text(encoding='utf-8'))
    bolsas, reqs = base['bolsas'], base['requisicoes']
    assert all(b['vencida'] == (v.d(b['data_validade']) < v.REFERENCIA) for b in bolsas)
    evitaveis = v.vencidas_com_demanda_compativel(bolsas, reqs)
    calculado = {'data_referencia': v.REFERENCIA.isoformat(), 'bolsas': len(bolsas), 'requisicoes': len(reqs),
                 'descarte_por_componente': v.resumir_por_componente(bolsas, evitaveis),
                 'descarte_por_periodo': {g: v.descarte_por_periodo(bolsas, evitaveis, g) for g in ('SEMANA', 'MES')},
                 'desabastecimento': v.desabastecimento(bolsas, reqs)}
    assert calculado == esperado, 'Indicadores versionados divergem do recálculo'
    cpp = json.loads(v.PAINEL_CPP.read_text(encoding='utf-8'))['painel_consolidado']['desabastecimento_por_tipo']
    por_tipo = {r['tipo_sanguineo']: r for r in cpp}
    for r in calculado['desabastecimento']['por_tipo']:
        assert r['probabilidade'] == v.r1(100 * por_tipo[r['tipo']]['probabilidade_desabastecimento'])
    for g in ('SEMANA', 'MES'):
        assert sum(r['total'] for r in calculado['descarte_por_periodo'][g]) == len(bolsas)
        assert sum(r['vencidas'] for r in calculado['descarte_por_periodo'][g]) == sum(b['vencida'] for b in bolsas)
    return calculado


def grafico(titulo, subtitulo, linhas, rodape):
    altura = 155 + 44 * len(linhas)
    svg = [f'<svg xmlns="http://www.w3.org/2000/svg" width="1100" height="{altura}" role="img" viewBox="0 0 1100 {altura}">',
           f'<title>{escape(titulo)}</title><desc>{escape(subtitulo)}</desc>',
           '<rect width="100%" height="100%" fill="#ffffff"/>',
           '<g font-family="Arial,sans-serif" fill="#183247">',
           f'<text x="24" y="31" font-size="21" font-weight="bold">{escape(titulo)}</text>',
           f'<text x="24" y="56" font-size="14">{escape(subtitulo)}</text>']
    for i, (nome, pct, detalhe) in enumerate(linhas):
        y = 80 + 44 * i
        svg += [f'<text x="24" y="{y+18}" font-size="15">{escape(nome)}</text>',
                f'<rect x="200" y="{y}" width="450" height="26" fill="#edf1f5"/>',
                f'<rect x="200" y="{y}" width="{pct*4.5:.3f}" height="26" fill="#ab354a"/>',
                f'<text x="665" y="{y+18}" font-size="14">{pct:.1f}% · {escape(detalhe)}</text>']
    svg += [f'<text x="200" y="{altura-40}" font-size="12">Escala das barras: 0% a 100%</text>',
            f'<text x="24" y="{altura-16}" font-size="12">{escape(rodape)}</text>', '</g></svg>']
    return '\n'.join(svg) + '\n'


def artefatos(dados):
    tipos = dados['desabastecimento']['por_tipo']
    falta = grafico('Rota Vital — risco de desabastecimento por tipo sanguíneo',
                    'Dados sintéticos · Binomial Negativa · horizonte de 61 dias · estoque fixo, componentes agregados',
                    [(r['tipo'].replace('_POS', '+').replace('_NEG', '−'), r['probabilidade'],
                      f"estoque {r['estoque_valido']}; demanda média {r['demanda']}; déficit {r['deficit']}") for r in tipos],
                    'Fonte: indicadores_u2_esperado.json. Não representa probabilidade diária nem atendimento efetivo.')
    descarte = grafico('Rota Vital — bolsas vencidas por hemocomponente',
                      'Dados sintéticos · fotografia em 17/09/2026 · denominador: todas as bolsas do componente',
                      [(NOMES[r['componente']], r['taxa'], f"{r['vencidas']} vencidas / {r['total']} bolsas")
                       for r in dados['descarte_por_componente']],
                      'Vencimento observado na base sintética; não é previsão de descarte nem ganho medido de FEFO.')
    periodo = grafico('Rota Vital — vencimento por mês de coleta',
                     'Dados sintéticos · todos os lotes avaliados na mesma data: 17/09/2026',
                     [(r['periodo'], r['taxa'], f"{r['vencidas']} vencidas / {r['total']} coletadas")
                      for r in dados['descarte_por_periodo']['MES']],
                     'Lotes recentes tiveram menos tempo para vencer. A diferença não demonstra melhoria da operação.')
    return {'desabastecimento.svg': falta, 'descarte-componentes.svg': descarte, 'descarte-periodos.svg': periodo}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    dados = conferir()
    for nome, conteudo in artefatos(dados).items():
        destino = DESTINO / nome
        if args.check:
            assert destino.read_text(encoding='utf-8') == conteudo, f'Gráfico desatualizado: {nome}'
        else:
            DESTINO.mkdir(parents=True, exist_ok=True)
            destino.write_text(conteudo, encoding='utf-8')
    print('Conferidos: 8 tipos, 4 componentes, 10 semanas, 3 meses e 3 gráficos; BN consistente com C++.')
