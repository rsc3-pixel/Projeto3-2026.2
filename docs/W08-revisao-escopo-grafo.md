# W08 — Revisão do Escopo do Grafo e Evidências (AED U1)

Documento de registro da tarefa de entrega da **Semana 08 (W08)** da disciplina de **Algoritmos e Estruturas de Dados (AED)**, referente à **Unidade 1**.

**Responsável:** Victor Roma  
**Disciplina:** Algoritmos e Estruturas de Dados (AED)  
**Unidade:** Unidade 1 (U1)  
**Status:** Concluída  
**Data de Conclusão:** 06/10/2026  
**Branch da Entrega:** `docs/w08-entrega-aed`  

---

## 1. Descrição da Tarefa

Revisar a documentação do escopo do grafo produzida originalmente na **W02** ([`docs/W02-escopo-grafo-rede-distribuicao.md`](W02-escopo-grafo-rede-distribuicao.md)) para assegurar que ela reflita fidedignamente as estruturas de dados efetivamente implementadas no código-fonte do sistema **Rota Vital**. Consolidar esta revisão e as comprovações técnicas como evidência oficial para a avaliação da **Unidade 1 de AED** ([`docs/evidencias-unidade-1-aed.md`](evidencias-unidade-1-aed.md)).

---

## 2. O que Foi Executado

1. **Criação da Branch de Entrega:**
   - Criada a branch dedicada [`docs/w08-entrega-aed`](https://github.com/rsc3-pixel/Projeto3-2026.2/tree/docs/w08-entrega-aed) a partir da versão mais recente da `main`.

2. **Padronização e Organização dos Arquivos da W02:**
   - Renomeação do arquivo de escopo para [`docs/W02-escopo-grafo-rede-distribuicao.md`](W02-escopo-grafo-rede-distribuicao.md), alinhando o prefixo com os demais documentos de entregas da disciplina (`W04-estruturas-base.md`, `W06-topologia-diferencas.md`).
   - Movimentação da imagem do grafo para a pasta de imagens padronizada: [`docs/img/W02-grafo-rede-distribuicao.png`](img/W02-grafo-rede-distribuicao.png).

3. **Revisão Técnica do Documento de Escopo (W02):**
   - **Vértice:** Mapeado para a classe genérica [`Grafo<T>`](../src/main/java/com/rotavital/estruturas/Grafo.java) com `T = String` na malha viária, conectando-se às entidades [`Local`](../src/main/java/com/rotavital/dominio/Local.java), [`Hemocentro`](../src/main/java/com/rotavital/dominio/Hemocentro.java), [`Hospital`](../src/main/java/com/rotavital/dominio/Hospital.java) e [`UnidadeRede`](../src/main/java/com/rotavital/rede/UnidadeRede.java).
   - **Aresta e Pesos:** Atualizado para detalhar a representação de pesos em minutos (`double pesoMinutos`), a validação obrigatória de pesos não-negativos (`IllegalArgumentException`) e o cálculo de tempos viários reais via distância Haversine a 40 km/h ([`PremissasMalha`](../src/main/java/com/rotavital/rede/PremissasMalha.java)).
   - **Direcionamento:** Confirmada a implementação de digrafo por padrão (`direcionado = true`).
   - **Representação em Memória:** Confirmada a lista de adjacência por mapas aninhados `Map<T, Map<T, Double>>` em $O(V + E)$ de memória.
   - **Algoritmo de Dijkstra:** Atualizado o status (originalmente fora de escopo na W02, agora plenamente implementado em [`Dijkstra<T>`](../src/main/java/com/rotavital/estruturas/Dijkstra.java) com heap binário `PriorityQueue` e mapa de predecessores para reconstrução de rota).
   - **Exemplo de 5 Vértices:** Inserida a imagem visual da topologia e adicionada referência ao teste automatizado [`DijkstraTest#redeDeExemplo`](../src/test/java/com/rotavital/estruturas/DijkstraTest.java), que comprova o caminho mínimo de 23 minutos.

4. **Elaboração do Documento Consolidado de Evidências (AED U1):**
   - Criação do documento [`docs/evidencias-unidade-1-aed.md`](evidencias-unidade-1-aed.md), seguindo o padrão de excelência de [`docs/evidencias-unidade-1-so.md`](evidencias-unidade-1-so.md).
   - Inclusão da análise detalhada das 4 estruturas de dados (`Grafo`, `Dijkstra`, `FilaPrioridadeFefo`, `IndiceEstoque`), da integração no serviço de negócio (`ServicoAlocacaoRota`), do quadro consolidado de complexidade, dos tempos medidos em microssegundos e do registro da execução dos testes unitários com 100% de aprovação.

---

## 3. Critérios de Aceite

- [x] Branch específica criada para a entrega W08 (`docs/w08-entrega-aed`)
- [x] Documento de escopo da W02 revisado e atualizado com as estruturas implementadas
- [x] Vértices, arestas, pesos, digrafo e lista de adjacência alinhados com o código de produção
- [x] Exemplo de 5 unidades validado tanto teoricamente quanto via teste de unidade automatizado
- [x] Imagem do grafo incorporada e versionada em `docs/img/W02-grafo-rede-distribuicao.png`
- [x] Rastreabilidade documentada entre especificações da W02 e classes Java do projeto
- [x] Documento de evidências da Unidade 1 de AED criado e padronizado
- [x] 100% dos testes unitários das estruturas aprovados (0 falhas, 0 erros)

---

## 4. Como Verificar

### 4.1 Executar os testes unitários das estruturas de dados
```bash
./mvnw.cmd test '-Dtest=GrafoTest,DijkstraTest,DijkstraCasosSemSolucaoTest,FilaPrioridadeFefoTest,IndiceEstoqueTest'
```
**Resultado esperado:**
- `GrafoTest`: 12 testes aprovados.
- `DijkstraTest`: 7 testes aprovados.
- `DijkstraCasosSemSolucaoTest`: 12 testes aprovados.
- `FilaPrioridadeFefoTest`: 4 testes aprovados.
- `IndiceEstoqueTest`: 4 testes aprovados.
- **Total:** 39 testes executados, 0 falhas, 0 erros (`BUILD SUCCESS`).

### 4.2 Executar a demonstração ponta a ponta e medição de desempenho
```bash
./mvnw.cmd test '-Dtest=DemoPontaAPontaTest,MedicaoDesempenhoTest,CriteriosAceiteAlocacaoTest'
```
**Resultado esperado:**
- Demonstração dos fluxos de alocação bem-sucedida e dos três motivos de falha (`SEM_ESTOQUE`, `TODAS_VENCIDAS`, `SEM_CAMINHO`).
- Medição de tempo do Dijkstra em ~11,5 µs e da alocação completa em ~17,1 µs.

---

## 5. Artefatos Entregues

| Artefato | Descrição |
|---|---|
| [`docs/W02-escopo-grafo-rede-distribuicao.md`](W02-escopo-grafo-rede-distribuicao.md) | Documento de escopo revisado e atualizado com as estruturas de código |
| [`docs/evidencias-unidade-1-aed.md`](evidencias-unidade-1-aed.md) | Documento completo de evidências técnicas para a Unidade 1 de AED |
| [`docs/W08-revisao-escopo-grafo.md`](W08-revisao-escopo-grafo.md) | Este documento de registro da entrega da W08 |
| [`docs/img/W02-grafo-rede-distribuicao.png`](img/W02-grafo-rede-distribuicao.png) | Imagem do grafo viário de exemplo modelado na W02 |
| [`src/main/java/com/rotavital/estruturas/Grafo.java`](../src/main/java/com/rotavital/estruturas/Grafo.java) | Implementação da lista de adjacência ponderada dirigida |
| [`src/main/java/com/rotavital/estruturas/Dijkstra.java`](../src/main/java/com/rotavital/estruturas/Dijkstra.java) | Implementação do algoritmo de caminho mínimo com heap binário |
