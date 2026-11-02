# Interpretação dos Indicadores Operacionais e Estatísticos (W06 - EST)

## 1. Contexto e Objetivo

Este documento consolida a portabilidade e a interpretação escrita dos cálculos de `dados/analise.py` para a aplicação Java (sob o contrato `/api/v1/indicadores/*`), conforme os critérios da história **W06-Indicadores (EST)**.

Todos os indicadores são computados dinamicamente a partir dos dados persistidos no banco de dados e refletem a massa sintética de referência composta por:
- **2.000 bolsas** coletadas em uma janela de 60 dias;
- **300 requisições hospitalares** totalizando **1.487 bolsas solicitadas**;
- Rede de distribuição localizada na Região Metropolitana do Recife (Recife, Olinda e Jaboatão dos Guararapes).

---

## 2. Metodologia, Fórmulas e Unidades dos Indicadores

### 2.1. Distribuição de Estoque por Tipo Sanguíneo e por Componente

* **Endpoint:** `GET /api/v1/indicadores/estoque-por-tipo` e `GET /api/v1/indicadores/estoque-por-componente`
* **Unidade:** Quantidade absoluta de bolsas ($un$) e percentual sobre o estoque total ($\%$)
* **Fórmula:**
  $$\text{Percentual}(c) = \frac{\text{Quantidade}(c)}{N_{\text{total}}} \times 100$$
  * Onde $N_{\text{total}} = 2.000$ bolsas e $c$ representa o grupo sanguíneo ou tipo de hemocomponente.

### 2.2. Taxa de Descarte por Vencimento por Componente

* **Endpoint:** `GET /api/v1/indicadores/descarte`
* **Unidade:** Taxa percentual ($\%$)
* **Fórmula:**
  $$\text{TaxaDescarte}(comp) = \frac{\text{Bolsas Vencidas}(comp)}{\text{Total Coletado}(comp)} \times 100$$
  * Critério de vencimento: $\text{dataValidade} < \text{dataReferencia}$ (ou $\text{diasAteVencer} < 0$).

### 2.3. Medidas de Tendência Central e Dispersão da Validade

* **Endpoint:** `GET /api/v1/indicadores/validade`
* **Unidade:** Dias ($dias$)
* **População considerada:** Apenas bolsas válidas ($\text{dataValidade} \ge \text{dataReferencia}$), totalizando $N = 1.345$ bolsas.
* **Fórmulas:**
  * **Média Aritmética ($\bar{x}$):**
    $$\bar{x} = \frac{1}{n} \sum_{i=1}^{n} x_i$$
  * **Mediana ($\tilde{x}$):** Valor central da distribuição ordenada de dias restantes:
    $$\tilde{x} = x_{(n+1)/2} \quad \text{(para } n \text{ ímpar)}, \quad \tilde{x} = \frac{x_{(n/2)} + x_{(n/2 + 1)}}{2} \quad \text{(para } n \text{ par)}$$
  * **Desvio-Padrão Amostral ($s$):**
    $$s = \sqrt{\frac{1}{n - 1} \sum_{i=1}^{n} (x_i - \bar{x})^2}$$
    *(Utilizado $n - 1$ para garantir consistência com o `statistics.stdev` do Python e correção de Bessel para amostras).*
  * **Faixa:** Valores $\min(x)$ e $\max(x)$.

### 2.4. Bolsas Próximas do Vencimento (Estoque Crítico / Alvo FEFO)

* **Endpoint:** `GET /api/v1/indicadores/criticas?limiteDias=7`
* **Unidade:** Quantidade de bolsas ($un$) e percentual sobre o estoque válido ($\%$)
* **Fórmula:**
  $$\text{Estoque Crítico} = \{ b \in \text{Bolsas Válidas} \mid 0 \le \text{diasAteVencer}(b) \le \text{limiteDias} \}$$
  $$\text{Percentual Crítico} = \frac{|\text{Estoque Crítico}|}{N_{\text{válidas}}} \times 100$$

### 2.5. Cobertura de Demanda por Tipo Sanguíneo

* **Endpoint:** `GET /api/v1/indicadores/cobertura`
* **Unidade:** Cobertura percentual ($\%$) e Déficit ($un$)
* **Fórmula:**
  $$\text{Cobertura}(g) = \frac{\text{Estoque Disponível Válido}(g)}{\text{Demanda Total Solicitada}(g)} \times 100$$
  $$\text{Déficit}(g) = \max(0, \text{Demanda Total Solicitada}(g) - \text{Estoque Disponível Válido}(g))$$
  * Onde $g \in \{ \text{A+}, \text{A-}, \text{B+}, \text{B-}, \text{AB+}, \text{AB-}, \text{O+}, \text{O-} \}$.

---

### 2.6. Taxa de Descarte por Período de Coleta e Descarte Evitável pela FEFO (U2, PI3-103)

* **Endpoints:** `GET /api/v1/indicadores/descarte/por-periodo?granularidade=SEMANA|MES` e os campos `descarteEvitavelFefo` e `percentualEvitavelFefo` em `GET /api/v1/indicadores/descarte`
* **Unidade:** Taxa percentual ($\%$) e bolsas ($un$)
* **Período:** bolsas agrupadas pela **data de coleta**, em semanas ISO (segunda a domingo, rótulo `2026-W33`) ou meses civis (`2026-08`); o vencimento é conferido na data de referência.
* **Fórmulas:**
  $$\text{TaxaDescarte}(p) = \frac{\text{Vencidas}(p)}{\text{Coletadas}(p)} \times 100$$
  $$\text{Evitável}(comp) = \left|\left\{ b \text{ vencida} : \exists\, i \text{ item},\ \text{tipo}(i) = \text{tipo}(b),\ \text{compatível}(\text{grupo}(b) \to \text{grupo}(i)),\ \text{coleta}(b) \le \text{data}(i) \le \text{validade}(b) \right\}\right|$$
  $$\text{\%Evitável}(comp) = \frac{\text{Evitável}(comp)}{\text{Vencidas}(comp)} \times 100$$
  * A compatibilidade é a de `MatrizCompatibilidade` (hemácias e plaquetas pela tabela de hemácias; plasma e crioprecipitado pela tabela invertida do plasma).
* **Relação com a política FEFO:** a FEFO despacha primeiro a bolsa que vence antes. Se, enquanto a bolsa ainda valia, chegou um pedido do mesmo componente que ela podia atender, a FEFO a teria colocado na frente da fila e ela não teria vencido na prateleira. O descarte **evitável** é, portanto, a parcela da perda atribuível à alocação; o restante é perda **estrutural**: a validade acabou sem que houvesse demanda compatível, o que só se resolve ajustando a coleta ao consumo. O indicador é um limite superior, porque confere a existência de demanda compatível, não a sua suficiência: dois pedidos no mesmo dia podem disputar a mesma bolsa.

### 2.7. Probabilidade de Desabastecimento por Tipo Sanguíneo (U2, PI3-102 e PI3-32)

* **Endpoint:** `GET /api/v1/indicadores/desabastecimento`
* **Unidade:** Probabilidade percentual ($\%$); variância em $un^2$; índice de dispersão adimensional
* **Período:** demanda das requisições entre a primeira e a última data registrada ($N$ dias), confrontada com o estoque válido na data de referência.
* **Fórmulas:** a demanda do tipo $g$ no horizonte é a variável aleatória $D_g$, com média $\mu_g$ igual ao total pedido e variância medida na série diária $d_{g,1}, \dots, d_{g,N}$:
  $$\mu_g = \sum_{t=1}^{N} d_{g,t}, \qquad \sigma_g^2 = N \cdot s_g^2 = N \cdot \frac{1}{N-1}\sum_{t=1}^{N}\left(d_{g,t} - \bar d_g\right)^2, \qquad \text{ID}_g = \frac{\sigma_g^2}{\mu_g}$$
  Com $\text{ID}_g > 1$ há sobredispersão e a Poisson (que impõe $\sigma^2 = \mu$) não descreve a demanda. A distribuição adotada é a **Binomial Negativa** parametrizada por média e variância:
  $$r = \frac{\mu^2}{\sigma^2 - \mu}, \quad p = \frac{r}{r + \mu}, \quad P(D = k) = \frac{\Gamma(k + r)}{\Gamma(r)\, k!}\, p^r (1 - p)^k, \qquad \mathbb{P}(\text{Desabastecimento}_g) = 1 - \sum_{k=0}^{S_g} P(D_g = k)$$
  onde $S_g$ é o estoque válido. Quando $\sigma^2 \le \mu$ o cálculo cai na Poisson. A Poisson é devolvida ao lado, como referência.
* **Nível de risco:** CRÍTICO se $P \ge 80\%$ ou cobertura $< 70\%$; ALTO se $P \ge 50\%$ ou cobertura $< 90\%$; MODERADO se $P \ge 20\%$ ou cobertura $< 100\%$; BAIXO nos demais casos.
* **Implementação:** `ModeloDemanda.java`, porta do módulo C++ do PI3-102 (`cpp/`). As somas são feitas em espaço logarítmico, porque $e^{-\mu}$ é zero em ponto flutuante para demandas na casa das centenas.

---

## 3. Apresentação e Interpretação dos Resultados

### 3.1. Distribuição de Estoque e Composição da Rede

Na base sintética de 2.000 bolsas, o estoque apresenta a seguinte proporção populacional:
* **O+:** 747 bolsas (37,4%)
* **A+:** 652 bolsas (32,6%)
* **O-:** 184 bolsas (9,2%)
* **A-:** 160 bolsas (8,0%)
* **B+:** 153 bolsas (7,6%)
* **AB+:** 50 bolsas (2,5%)
* **B-:** 47 bolsas (2,4%)
* **AB-:** 7 bolsas (0,4%)

Em relação aos componentes:
* **Concentrado de Hemácias:** 1.128 bolsas (56,4%)
* **Concentrado de Plaquetas:** 407 bolsas (20,4%)
* **Plasma Fresco Congelado:** 369 bolsas (18,4%)
* **Crioprecipitado:** 96 bolsas (4,8%)

**Interpretação:** A composição reflete diretamente as prevalências genéticas da população brasileira (predomínio de O+ e A+, escassez extrema de AB- e B-). Essa distribuição impõe desafios logísticos severos: tipos raros precisam de alocação estratégica imediata, pois a chance de recomposição rápida do estoque é baixa.

---

### 3.2. Análise de Descarte por Vencimento

* **Geral:** Das 2.000 bolsas, **655 venceram**, resultando em uma taxa global de descarte de **32,8%**.
* **Por Componente:**
  * **Concentrado de Plaquetas:** 407 total, 367 vencidas $\rightarrow$ **90,2% de descarte**.
  * **Concentrado de Hemácias:** 1.128 total, 288 vencidas $\rightarrow$ **25,5% de descarte**.
  * **Plasma Fresco Congelado:** 369 total, 0 vencidas $\rightarrow$ **0,0% de descarte**.
  * **Crioprecipitado:** 96 total, 0 vencidas $\rightarrow$ **0,0% de descarte**.

**Interpretação:**
1. O descarte na hemorrede não é homogêneo, concentrando-se quase integralmente em componentes de curta vida útil.
2. O **Concentrado de Plaquetas (validade de 5 dias)** possui uma taxa de perda crítica de 90,2%. Em um cenário de coleta contínua em janela de 60 dias sem política ativa de distribuição, as plaquetas vencem antes de serem requisitadas.
3. Isso evidencia a urgência da estratégia **FEFO (First Expire, First Out)** e do despacho dinâmico prioritário de plaquetas para hospitais com maior rotatividade cirúrgica e oncológica.
4. Hemácias (42 dias) sofrem descarte moderado (25,5%), enquanto Plasma e Crio (365 dias) não apresentam perda nessa janela.

---

### 3.3. Medidas de Tendência Central e Dispersão da Validade

Considerando as **1.345 bolsas válidas**:
* **Geral:**
  * Média: **129,2 dias**
  * Mediana: **32,0 dias**
  * Desvio-Padrão Amostral: **150,7 dias**
  * Amplitude (Mín - Máx): **0 a 365 dias**
* **Por Componente:**
  * **Concentrado de Plaquetas ($n = 40$):** Média: 2,3 dias | Mediana: 3,0 dias | Desvio: 1,7 dias
  * **Concentrado de Hemácias ($n = 840$):** Média: 21,0 dias | Mediana: 21,0 dias | Desvio: 12,3 dias
  * **Plasma Fresco Congelado ($n = 369$):** Média: 335,1 dias | Mediana: 335,0 dias | Desvio: 17,4 dias
  * **Crioprecipitado ($n = 96$):** Média: 336,5 dias | Mediana: 337,5 dias | Desvio: 18,1 dias

**Interpretação Estatística:**
1. **Assimetria e Distorção da Média Global:** A média geral (129,2 dias) é mais de **4 vezes superior** à mediana (32,0 dias), com um desvio-padrão altíssimo (150,7 dias). Se a gestão hospitalar avaliasse o estoque apenas pela média, acreditaria falsamente que a rede dispõe de mais de 4 meses de suprimento seguro.
2. A distribuição é bimodal/multimodal e fortemente assimétrica à direita devido à convivência de produtos com prazos de validade em ordens de grandeza discrepantes (5 dias para plaquetas vs. 365 dias para plasma e crioprecipitado).
3. **Comportamento por Componente:** Ao estratificar por componente, a média e a mediana convergem perfeitamente (Hemácias: 21,0 dias média e mediana; Plasma: 335,1 média vs 335,0 mediana). Isso prova que a dispersão global é fruto da mistura de componentes e valida a necessidade técnica de analisar a validade separadamente por hemocomponente.

---

### 3.4. Bolsas Próximas do Vencimento (Janela de 7 Dias)

* **Quantidade de bolsas críticas ($\le 7$ dias restantes):** **187 bolsas**
* **Proporção do estoque válido:** **13,9%**

**Interpretação:**
Quase 14% de todo o estoque útil da rede vence em até uma semana. Essas 187 bolsas são o **alvo prioritário absoluto do algoritmo de despacho FEFO**. Caso não sejam alocadas e transportadas imediatamente para atender às requisições pendentes, transformar-se-ão em descarte evitável nos próximos 7 dias.

---

### 3.5. Cobertura de Demanda e Déficit por Tipo Sanguíneo

Comparativo entre o estoque válido disponível ($1.345$ bolsas) e a demanda acumulada das requisições ($1.487$ bolsas solicitadas):

| Tipo Sanguíneo | Disponível | Demandado | Cobertura (%) | Déficit (un) | Situação |
|:---:|:---:|:---:|:---:|:---:|:---:|
| **AB-** | 5 | 10 | 50,0% | 5 | Déficit crítico |
| **AB+** | 30 | 44 | 68,2% | 14 | Déficit |
| **A+** | 454 | 549 | 82,7% | 95 | Déficit absoluto severo |
| **B+** | 105 | 118 | 89,0% | 13 | Déficit |
| **A-** | 110 | 122 | 90,2% | 12 | Déficit |
| **O+** | 492 | 518 | 95,0% | 26 | Déficit |
| **B-** | 30 | 29 | 103,4% | 0 | Cobertura total |
| **O-** | 119 | 97 | 122,7% | 0 | Cobertura total (Superávit) |
| **TOTAL** | **1.345** | **1.487** | **90,5%** | **165** | **Déficit Líquido Global** |

**Interpretação:**
1. **Déficit Sistêmico:** Globalmente, a rede atende a 90,5% da demanda solicitada, restando um déficit não atendido de 165 bolsas se considerada apenas compatibilidade idêntica (isogrupo).
2. **Gargalo nos tipos AB e A+:**
   - O tipo **AB-** possui a menor cobertura percentual (50,0%), reflexo de sua raridade.
   - O tipo **A+** concentra o maior déficit em volume absoluto (**95 bolsas em falta**). Apesar de representar 32,6% das doações, a demanda hospitalar consumiu mais do que o estoque válido acumulado.
3. **Papel Estratégico do O- (Doador Universal):** O tipo O- apresenta cobertura de **122,7%** (superávit de 22 bolsas). Na prática hemoterápica, esse excedente de O- é o recurso vital que o motor de alocação utilizará para mitigar os déficits dos tipos com cobertura abaixo de 100% (como A-, B- e até tipos positivos em emergências).

---

### 3.6. Descarte por Período de Coleta e Parcela Evitável pela FEFO (U2)

Sobre os dados sintéticos (referência 17/09/2026), a taxa de descarte por semana de coleta cai de **86,2% (semana 29, 13 a 19/07)** para **0,0% (semana 38, 14 a 20/09)**, passando por 72,2%, 76,7%, 43,4%, 21,7%, 22,4%, 17,9%, 19,8% e 10,0%. Por mês: **76,6% em julho, 27,6% em agosto e 12,0% em setembro**. A leitura é a esperada: o descarte se concentra nos lotes antigos, e a curva por período é a estrutura etária do estoque.

| Hemocomponente | Coletadas | Vencidas | Taxa | Evitável pela FEFO | % das vencidas |
|---|:---:|:---:|:---:|:---:|:---:|
| Concentrado de Plaquetas | 407 | 367 | 90,2% | 332 | 90,5% |
| Concentrado de Hemácias | 1.128 | 288 | 25,5% | 288 | 100,0% |
| Plasma Fresco Congelado | 369 | 0 | 0,0% | 0 | – |
| Crioprecipitado | 96 | 0 | 0,0% | 0 | – |

1. **Hemácias:** todas as 288 bolsas vencidas tiveram pedido compatível dentro dos 42 dias de validade. A perda é inteiramente atribuível à alocação, e é exatamente o que a FEFO corrige.
2. **Plaquetas:** 332 das 367 vencidas eram evitáveis; as outras 35 venceram em janelas de 5 dias sem nenhum pedido compatível. Essa fração (9,5%) é a perda estrutural: nenhuma ordem de fila a salva, só a calibração da coleta.
3. **Plasma e crioprecipitado:** validade de 365 dias; não houve descarte no horizonte.

### 3.7. Probabilidade de Desabastecimento (U2)

| Tipo | Estoque válido | Demanda (61 dias) | Var/média | P(falta) Binomial Negativa | P(falta) Poisson | Risco |
|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| A+ | 454 | 549 | 2,09 | **99,8%** | 100,0% | CRÍTICO |
| AB+ | 30 | 44 | 3,93 | **85,2%** | 98,3% | CRÍTICO |
| AB- | 5 | 10 | 3,49 | **76,2%** | 93,3% | CRÍTICO |
| B+ | 105 | 118 | 3,31 | **72,7%** | 87,6% | ALTO |
| A- | 110 | 122 | 3,08 | **71,4%** | 85,1% | ALTO |
| O+ | 492 | 518 | 3,81 | **71,1%** | 86,9% | ALTO |
| B- | 30 | 29 | 3,62 | **40,3%** | 37,9% | MODERADO |
| O- | 119 | 97 | 3,36 | **11,0%** | 1,3% | BAIXO |

Em todos os tipos a variância é 2 a 4 vezes a média, porque cada item de pedido traz de 1 a 4 bolsas. A Poisson exagera a certeza nos tipos em déficit e **subestima o risco nos tipos em superávit**: o O-, doador universal, teria 1,3% de risco pela Poisson e tem 11,0% com a variância real. A cobertura sozinha também engana: o B- cobre 103% da demanda média e ainda assim tem 40% de chance de faltar.

**Validação:** os números acima são os de `dados/validar_indicadores_u2.py` (Python, biblioteca padrão) e são reproduzidos pelo backend no teste `IndicadorServicoDadosSinteticosTest`, que carrega o mesmo dataset em `IndicadorServico` e compara campo a campo; a Binomial Negativa também confere com o módulo C++ do PI3-102 a uma casa decimal. O painel em produção calcula sobre o banco da aplicação (carga de `CargaInicial`, 100 bolsas), por isso exibe valores diferentes dos desta tabela: a regra é a mesma, os dados não.

---

## 4. Conclusão Operacional

A portabilidade dos indicadores de `dados/analise.py` para a aplicação Java fornece a base empírica e analítica necessária para a tomada de decisão no Rota Vital:

1. **Priorização FEFO:** Justificada pelo volume de 187 bolsas críticas a vencer em 7 dias e pela taxa de descarte de 90,2% em plaquetas.
2. **Matriz de Compatibilidade:** Justificada pelo déficit de 165 bolsas em tipos específicos (A+, AB-, AB+, O+), demonstrando que a alocação não pode depender apenas de equivalência exata de tipo sanguíneo.
3. **Métricas de Dispersão no Dashboard:** A divergência brutal entre média (129 dias) e mediana (32 dias) comprova a obrigatoriedade de apresentar medidas de dispersão e valores desagregados por componente para a equipe médica e de suprimentos.
