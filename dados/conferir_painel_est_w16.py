"""Captura somente GETs públicos para a evidência W16; não modifica a aplicação.

python dados/conferir_painel_est_w16.py --saida docs/evidencias/w16-est
Não reexecute sobre a evidência histórica se quiser preservar a captura original.
"""
import argparse
import json
from datetime import datetime, timezone, timedelta
from pathlib import Path
from urllib.request import urlopen
from html.parser import HTMLParser


class Texto(HTMLParser):
    def __init__(self):
        super().__init__()
        self.partes = []
        self.ignorar = 0

    def handle_starttag(self, tag, attrs):
        if tag in ('script', 'style'):
            self.ignorar += 1

    def handle_endtag(self, tag):
        if tag in ('script', 'style'):
            self.ignorar -= 1

    def handle_data(self, data):
        if not self.ignorar and data.strip():
            self.partes.append(' '.join(data.split()))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base', default='https://rsc3-rotavital.duckdns.org')
    parser.add_argument('--saida', required=True, type=Path)
    args = parser.parse_args()
    args.saida.mkdir(parents=True, exist_ok=True)
    inicio = datetime.now(timezone(timedelta(hours=-3))).isoformat()
    paths = ['/actuator/health', '/painel/HC01'] + [
        '/api/v1/indicadores/' + p for p in ('estoque-por-tipo', 'estoque-por-componente',
        'cobertura', 'criticas', 'validade', 'desabastecimento', 'descarte',
        'descarte/por-periodo', 'descarte/por-periodo?granularidade=MES')]
    resultados = []
    for path in paths:
        with urlopen(args.base.rstrip('/') + path, timeout=30) as response:
            bruto = response.read().decode('utf-8')
            registro = {'path': path, 'status': response.status}
            if path == '/painel/HC01':
                texto = Texto()
                texto.feed(bruto)
                (args.saida / 'painel-texto.txt').write_text('\n'.join(texto.partes)+'\n', encoding='utf-8')
                registro['conteudo'] = 'painel-texto.txt'
            else:
                registro['resposta'] = json.loads(bruto)
            resultados.append(registro)
    evidencia = {'inicio_fortaleza': inicio, 'fim_fortaleza': datetime.now(timezone(timedelta(hours=-3))).isoformat(),
                 'base': args.base, 'observacao': 'GETs sequenciais; não constituem snapshot transacional. Valores podem mudar entre chamadas.',
                 'resultados': resultados}
    (args.saida / 'captura-http.json').write_text(json.dumps(evidencia, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    print(f'{len(resultados)} GETs concluídos e registrados em {args.saida}')


if __name__ == '__main__':
    main()
