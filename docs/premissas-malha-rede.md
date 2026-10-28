# Premissas da malha da rede de distribuição (PI3-19)

Documento de registro das premissas usadas para gerar as arestas do grafo das
12 unidades reais. Segue o mesmo princípio de `dados/premissas.py`: todo número
aqui é uma decisão, não um dado medido, e está reunido em um lugar só para que
possa ser questionado e trocado.

## O problema

O `dados/unidades.py` já traz as 12 unidades reais da rede, com coordenadas
geográficas. O que não existe em lugar nenhum do projeto são as **arestas**:
quais unidades se ligam diretamente e quantos minutos leva cada trecho.

Sem isso o Dijkstra não roda, porque é justamente o tempo de percurso que ele
minimiza (decisão registrada na seção 3 do `escopo-grafo-rede-distribuicao.md`).

Nenhum membro do grupo havia levantado tempos reais entre as unidades. A opção
de consultar uma API de rotas (Google Distance Matrix, OSRM) foi descartada
para esta sprint porque exigiria chave de API e acesso à rede durante os
testes, e o pipeline de CI roda sem isso — os testes quebrariam no PR.

A saída foi derivar os tempos das coordenadas que já temos, com premissas
explícitas.

## As premissas

### 1. Velocidade média de percurso: 25 km/h

Velocidade média de veículo em deslocamento urbano na Região Metropolitana do
Recife, considerando semáforos, congestionamento e trechos de via local. Não é
a velocidade de via livre nem o limite legal: é a velocidade efetiva porta a
porta, que é o que interessa para estimar tempo de entrega.

É o número mais frágil deste documento e o primeiro a ser substituído caso o
grupo consiga tempos reais.

### 2. Fator de sinuosidade: 1,4

A distância calculada a partir das coordenadas é em linha reta, e veículo não
anda em linha reta. O fator converte distância geodésica em distância de via.

O valor 1,4 (percurso 40% maior que a linha reta) é a faixa usualmente adotada
para malha urbana densa. Em cidade com traçado irregular e barreiras naturais
— e o Recife tem rios, pontes e canais — o desvio real tende a ficar nessa
ordem ou acima dela.

### 3. Vizinhos diretos por unidade: 4

Cada unidade recebe aresta para as 4 unidades geograficamente mais próximas.

A justificativa vem do próprio escopo do grafo (seção 5): a rede foi modelada
como **esparsa**, e a escolha de lista de adjacência sobre matriz foi
justificada por essa esparsidade. Ligar todas as unidades com todas (132
arestas dirigidas) contrariaria a modelagem já aprovada e transformaria o
Dijkstra num cálculo trivial de aresta direta, sem caminho intermediário.

Com 4 vizinhos e reciprocidade, o grafo fica com **64 arestas dirigidas** —
pouco menos da metade do total possível.

### 4. Reciprocidade das arestas: mesmo peso nos dois sentidos

O grafo é dirigido (decisão da seção 4 do escopo), e a justificativa registrada
lá é que ida e volta não levam necessariamente o mesmo tempo.

Nesta sprint, porém, o tempo é **derivado da distância**, e distância é
simétrica. Então A->B e B->A recebem o mesmo peso.

Isso é uma **simplificação assumida**, não uma mudança de decisão: a estrutura
continua dirigida e aceita pesos diferentes por sentido. Quando houver dados
reais de tempo por sentido, basta alimentar valores distintos, sem alterar o
código.

A reciprocidade também garante que o grafo fique conectado: se A escolhe B como
vizinho, B passa a alcançar A mesmo que B não tivesse escolhido A. Por isso
algumas unidades acabam com mais de 4 vizinhos.

## A fórmula

```
distancia_km = haversine(lat1, lon1, lat2, lon2)      # linha reta, raio 6371 km
distancia_via_km = distancia_km * 1,4                 # fator de sinuosidade
tempo_min = distancia_via_km / 25 * 60                # velocidade média
```

Haversine é a fórmula padrão de distância entre dois pontos sobre a superfície
da Terra a partir de latitude e longitude.

## Resultado

- 12 vértices
- 64 arestas dirigidas (32 pares recíprocos)
- Grafo conectado: a partir de qualquer unidade é possível alcançar as outras 11

### Alguns tempos calculados, a partir do HEMOPE (HC01)

| Destino | Unidade | Tempo mínimo |
|---|---|---|
| HC03 | IHENE (Boa Vista) | 2,1 min |
| HC02 | GSH Hemato (Boa Vista) | 2,9 min |
| HC04 | Real Português (Paissandu) | 4,2 min |
| HC05 | IMIP (Boa Vista) | 5,9 min |
| HC06 | HC UFPE (Cidade Universitária) | 18,0 min |
| HC07 | Tricentenário (Olinda) | 25,4 min |
| HC09 | N. Sra. de Lourdes (Jaboatão) | 34,0 min |
| HC08 | Hemolab (Rio Doce, Olinda) | 39,5 min |
| HC11 | Guararapes (Jaboatão) | 44,3 min |
| HC12 | Memorial Jaboatão | 50,1 min |

Os valores são plausíveis para quem conhece a região: deslocamento entre
unidades da Boa Vista em poucos minutos, e travessia Recife -> Jaboatão na casa
dos 45 minutos.

### Um detalhe que mostra o Dijkstra funcionando

A aresta direta HC01 -> HC05 (HEMOPE -> IMIP) custa 6,0 minutos. Mas o caminho
mínimo calculado é **5,9 minutos**, passando por HC02 (2,9 + 3,0). O algoritmo
encontra o desvio mais rápido que a ligação direta — exatamente o mesmo efeito
demonstrado no exemplo manual de 5 unidades do documento de escopo.

## Como trocar os números

Todas as premissas estão como constantes nomeadas em uma classe própria.
Trocar a velocidade média, o fator de sinuosidade ou o número de vizinhos é
alterar uma linha cada e rodar os testes.

## Pendências para o grupo

1. Alguém tem tempos reais entre as unidades? Substituem tudo isto.
2. 25 km/h e fator 1,4 são aceitáveis como premissa desta sprint?
3. 4 vizinhos por unidade está de acordo com a modelagem esparsa aprovada?
4. Modelar assimetria de ida e volta fica para quando houver dado real?
