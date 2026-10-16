# W10 — Roteirização por vizinho mais próximo (AED Unidade 2)

## 1. Objetivo

A Unidade 2 de Algoritmos e Estruturas de Dados pede **roteirização por busca de vizinho mais próximo**. Na Unidade 1 o projeto já havia resolvido o problema do *caminho mínimo entre dois pontos* com Dijkstra; a U2 muda a pergunta: dado um ponto de partida e **vários** pontos de entrega, em que **ordem** visitá-los.

No RotaVital isso corresponde à operação real de um veículo que sai de um hemocentro carregado e precisa atender uma lista de hospitais na mesma viagem. O peso das arestas continua sendo **tempo estimado de percurso em minutos**, pela mesma razão registrada na seção 3 do [W02](W02-escopo-grafo-rede-distribuicao.md): hemocomponente tem validade curta e cadeia fria, então o que importa minimizar é tempo, não quilometragem.

A implementação é uma classe Java pura, desacoplada do framework web, para que a camada de POO possa consumi-la diretamente.

## 2. O algoritmo

A heurística é **gulosa**: a cada passo vai ao ponto de entrega ainda não visitado que estiver mais perto da posição atual, e repete dali.

1. `atual` recebe a origem; `pendentes` recebe os pontos de entrega, sem repetidos, sem nulos e sem a própria origem.
2. Enquanto houver pendentes:
   1. medir a distância de `atual` até **todos** os pendentes de uma vez;
   2. escolher o pendente de **menor** distância entre os que têm caminho;
   3. se nenhum tiver caminho, **interromper** o laço;
   4. reconstruir o trecho `atual → escolhido`, concatenar ao caminho total sem repetir o vértice de junção, somar o custo;
   5. registrar o escolhido na ordem de visita, removê-lo dos pendentes e fazer `atual = escolhido`.
3. O que sobrou em `pendentes` são os **pontos não atendidos**.

```
roteiro(origem, pontos):
    atual      <- origem
    pendentes  <- pontos sem nulos, sem repetidos, sem origem
    caminho    <- [origem];  custo <- 0;  visitados <- []

    enquanto pendentes nao vazio:
        distancias <- dijkstra(atual)
        escolhido  <- pendente de menor distancias[p], com p em distancias
        se escolhido == nulo: interrompe          // ninguem alcancavel

        trecho <- rota(atual, escolhido)
        caminho <- caminho + trecho[1..]          // descarta a juncao
        custo   <- custo + trecho.custo
        visitados <- visitados + escolhido
        pendentes <- pendentes - escolhido
        atual     <- escolhido

    devolve (visitados, caminho, pendentes, custo)
```

## 3. Equivalência com a versão em C/C++

> **Nota de honestidade.** Não existe no repositório código em C ou C++ de vizinho mais próximo. O diretório [`aed/`](../aed/) contém apenas a Unidade 1 — [`aed/unidade1/c/`](../aed/unidade1/c/) e [`aed/unidade1/cpp/`](../aed/unidade1/cpp/), com lista encadeada, fila FIFO e pilha LIFO. Não há `aed/unidade2`, nem nenhum arquivo com matriz de distâncias ou vetor `visitado[]`. A comparação abaixo é, portanto, com o **algoritmo clássico** como descrito na literatura, e não com um arquivo específico do repositório.

A versão clássica em C/C++ assume uma **matriz de distâncias** `dist[n][n]` já preenchida e um vetor `visitado[n]`:

```c
int atual = origem;
visitado[atual] = 1;
for (int passo = 1; passo < n; passo++) {
    int melhor = -1;
    double menor = INF;
    for (int j = 0; j < n; j++) {
        if (!visitado[j] && dist[atual][j] < menor) {
            menor  = dist[atual][j];
            melhor = j;
        }
    }
    if (melhor == -1) break;          /* nada alcancavel */
    visitado[melhor] = 1;
    custo += menor;
    atual = melhor;
}
```

O mapeamento para o Java, elemento por elemento:

| Versão clássica em C/C++ | Versão Java do RotaVital | Por quê |
|---|---|---|
| `dist[atual][j]`, matriz pré-calculada | `Dijkstra.calcularDistancias(atual)` | A malha é **esparsa** (seção 5 do W02): cada unidade liga só às 4 mais próximas, então a maioria dos pares não tem aresta direta. Uma matriz exigiria O(V²) de espaço e, pior, consultar aresta direta daria "sem caminho" para pares perfeitamente alcançáveis por duas ou três pernas. |
| `int visitado[n]` | `LinkedHashSet<T> pendentes` | Inverte o sentido — guarda quem **falta**, não quem já foi — o que torna a condição de parada o próprio esvaziamento. E, por preservar a ordem de inserção, resolve o desempate sem vetor auxiliar. |
| `for (passo...)` com busca do menor | `while (!pendentes.isEmpty())` em `calcularRoteiro` | Mesma estrutura gulosa; o laço interno de busca do menor virou o método privado `maisProximoEntre`. |
| `menor = INF` e teste `< menor` | ausência da chave no mapa + comparação `<` estrita | O Dijkstra devolve **apenas os alcançáveis**: ausência significa "sem caminho", sem valor sentinela. A comparação estrita preserva o primeiro em caso de empate. |
| `if (melhor == -1) break;` | `if (escolhido == null) break;` + `pontosInalcancaveis` | O C clássico apenas para; aqui o que sobrou é **registrado** e devolvido ao chamador. |
| `custo += menor` | `custoTotal += trecho.custoTotal()` | Equivalente, mas o Java também reconstrói o caminho vértice a vértice. |
| *(não existe)* | `caminhoCompleto` | A matriz não guarda por onde se passa. Com grafo esparso, o trajeto atravessa unidades intermediárias, e saber quais importa para o despacho. |

A diferença conceitual é uma só: **onde está a distância**. O C clássico recebe um número pronto de uma matriz; o Java calcula o caminho mínimo sob demanda, porque na malha real a distância útil não é a da aresta, é a do caminho.

## 4. Reaproveitamento da Unidade 1

Três estruturas da U1 foram usadas **sem nenhuma alteração**:

- **[`Grafo<T>`](../src/main/java/com/rotavital/estruturas/Grafo.java)** — lista de adjacência, dirigido. Entra como dependência do construtor; os testes também o consultam por `contemAresta` para confirmar que o caminho é percorrível.
- **[`Dijkstra<T>`](../src/main/java/com/rotavital/estruturas/Dijkstra.java)** — entra duas vezes por passo: `calcularDistancias(atual)` mede todos os pendentes de uma vez, e `calcularRota(atual, escolhido)` reconstrói o trecho. Caminho mínimo **não foi reimplementado**.
- **[`ResultadoRota<T>`](../src/main/java/com/rotavital/estruturas/ResultadoRota.java)** — valor de retorno de cada trecho e **molde** do `ResultadoRoteiro`: classe final, construtor privado, fábrica estática, `List.copyOf`, `equals`/`hashCode`/`toString`.

O contrato do `calcularDistancias` — devolver mapa vazio para origem inexistente e omitir os inalcançáveis em vez de usar `POSITIVE_INFINITY` — é o que faz as bordas funcionarem sem caso especial no código novo.

## 5. Decisões técnicas

**Distância por caminho mínimo, não por aresta direta.** Já justificado na seção 3. O teste `escolhaUsaCaminhoMinimoENaoArestaDireta` fixa a diferença: no cenário dele, decidir por aresta direta inverteria a ordem de visita.

**Desempate pela ordem de entrada.** Pendentes em `LinkedHashSet`, percorridos nessa ordem, com troca de candidato apenas por `<` estrito. Sem critério estável, a mesma entrada poderia produzir roteiros diferentes entre execuções e não haveria como auditar a escolha — o mesmo cuidado que a regra de desempate da FEFO adota (seção "Regra de desempate" do W02).

**Inalcançável é resultado, não exceção.** Ponto sem caminho é situação normal da operação. Lançar descartaria o roteiro parcial já calculado, que é justamente a informação útil: saber que seis das oito entregas são viáveis vale mais que um erro mudo. O projeto já tomara essa decisão em `ResultadoRota.semCaminho()` e em `MotivoFalhaAlocacao`.

**Bordas.** Lista vazia, origem fora do grafo, ponto de entrega fora do grafo, ponto repetido, origem dentro da lista e elemento nulo na lista produzem **resultado tratado**. Apenas origem nula, lista nula e grafo nulo lançam `IllegalArgumentException`, por serem erro de programa de quem chamou. A assimetria entre "nulo dentro da lista" e "nulo como argumento" segue o [`ServicoAlocacaoRota`](../src/main/java/com/rotavital/alocacao/ServicoAlocacaoRota.java), que descarta bolsa nula no estoque mas lança para chave ou lista nulas.

**Desacoplamento.** `VizinhoMaisProximo` e `ResultadoRoteiro` importam somente `java.util`. Continuam genéricos em `T`: a malha entra apenas nos testes, que são o consumidor.

## 6. Complexidade

**O(k · (V + E) log V)** para k pontos de entrega, V vértices e E arestas.

O laço executa no máximo k vezes, uma por ponto visitado. Cada iteração roda dois Dijkstra — um `calcularDistancias` para medir todos os pendentes de uma vez e um `calcularRota` para reconstruir o trecho — e cada Dijkstra com heap binário custa O((V + E) log V). A constante 2 não altera a ordem de grandeza. A busca do menor entre os pendentes é O(k) por passo e fica dominada pelo Dijkstra, já que k ≤ V.

Na malha sintética atual, medidos no `CarregadorMalha`:

| Grandeza | Valor |
|---|---:|
| V (unidades) | 12 |
| E (arestas dirigidas) | 64 |
| Vizinhos por unidade (`PremissasMalha`) | 4 |
| k (hospitais do roteiro) | 8 |

Com V = 12 e E = 64 o custo é irrelevante na prática; a ordem de grandeza importa para o caso de a rede crescer.

## 7. Rota na malha sintética

Obtida pelo teste `roteiroNaMalhaRealAtendeTodosOsHospitais`, partindo do HEMOPE para as oito unidades do tipo `AGENCIA_TRANSFUSIONAL`:

```
Origem:            HC01 — Hemocentro Recife (HEMOPE)
Pontos pedidos:    HC04, HC05, HC06, HC07, HC09, HC10, HC11, HC12
Ordem de visita:   HC04, HC05, HC06, HC09, HC12, HC11, HC10, HC07
Caminho completo:  HC01 → HC04 → HC05 → HC04 → HC06 → HC09 → HC12 → HC11 → HC10 → HC05 → HC02 → HC07
Custo total:       173,6 minutos
alcancouTodos:     true
```

O caminho tem 12 vértices, repete HC04 e HC05 e passa por HC02, que não é ponto de entrega. Isso é esperado numa malha esparsa: ir de um hospital a outro atravessa unidades intermediárias. É exatamente por isso que `ordemDeVisita` e `caminhoCompleto` são listas separadas.

## 8. Limitação: a heurística não garante o ótimo

O vizinho mais próximo decide olhando **só o passo seguinte**. Uma escolha barata agora pode obrigar a um trecho caro depois, e o roteiro final pode custar mais que outra ordem possível. Encontrar a ordem ótima é o **problema do caixeiro viajante**, de custo exponencial; a heurística entrega uma ordem boa em tempo polinomial, que é o que a operação precisa.

Isso está documentado em teste, não só em prosa. O teste `roteiroGulosoPodeCustarMaisQueAOrdemAlternativa` monta o grafo mínimo que expõe o problema:

```
CD --1--> P1 --100--> P2
CD --2--> P2 ---2---> P1
```

Do CD, P1 está a 1 e P2 a 2, então o guloso vai a P1 primeiro e fecha o roteiro em **101 minutos**. Visitar P2 primeiro custaria **4 minutos** (2 + 2) — o teste calcula esse valor com o Dijkstra e assere que é menor. É uma limitação conhecida, não um defeito da implementação.

## 9. Rastreabilidade

**PI3-94 — W10, Roteirização por vizinho mais próximo em Java (AED)**, dividida em três subtarefas:

| Subtarefa | Entrega | PR | Branch |
|---|---|---|---|
| **PI3-148** | `VizinhoMaisProximo<T>` e `ResultadoRoteiro<T>` | #52 (base `main`) | `feat/vizinho-mais-proximo-pi3-XXX` |
| **PI3-149** | Pontos inalcançáveis, bordas a) a g), aplicação na malha sintética | #53 (base da PI3-148) | `feat/vizinho-sem-caminho-pi3-149` |
| **PI3-150** | Fechamento da cobertura de testes e este documento | base da PI3-149 | `feat/vizinho-testes-doc-pi3-150` |

Observações de registro:

- Os commits da **PI3-148 levam "(PI3-149)" na mensagem, por engano**; a equipe decidiu não corrigir, para não reescrever histórico de uma branch já com PR aberto.
- Pelo mesmo motivo, as **evidências da PI3-148 e da PI3-149 estão no mesmo arquivo**, [`docs/evidencias-pi3-149.md`](evidencias-pi3-149.md) — a da 148 primeiro, a da 149 depois do separador. As desta subtarefa estão em [`docs/evidencias-pi3-150.md`](evidencias-pi3-150.md).

Arquivos da entrega:

- [`VizinhoMaisProximo.java`](../src/main/java/com/rotavital/estruturas/VizinhoMaisProximo.java) — PI3-148, estendido na PI3-149
- [`ResultadoRoteiro.java`](../src/main/java/com/rotavital/estruturas/ResultadoRoteiro.java) — PI3-148, estendido na PI3-149
- [`VizinhoMaisProximoTest.java`](../src/test/java/com/rotavital/estruturas/VizinhoMaisProximoTest.java) — 3 testes na PI3-148, 16 na PI3-149, 24 na PI3-150

**Critérios de aceite da PI3-94:**

1. *Rota calculada para os pontos de entrega da malha sintética* — atendido na PI3-149, testes `roteiroNaMalhaRealAtendeTodosOsHospitais` e `custoTotalEhASomaDosTrechos`; rota registrada na seção 7.
2. *Testes unitários cobrindo rota normal e ponto inalcançável* — rota normal em `rotaNormalVisitaNaOrdemDoVizinhoMaisProximo` (PI3-148); ponto inalcançável em `pontoDesconectadoNaoImpedeOsDemais` e `roteiroParcialEhPreservadoQuandoARotaTrava` (PI3-149), reforçados por `maoUnicaDeixaPontoInalcancavelDepoisDaParada` (PI3-150).
