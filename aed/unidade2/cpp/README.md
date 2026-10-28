# AED Unidade 2 — versão em C++

Fila de prioridade FEFO por inserção ordenada: a bolsa que vence primeiro está
sempre no início, sem nenhum passo de ordenação depois.

Conversão da versão em C, que fica em [`../c/`](../c/) e continua no
repositório como contraponto para a tradução comentada exigida pela PI3-98.

## Como compilar e rodar

```bash
make
./rota_vital_u2
```

Para limpar os objetos e o executável:

```bash
make clean
```

Compilado com `g++ -Wall -Wextra -std=c++17`, sem avisos.

## Equivalência com a versão em C

As duas versões produzem a mesma saída sobre os mesmos dados. A conferência é
reproduzível:

```bash
cd ../c   && make && ./rota_vital_u2 > /tmp/u2_c.txt
cd ../cpp && make && ./rota_vital_u2 > /tmp/u2_cpp.txt
diff /tmp/u2_c.txt /tmp/u2_cpp.txt
```

As únicas diferenças são textos descritivos: a linha "Versão em C++" no
cabeçalho, `nullptr` no lugar de `NULL`, e a etapa 6, que descreve RAII em vez
de liberação manual. Todos os dados batem: a ordenação, o empate resolvido
pelo código de rastreio, o descarte das vencidas e a retirada até esvaziar.

## O que mudou, e o que foi mantido de propósito

A conversão **não** trocou a lista encadeada por `std::list` ou
`std::multiset`. Isso resolveria em poucas linhas e apagaria justamente o que
o exercício ensina: o encadeamento, o religamento de ponteiros nos três casos
de inserção, e o custo O(n) de manter a ordem a cada entrada.

Os nós continuam alocados com `new` e liberados com `delete`.

| Aspecto | C | C++ |
|---|---|---|
| Texto | `char[16]` com `copiar_texto` | `std::string` |
| Comparação | `strcmp(a, b) < 0` | `a < b` |
| Resultado | macros `RV_OK` / `RV_ERRO` | `enum class Resultado` |
| Valor de saída | parâmetro `&saida` + código de retorno | `std::optional<Bolsa>` |
| Lista de descartadas | vetor pré-alocado + capacidade | `std::vector<Bolsa>` |
| Criação | `fila_criar()`, podendo devolver `NULL` | construtor |
| Liberação | `fila_destruir()`, chamada manual | destrutor (RAII) |
| Organização | `struct` + funções com prefixo | `class` com métodos |
| Saída formatada | `printf("%-8s")` | `std::setw` com `std::left` |

Cada arquivo traz comentários marcados com **DIFERENCA PARA A VERSAO EM C**
nos pontos em que a tradução tomou uma decisão. São esses trechos que
alimentam a tradução comentada da PI3-98.

## Por que a ordem funciona sobre texto

As datas estão no formato ISO `AAAA-MM-DD`, que ordena igual como texto e como
data: `"2026-10-01" < "2026-10-20"` é verdadeiro nos dois sentidos.

Em C isso exigia `strcmp`, cujo retorno é um inteiro que se lê ao contrário da
intuição. Em C++ o operador `<` de `std::string` faz o esperado, igual ao
`compareTo` do Java.

## A inserção estável

O laço do caso 3 avança enquanto `vemAntes(atual, nova)` é verdadeiro, em vez
de usar a negação de `vemAntes(nova, atual)`. A diferença aparece no empate: a
bolsa nova acaba **depois** das de mesma validade, e não antes.

É o que torna o resultado determinístico. Duas bolsas que vencem no mesmo dia
sempre resolvem para a mesma escolha, em qualquer ordem de montagem do
estoque.

## Arquivos

| Arquivo | O que tem |
|---|---|
| `dominio.hpp` | `Bolsa` e `enum class Resultado` |
| `fila_fefo.hpp` / `.cpp` | a fila, com os três casos de inserção |
| `main.cpp` | demonstração em seis etapas |
| `Makefile` | compilação com um comando |
