# Evidências — E1 · Container automatizado

Capturado em 20/10/2026.

## `docker compose ps`

```
NAME              IMAGE                STATUS                        PORTS
rotavital-api     projeto3-20262-api   Up About a minute (healthy)   0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp
rotavital-banco   postgres:17-alpine   Up About a minute (healthy)   5432/tcp
```

## API respondendo

```
$ curl -s http://localhost:8080/actuator/health
{"groups":["liveness","readiness"],"status":"UP"}

$ curl -s http://localhost:8080/api/v1/hemocentros | head
[{"id":"HC01","nome":"Hemocentro Recife (HEMOPE)","telefone":"(81) 3182-4600","cnpj":"11222333000181","endereco":{"logradouro":"Rua Joaquim Nabuco","numero":"171","bairro":"Gracas","cidade":"Recife","uf":"PE","cep":"52011-000","latitude":-8.0528651,"longitude":-34.8979716}},{"id":"HC02","nome":"GSH ...
```

## Tabelas criadas no PostgreSQL

```
                List of relations
 Schema |        Name         | Type  |   Owner   
--------+---------------------+-------+-----------
 public | alocacao            | table | rotavital
 public | bolsa               | table | rotavital
 public | item_requisicao     | table | rotavital
 public | leitura_temperatura | table | rotavital
 public | local               | table | rotavital
 public | requisicao          | table | rotavital
 public | rota                | table | rotavital
 public | rota_bolsa          | table | rotavital
 public | veiculo             | table | rotavital
(9 rows)

```

## Tamanho das imagens

```
projeto3-20262-api:latest	410MB
postgres:17-alpine	424MB
postgres:16	642MB
```

## Persistência: os dados sobrevivem ao restart

O volume nomeado é o que diferencia este ambiente do H2 em memória usado em
desenvolvimento. Teste feito:

```
$ POST /api/v1/bolsas          -> bolsa 7424c0b3-7d3... criada
$ docker compose restart
$ GET  /api/v1/bolsas/7424c0b3 -> HTTP 200
```

A bolsa sobreviveu. Com H2 em memória a mesma sequência devolveria 404.

## Testes rodam dentro do build

O primeiro estágio do Dockerfile executa `./mvnw clean package`. Se algum
teste falhar, a imagem não é gerada:

```
[INFO] Tests run: 411, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```
