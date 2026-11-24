# language: pt
# História âncora do agregado Requisição (Trabalho U2, Parte 2, seção B).
# Invariantes cobertas: I1 (item não recebe mais do que pediu) e I2 (só bolsa
# disponível, na validade, do mesmo componente e de grupo compatível).
# Será executado com Cucumber na Parte 3; os mesmos casos já rodam como JUnit
# em RequisicaoTest e BolsaTest.

Funcionalidade: Alocar bolsa compatível a um item da requisição
  Como técnica do hemocentro
  Quero alocar a um item da requisição uma bolsa compatível com o receptor
  Para atender o hospital sem risco transfusional e sem desperdício

  Contexto:
    Dado uma requisição URGENTE do hospital HC05
    E um item de 2 bolsas de CONCENTRADO_HEMACIAS para receptor A+

  Cenário: Bolsa O- atende receptor A+
    Dado uma bolsa "B-1" de CONCENTRADO_HEMACIAS O- disponível no hemocentro HC01, dentro da validade
    Quando a técnica alocar a bolsa "B-1" ao item
    Então a bolsa "B-1" fica RESERVADA
    E a requisição fica PARCIALMENTE_ATENDIDA

  Cenário: Bolsa de grupo incompatível é recusada
    Dado uma bolsa "B-2" de CONCENTRADO_HEMACIAS AB+ disponível, dentro da validade
    Quando a técnica alocar a bolsa "B-2" ao item
    Então a alocação é recusada com a mensagem "Grupo sanguineo incompativel"
    E a bolsa "B-2" continua DISPONIVEL

  Cenário: Item atendido não recebe mais bolsas
    Dado que o item já recebeu 2 bolsas compatíveis
    E uma bolsa "B-3" de CONCENTRADO_HEMACIAS O- disponível, dentro da validade
    Quando a técnica alocar a bolsa "B-3" ao item
    Então a alocação é recusada com a mensagem "Item ja esta totalmente atendido"
    E a requisição continua ATENDIDA

  Cenário: Bolsa vencida é recusada mesmo sendo compatível
    Dado uma bolsa "B-4" de CONCENTRADO_HEMACIAS O- cuja validade terminou ontem
    Quando a técnica alocar a bolsa "B-4" ao item
    Então a alocação é recusada com a mensagem "Bolsa vencida"
