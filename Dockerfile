# Rota Vital — imagem da aplicação
#
# Build multi-stage: o primeiro estágio compila e roda os testes, o segundo
# leva apenas o jar. Sem isso a imagem final carregaria o Maven, o JDK
# completo, o código-fonte e o repositório de dependências — centenas de
# megabytes que não servem para executar nada em produção, e que ampliam a
# superfície de ataque.

# ---------------------------------------------------------------------------
# Estágio 1 — compilação e testes
# ---------------------------------------------------------------------------
# JDK completo, porque aqui é preciso compilar. A imagem é descartada ao fim
# do build: nada dela chega à final.
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /build

# As dependências são copiadas antes do código-fonte de propósito. O Docker
# reaproveita camadas enquanto os arquivos não mudam, e o pom.xml muda muito
# menos que o src/. Assim um ajuste de código não força baixar o Maven inteiro
# de novo.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

COPY src/ src/

# O infra/ entra porque é insumo de teste, não só de deploy: o
# PerfilProducaoTest lê o nginx-rota-vital.conf para conferir que a porta do
# perfil de produção bate com a do proxy reverso. Sem este COPY o teste falha
# com NoSuchFile, e a imagem nem chega a ser gerada.
COPY infra/ infra/

# O teste roda aqui, dentro do build. Se algum falhar, a imagem não é gerada:
# é o mesmo portão que o pipeline aplica, agora também para quem compila na
# própria máquina.
RUN ./mvnw -B clean package

# ---------------------------------------------------------------------------
# Estágio 2 — execução
# ---------------------------------------------------------------------------
# JRE, não JDK: para executar não é preciso compilador. Alpine porque o
# resultado fica em torno de 180 MB contra cerca de 450 MB da variante padrão,
# e menos pacotes instalados significam menos vulnerabilidades a acompanhar.
FROM eclipse-temurin:21-jre-alpine

# Usuário próprio, sem shell. Container que roda como root e é comprometido
# entrega root ao atacante; este usuário não tem nem onde se logar.
RUN addgroup -S rotavital && adduser -S -G rotavital -s /sbin/nologin rotavital

WORKDIR /app

COPY --from=build --chown=rotavital:rotavital /build/target/*.jar app.jar

USER rotavital

EXPOSE 8080

# Os mesmos limites usados na VM, chegados por medição e não por estimativa:
# com heap de 256 MB o processo ficou em 331 MB de RSS. UseSerialGC evita as
# threads extras do G1, que não se pagam em container pequeno.
ENV JAVA_OPTS="-Xmx256m -XX:MaxMetaspaceSize=128m -XX:+UseSerialGC"

# O healthcheck consulta o Actuator, que já está exposto. start-period generoso
# porque a JVM leva algumas dezenas de segundos na primeira subida: sem essa
# folga o container seria marcado como doente antes de terminar de iniciar.
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

# Forma exec com sh -c para que $JAVA_OPTS seja expandido. Sem o shell, a
# variável chegaria ao java como texto literal.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
