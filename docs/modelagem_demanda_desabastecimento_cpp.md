# Modelagem Estocástica de Demanda e Risco de Desabastecimento em C++ (PI3-102)

> **Projeto Integrador 3 (Rota Vital - 2026.2)**  
> **Fase 4 (CRISP-DM) — Modelagem**  
> **Linguagem:** C++ (GCC 15.2 / Padrão C++20)  
> **Integração de Disciplinas:** Programação Orientada a Objetos (POO), Algoritmos e Estrutura de Dados (AED), Estatística / Ciência de Dados.  
> **Ticket:** PI3-102 — Probabilidade de desabastecimento por tipo (EST). O resultado alimenta o painel consolidado **PI3-32**.

---

## 1. Visão Geral e Contexto de Engenharia

No planejamento do ciclo CRISP-DM do **Rota Vital**, a **Fase 4 (Modelagem)** está prevista para a semana 12. Conforme o briefing do projeto (`dados/crisp-dm-briefing.md`), treinar modelos de *Machine Learning* preditivo sobre bases sintéticas seria redundante: o algoritmo apenas aprenderia de volta as premissas paramétricas declaradas em `premissas.py`.

A modelagem com valor de negócio para a hemorrede, e que este documento cobre, consiste em:
1. **Modelar a demanda por tipo de hemocomponente e grupo sanguíneo como variável aleatória** a partir dos dados acumulados do projeto;
2. **Calcular a probabilidade de desabastecimento (*stockout risk*)**, confrontando a distribuição de demanda com a disponibilidade física de bolsas válidas;
3. **Registrar a distribuição estatística escolhida e sua justificativa**, verificada contra os próprios dados;
4. **Disponibilizar os resultados para o painel consolidado de indicadores (PI3-32)**, integrando os conceitos de **Dados Acumulados (POO)** e **Saídas (AED)**.

---

## 2. Fundamentos Conceituais das Disciplinas

### 2.1. DADOS ACUMULADOS (Programação Orientada a Objetos - POO)

> **Definição:**  
> *Dados acumulativos são valores que vão sendo somados ou atualizados ao longo da execução do programa, conforme novas informações são recebidas.*  
>  
> **Analogia Pedagógica:**  
> Imagine um sistema de uma loja que registra o preço de vários produtos. A cada produto cadastrado ou lido pelo leitor de código de barras no caixa, o seu valor é adicionado progressivamente ao total da compra.

#### Aplicação na Arquitetura C++ do Rota Vital:
No sistema desenvolvido em C++, implementamos duas classes com o padrão de projeto *Accumulator*:
- **`AcumuladorEstoque`:** A cada bolsa física inspecionada (código, grupo ABO/Rh, hemocomponente, dias até vencer e status de vencimento), os acumuladores privados são incrementados (`totalBolsas++`, `totalValidas++`, contadores em mapas associativos `std::map`).
- **`AcumuladorDemanda`:** A cada requisição emitida por uma unidade hospitalar e a cada item de transfusão requisitado, o volume solicitado é somado dinamicamente:
  $$\text{TotalDemandado} \leftarrow \text{TotalDemandado} + Q_{\text{item}}$$
  Além do total, a classe acumula a demanda **por dia** (data da requisição), o que permite reconstruir a série diária de cada tipo e medir a variância real da demanda (seção 3).
- **Sobrecarga de Operadores em POO:** As classes implementam `operator+=`, permitindo acumulação idiomática e segura:
  ```cpp
  acumuladorDemanda += requisicao; // Adiciona o lote ao total acumulado
  acumuladorEstoque += bolsa;      // Adiciona a bolsa física ao total acumulado
  ```
- **Encapsulamento:** Os totais acumulados não podem ser alterados arbitrariamente por código externo; o acesso ocorre unicamente por meio de métodos seletores (*getters*), garantindo a consistência dos dados contábeis da hemorrede.

---

### 2.2. SAÍDAS (Algoritmos e Estrutura de Dados - AED)

> **Definição:**  
> *As saídas são as informações que o programa apresenta ao usuário depois de executar uma instrução ou processar dados.*  
>  
> **Pipeline Clássico:**  
> O programa recebe dados (**Entrada**), processa esses dados através de algoritmos e estruturas de dados (**Processamento**) e apresenta um resultado (**Saída**).

#### O Pipeline de Três Estágios do Módulo C++:
```
┌─────────────────────────────────┐
│     1. ENTRADA DE DADOS         │  Leitura do arquivo estruturado dados_sinteticos.json
│     (Input / Leitor JSON)       │  (2.000 bolsas de hemocomponentes e 300 requisições hospitalares)
└────────────────┬────────────────┘
                 │
                 ▼
┌─────────────────────────────────┐
│     2. PROCESSAMENTO            │  • Acumulação linear O(N) nas classes POO (totais e série diária)
│     (Estruturas & Algoritmos)   │  • Estruturas de busca rápida: std::map, std::vector, std::pair
│                                 │  • Algoritmos probabilísticos: Binomial Negativa, Poisson,
│                                 │    Normal (TCL) e Monte Carlo por bootstrap
└────────────────┬────────────────┘
                 │
                 ▼
┌─────────────────────────────────┐
│     3. SAÍDA DE DADOS           │  • Saída 1: Relatório tabular no terminal (std::cout formatado)
│     (Output / Painel PI3-32)    │  • Saída 2: dados/painel_consolidado_pi3_32.json
└─────────────────────────────────┘
```

---

## 3. Modelagem Estatística: Demanda como Variável Aleatória

### 3.1. Definição da Variável Aleatória de Demanda ($D$)
A demanda hospitalar por bolsas de sangue não é um valor determinístico. Em qualquer intervalo de tempo, o número de pacientes que requerem transfusões varia de forma estocástica.

Definimos a demanda total por um tipo sanguíneo $t$ no horizonte observado como uma **variável aleatória discreta não-negativa**:
$$D_t = \sum_{j=1}^{N_{\text{req}}} Q_{j, t}, \quad D_t \in \{0, 1, 2, 3, \dots\}$$
onde $N_{\text{req}}$ é o número de requisições recebidas e $Q_{j, t}$ é a quantidade de bolsas do tipo $t$ na $j$-ésima requisição.

O horizonte é o período coberto pelas requisições sintéticas: **61 dias, de 19/07/2026 a 17/09/2026**. O estoque $S_t$ é a fotografia das bolsas válidas na data de referência da base. A pergunta respondida é: *com o estoque válido de hoje, qual a probabilidade de não atender uma demanda do tamanho da observada no horizonte?*

---

### 3.2. Por que não basta a Poisson: o teste nos dados

O ponto de partida natural é a Poisson: as requisições chegam de forma independente, e o número de **pedidos** num intervalo é bem descrito por um processo de Poisson. O problema é que a demanda é medida em **bolsas**, e cada item de requisição traz **de 1 a 4 bolsas**. A soma de um número Poisson de lotes de tamanho variável é uma **Poisson composta**, cuja variância é maior que a média:
$$\text{Var}[D] = \lambda \cdot \mathbb{E}[Q^2] > \lambda \cdot \mathbb{E}[Q] = \mathbb{E}[D]$$

A Poisson impõe $\text{Var}[D] = \mathbb{E}[D]$. Para verificar se essa hipótese se sustenta, medimos o **índice de dispersão** (variância / média) da série diária de cada tipo:

| Tipo | Média diária | Variância diária | Índice de dispersão |
|:---:|:---:|:---:|:---:|
| A+ | 9,00 | 18,52 | 2,06 |
| A- | 2,00 | 6,07 | 3,03 |
| B+ | 1,93 | 6,29 | 3,25 |
| O- | 1,59 | 5,26 | 3,31 |
| AB- | 0,16 | 0,56 | 3,44 |
| B- | 0,48 | 1,69 | 3,56 |
| O+ | 8,49 | 31,82 | 3,75 |
| AB+ | 0,72 | 2,79 | 3,87 |

Em todos os tipos a variância é **2 a 4 vezes a média**. A Poisson, portanto, subestima a incerteza da demanda e, com ela, o risco de desabastecimento justamente nos tipos com estoque próximo da demanda.

---

### 3.3. Distribuição Escolhida: Binomial Negativa $D_t \sim \text{BN}(\mu_t, \sigma_t^2)$

A **Binomial Negativa** é a distribuição discreta padrão para contagens sobredispersas. Ela é parametrizada pela média e pela variância observadas:
$$r = \frac{\mu^2}{\sigma^2 - \mu}, \qquad p = \frac{r}{r + \mu}, \qquad P(D = k) = \frac{\Gamma(k + r)}{\Gamma(r)\, k!}\, p^r (1-p)^k$$

com:
- $\mu_t$ = demanda total observada do tipo $t$ no horizonte;
- $\sigma_t^2 = N \cdot s_t^2$, onde $s_t^2$ é a variância amostral da série diária e $N = 61$ dias (a demanda do horizonte é a soma de $N$ dias independentes).

#### Justificativa:
1. **Natureza discreta e não-negativa:** não existem frações nem valores negativos de bolsas.
2. **Aderência aos dados:** a variância medida é 2 a 4 vezes a média (seção 3.2); a Binomial Negativa acomoda isso, a Poisson não.
3. **Generaliza a Poisson:** quando $\sigma^2 = \mu$, a Binomial Negativa se reduz à Poisson. O código usa essa redução quando não há série diária disponível (requisições sem data).
4. **Literatura especializada:** modelos de estoque hemoterápico (*Prastacos, 1984*; *Pierskalla, 2004*; *Beliën & Forcé, 2012*) usam a Poisson como referência e a Binomial Negativa quando a demanda apresenta sobredispersão.

A CDF é calculada termo a termo em espaço logarítmico (`std::lgamma`), o que evita *underflow* mesmo para médias altas como a de concentrado de hemácias ($\mu = 807$).

---

### 3.4. Métodos de Comparação

O programa calcula $P(D > S)$ por quatro caminhos:

| Método | Hipótese | Papel |
|---|---|---|
| **Binomial Negativa** | Média e variância medidas na série diária | **Probabilidade adotada** (risco e recomendação) |
| **Poisson** | Variância = média | Referência, para mostrar quanto a hipótese simplificada subestima o risco |
| **Normal (TCL)** | Soma de 61 dias independentes: $\mathcal{N}(\mu, N s^2)$, com correção de continuidade | Aproximação contínua com a variância real |
| **Monte Carlo por bootstrap** | Nenhuma distribuição teórica | Validação independente |

#### Teorema Central do Limite (TCL)
A demanda do horizonte é a soma de 61 demandas diárias independentes; pelo TCL ela se aproxima de uma Normal com a **variância real** do horizonte:
$$P(D_t > S_t) \approx 1 - \Phi\left(\frac{S_t + 0.5 - \mu_t}{\sqrt{N s_t^2}}\right)$$

#### Validação por Monte Carlo (bootstrap)
Cada uma das **100.000 simulações** (semente `2026`) sorteia 61 dias da série observada **com reposição** e soma a demanda. A fração de simulações em que a soma supera $S_t$ estima $P(D_t > S_t)$ **sem supor distribuição alguma**. Se a Binomial Negativa estiver bem ajustada, as duas estimativas devem concordar, e é isso que se observa (seção 5).

---

## 4. Cálculo da Probabilidade de Desabastecimento (*Stockout Risk*)

### 4.1. Regra Fundamental de Negócio: Estoque Físico Válido ($S$)
**Bolsas vencidas não constituem estoque útil**: representam descarte biológico obrigatório. A disponibilidade $S_t$ considera **exclusivamente as bolsas válidas**:
$$S_t = N_{\text{total}}(t) - N_{\text{vencidas}}(t)$$

### 4.2. Formulação Probabilística
O evento de **desabastecimento** ocorre quando a demanda $D_t$ é estritamente maior que o estoque disponível $S_t$:
$$\mathbb{P}(\text{Desabastecimento}_t) = \mathbb{P}(D_t > S_t) = 1 - F_{D_t}(S_t)$$

- **Via Binomial Negativa (adotada):** $1 - \sum_{k=0}^{S_t} P_{\text{BN}}(D_t = k)$
- **Via Poisson (referência):** $1 - \sum_{k=0}^{S_t} \frac{e^{-\mu_t} \mu_t^k}{k!}$
- **Via Monte Carlo (bootstrap):** $\hat{\mathbb{P}} = \frac{1}{M} \sum_{m=1}^{M} \mathbb{I}\left(D_t^{(m)} > S_t\right)$, com $M = 100.000$

---

## 5. Resultados sobre os Dados Sintéticos Oficiais

Base: `dados/dados_sinteticos.json` — **2.000 bolsas e 300 requisições hospitalares (1.487 bolsas solicitadas em 61 dias)**. Os totais conferem com a análise descritiva oficial (`dados/resultados.json`, gerado por `dados/analise.py`).

### 5.1. Desabastecimento por Tipo Sanguíneo

| Tipo | Estoque Válido ($S$) | Demanda ($\mu$) | Var/média | Cobertura | Déficit | **P (Bin. Neg.)** | P (Poisson) | P (Normal) | P (Bootstrap) | Risco |
|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| **A+** | 454 | 549 | 2,09 | 82,7% | 95 | **99,8%** | 100,0% | 99,7% | 99,8% | **CRÍTICO** |
| **AB+** | 30 | 44 | 3,93 | 68,2% | 14 | **85,2%** | 98,3% | 84,8% | 85,1% | **CRÍTICO** |
| **AB-** | 5 | 10 | 3,49 | 50,0% | 5 | **76,2%** | 93,3% | 77,7% | 78,4% | **CRÍTICO** |
| **B+** | 105 | 118 | 3,31 | 89,0% | 13 | **72,7%** | 87,5% | 73,7% | 73,3% | **ALTO** |
| **A-** | 110 | 122 | 3,08 | 90,2% | 12 | **71,4%** | 85,1% | 72,3% | 71,8% | **ALTO** |
| **O+** | 492 | 518 | 3,81 | 95,0% | 26 | **71,1%** | 86,9% | 71,7% | 71,6% | **ALTO** |
| **B-** | 30 | 29 | 3,62 | 103,4% | 0 | **40,3%** | 37,9% | 44,2% | 41,8% | **MODERADO** |
| **O-** | 119 | 97 | 3,36 | 122,7% | 0 | **11,0%** | 1,1% | 10,6% | 10,9% | **BAIXO** |

O bootstrap, que não depende de nenhuma distribuição teórica, concorda com a Binomial Negativa em todos os tipos (diferença máxima de cerca de 2 pontos percentuais). A Poisson se afasta nos dois sentidos:
- **Tipos com déficit** (AB+, AB-, B+, A-, O+): a Poisson concentra demais a demanda em torno da média e **exagera a certeza** do desabastecimento.
- **Tipos com superávit** (O-): a Poisson **subestima o risco em dez vezes** (1,1% contra 11,0%). Um gestor que confiasse nela trataria o O-, o doador universal, como estoque folgado.

---

### 5.2. A Lição Estatística: Cobertura vs. Probabilidade de Desabastecimento

Observe os grupos **B-** e **O-**:
- **B-:** cobertura de $\frac{30}{29} = 103,4\%$, déficit zero, e ainda assim **40,3%** de probabilidade de desabastecimento.
- **O-:** cobertura de 122,7%, superávit de 22 bolsas, e **11,0%** de probabilidade de faltar.

> [!IMPORTANT]
> **Por que um estoque com cobertura acima de 100% ainda corre risco de faltar sangue?**  
> Porque a demanda é uma **variável aleatória**, não um número fixo. A cobertura compara o estoque com a demanda média; o risco depende também da variabilidade em torno dessa média. Com pedidos de vários lotes, a demanda oscila muito mais do que a Poisson supõe, e um estoque "suficiente na média" ainda é ultrapassado com frequência.  
> Por isso a gestão da hemorrede **não pode confiar apenas na taxa de cobertura estática**, e a probabilidade de desabastecimento precisa ser calculada com a variância real da demanda.

---

### 5.3. Desabastecimento por Hemocomponente

| Hemocomponente | Total Coletado | Válidas | Vencidas | Taxa de Descarte | Demanda ($\mu$) | Var/média | **P (Bin. Neg.)** | P (Poisson) | Risco |
|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| **Concentrado de Plaquetas** | 407 | 40 | 367 | **90,2%** | 257 | 2,95 | **100,0%** | 100,0% | **CRÍTICO** |
| **Concentrado de Hemácias** | 1.128 | 840 | 288 | **25,5%** | 807 | 4,60 | **28,6%** | 11,9% | **MODERADO** |
| **Plasma Fresco Congelado** | 369 | 369 | 0 | **0,0%** | 344 | 3,09 | **21,4%** | 8,5% | **MODERADO** |
| **Crioprecipitado** | 96 | 96 | 0 | **0,0%** | 79 | 2,48 | **10,9%** | 2,7% | **BAIXO** |

- **Plaquetas:** validade de 5 dias; 90% do coletado venceu antes do uso. O desabastecimento é praticamente certo sem uma política FEFO rigorosa.
- **Hemácias e plasma:** a Poisson indicaria risco baixo (11,9% e 8,5%), mas com a variância real o risco é **mais que o dobro** e os dois passam a MODERADO.
- A taxa de descarte por hemocomponente também é exportada no JSON e serve de insumo para o **PI3-103** (taxa de descarte por vencimento).

---

## 6. Estrutura do Arquivo de Saída JSON (`dados/painel_consolidado_pi3_32.json`)

O executável grava o JSON **na mesma pasta do arquivo de entrada** (`dados/`), seja executado a partir da raiz do repositório ou da pasta `cpp/`. Cada tipo sanguíneo traz:

```json
{
  "tipo_sanguineo": "O_NEG",
  "nome_exibicao": "O-",
  "estoque_total": 184,
  "estoque_valido_disponivel": 119,
  "estoque_vencido": 65,
  "demanda_esperada": 97,
  "variancia_demanda": 326.1,
  "indice_dispersao": 3.36,
  "cobertura_pontual_pct": 122.7,
  "deficit_unidades": 0,
  "probabilidade_desabastecimento": 0.110,
  "probabilidade_desabastecimento_poisson": 0.011,
  "probabilidade_desabastecimento_normal_tcl": 0.106,
  "probabilidade_desabastecimento_monte_carlo": 0.109,
  "nivel_risco": "BAIXO",
  "recomendacao_operacional": "ESTOQUE SEGURO: Superavit registrado. Pode servir como retaguarda estrategica regional."
}
```

O campo que o painel PI3-32 deve exibir é **`probabilidade_desabastecimento`** (Binomial Negativa). Os demais ficam disponíveis para comparação. Os valores acima estão arredondados; o arquivo traz a precisão completa.

---

## 7. Como Compilar e Reproduzir em C++

A arquitetura usa C++ padrão (C++20). A única dependência externa é a biblioteca de cabeçalho único `nlohmann/json`, já incluída em `cpp/include/`. Os executáveis em `cpp/bin/` são gerados localmente e não são versionados.

### 7.1. Compilação e Execução via PowerShell:
```powershell
cd cpp
.\compile.ps1
```

### 7.2. Compilação e Execução via Prompt Batch (Windows):
```cmd
cd cpp
build.bat
```

### 7.3. Compilação Manual via g++:
```bash
cd cpp
# Compilar aplicacao principal
g++ -std=c++20 -O3 -I include src/*.cpp -o bin/rota_vital_modelagem.exe

# Executar (le ../dados/dados_sinteticos.json e grava ../dados/painel_consolidado_pi3_32.json)
bin/rota_vital_modelagem.exe

# Compilar e rodar testes unitarios
g++ -std=c++20 -O3 -I include src/Dominio.cpp src/AcumuladorEstoque.cpp src/AcumuladorDemanda.cpp src/ModeloDemanda.cpp src/CalculadorDesabastecimento.cpp tests/TestesModelagem.cpp -o bin/testes_modelagem.exe
bin/testes_modelagem.exe
```

A suíte tem **8 testes**, cobrindo os acumuladores, a série diária, Poisson, Binomial Negativa (incluindo valores de referência calculados à mão), o bootstrap e o calculador de desabastecimento.

### 7.4. Reprodutibilidade
Com a mesma base e a semente `2026`, duas execuções produzem o mesmo JSON, byte a byte. O campo `meta.data_execucao` é fixo justamente para preservar essa propriedade.

---

## 8. Conclusão

A modelagem atende aos critérios de aceite do **PI3-102**:
1. **Demanda modelada como variável aleatória** por tipo sanguíneo, por hemocomponente e pela combinação dos dois;
2. **Probabilidade de desabastecimento calculada** confrontando a demanda com o estoque válido, excluindo bolsas vencidas;
3. **Distribuição registrada e justificada:** Binomial Negativa, escolhida porque os dados mostram variância 2 a 4 vezes a média, o que invalida a hipótese da Poisson;
4. **Cálculo correto e reproduzível:** a solução analítica é confirmada por um Monte Carlo por bootstrap independente de distribuição, e a execução é determinística;
5. **Resultado disponível para o painel PI3-32** em `dados/painel_consolidado_pi3_32.json`, no campo `probabilidade_desabastecimento`.
