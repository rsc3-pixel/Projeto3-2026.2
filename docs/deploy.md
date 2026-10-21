# Deploy do Rota Vital

Como a aplicação sai do GitHub e chega no ar. Documento da PI3-21.

---

## O cenário: uma VM compartilhada

O Rota Vital **não tem uma máquina só para ele**. A VM (`flux`, em
`us-west1-a`) já hospedava outra aplicação antes, e isso moldou quase todas as
decisões deste documento.

O que já existe lá:

| Porta | Serviço | Domínio |
|---|---|---|
| 80 / 443 | Nginx | — |
| 3000 | Flux (Node) | `rsc3-flux.duckdns.org` |
| **8081** | **Rota Vital** | `rsc3-rotavital.duckdns.org` |

O Nginx já tem **Certbot configurado**, então o Rota Vital ganha HTTPS de
graça, com o mesmo padrão dos outros sites da máquina.

Três consequências:

1. **A aplicação não usa a 8080.** É a porta padrão do Spring e a mais
   disputada. Duas aplicações na mesma porta não convivem: a segunda a subir
   falha com `Port already in use`.

2. **A aplicação entra atrás do Nginx que já está lá**, com domínio próprio.
   Nenhuma regra nova de firewall, HTTPS pelo Certbot, e o desenho fica igual
   ao [diagrama de arquitetura do PI3-16](diagramas/diagrama_arquitetura_redes.png),
   que já previa o Nginx como ponto único de entrada.

3. **Nada do provisionamento toca no que já roda.** O script não para,
   reinicia nem reconfigura serviço algum além do próprio, e confere se a
   porta está livre antes de instalar qualquer coisa.

```
                      internet
                          │  HTTPS / 443
                          ▼
                  ┌─────────────┐
                  │    Nginx    │  já existia, com Certbot
                  └──┬───────┬──┘
    rsc3-flux...    │       │    rsc3-rotavital...
                    ▼       ▼
             127.0.0.1:3000   127.0.0.1:8081
                Flux            Rota Vital (JVM)
```

A aplicação escuta em `127.0.0.1`, não em `0.0.0.0`. O sistema operacional
recusa qualquer conexão vinda de fora da máquina: a única porta de entrada é
o Nginx. Mesmo que a 8081 fosse aberta no firewall por engano, não haveria o
que alcançar.

---

## O pipeline

```
   push na main
        │
        ▼
┌───────────────┐
│  1. Build     │  o código compila?
└───────┬───────┘
        │ passou
        ▼
┌───────────────┐
│  2. Testes    │  o comportamento está correto?
└───────┬───────┘
        │ passou
        ▼
┌───────────────┐
│ 3. Empacotar  │  gera o rota-vital.jar
└───────┬───────┘
        │ passou
        ▼
┌───────────────┐
│  4. Deploy    │  só na main, só com os segredos configurados
└───────┬───────┘
        │
        ├─ envia o jar por scp
        ├─ chama publicar.sh (troca o jar e reinicia)
        ├─ confirma health UP dentro da VM
        ├─ confirma health UP pela URL pública
        └─ confirma /h2-console inacessível
```

Cada etapa só começa se a anterior passou. Falha em qualquer ponto impede o
deploy, que é o critério de aceite da story.

**Por que quatro jobs e não um só?**

1. **Erro aparece antes.** Erro de compilação quebra em segundos, sem esperar
   a suite inteira.
2. **A aba Actions fica legível.** Você vê onde quebrou sem abrir o log.
3. **O jar publicado é o mesmo que passou nos testes.** O deploy baixa o
   artefato do empacotamento, em vez de recompilar.

**Por que duas verificações de health?** A primeira roda por SSH, dentro da
VM, batendo em `127.0.0.1:8081`. A segunda vem de fora, pela URL pública.

Se ambas falhassem juntas, não daria para saber se o problema é a aplicação
ou o proxy. Separadas, o job aponta a camada exata: passou a primeira e
falhou a segunda, o Nginx é que não está encaminhando.

---

## Parte 1: preparar a VM

Roda uma vez só.

### 1.1 Conectar

```bash
gcloud compute ssh flux --zone=us-west1-a
```

### 1.2 Rodar o provisionamento

O repositório é **privado**, então `curl` anônimo no `raw.githubusercontent`
devolve 404. Clone na VM:

```bash
cd ~
git clone https://github.com/rsc3-pixel/Projeto3-2026.2.git rota-vital-repo
sudo bash rota-vital-repo/infra/provisionar.sh
```

Depois de provisionar, o clone pode sair — o deploy manda o jar por `scp`,
não usa o repositório na VM:

```bash
rm -rf ~/rota-vital-repo
```

> Se o clone pedir senha, o token em `~/.git-credentials` expirou. Gere um
> Personal Access Token em GitHub > Settings > Developer settings > Tokens,
> com escopo `repo`, e use-o como senha.

O script começa mostrando memória e portas em uso, para você ver o cenário
antes de qualquer alteração. Depois:

- **confere se a 8081 está livre** e para com instrução clara se não estiver
- instala o Java 21 (a VM não tinha Java)
- cria o usuário `rotavital`, sem shell de login e sem home
- registra o serviço systemd
- libera o sudo apenas para o script de publicação

Rodar duas vezes não causa problema.

> **Sobre a memória.** A VM tem 978 MB no total e cerca de 577 MB
> realmente disponíveis (`MemAvailable`) com o Flux rodando.
>
> O serviço limita o heap em 256 MB (`-Xmx256m`), com teto de metaspace e
> coletor serial. **A conta que importa: a JVM consome mais que o heap.**
> Metaspace, pilhas de thread e buffers ficam fora dele.
>
> Medição real com esses limites: **331 MB** de memória residente, estável
> após 100 requisições aos cinco recursos da API. Sobram ~246 MB de folga.
>
> Se aparecer `OutOfMemoryError` no log, aumente o `-Xmx` em passos de 64m e
> acompanhe com `head -3 /proc/meminfo`.

Confira ao final:

```bash
java -version                          # deve mostrar 21
sudo systemctl status rota-vital       # inativo, ainda sem jar
```

### 1.3 Criar o subdomínio

Em [duckdns.org](https://www.duckdns.org), com a mesma conta dos domínios que
já existem:

1. campo **sub domain**: `rsc3-rotavital`
2. **add domain**
3. aponte para o IP externo da VM

Confirme que resolveu:

```bash
dig +short rsc3-rotavital.duckdns.org
```

Deve devolver o IP da VM. Se vier vazio, espere um minuto e tente de novo.

### 1.4 Configurar o Nginx

```bash
sudo cp ~/rota-vital-repo/infra/nginx-rota-vital.conf /etc/nginx/sites-available/rota-vital
sudo ln -s /etc/nginx/sites-available/rota-vital /etc/nginx/sites-enabled/
```

> Faça isto **antes** de remover o clone.

Valide **antes** de aplicar:

```bash
sudo nginx -t
```

`nginx -t` testa sem aplicar. Havendo erro de sintaxe, ele recusa e o Nginx
atual continua servindo normalmente. **Nunca recarregue sem esse teste numa
máquina com aplicação em produção.**

Com o teste passando:

```bash
sudo systemctl reload nginx
```

`reload` relê a configuração sem derrubar conexões em andamento, diferente de
`restart`. O Flux não sente nada.

### 1.5 Emitir o certificado HTTPS

```bash
sudo certbot --nginx -d rsc3-rotavital.duckdns.org
```

O Certbot **edita o arquivo do site sozinho**: acrescenta o bloco `listen 443
ssl`, as linhas de certificado e um segundo bloco `server` redirecionando a
porta 80 para HTTPS. É por isso que o arquivo versionado tem apenas o bloco
da porta 80 — o resto é gerado, e reescrever manualmente atrapalharia a
renovação automática.

> **Se algo der errado:** `sudo rm /etc/nginx/sites-enabled/rota-vital`,
> depois `sudo nginx -t && sudo systemctl reload nginx`. Tudo volta ao que era.

### 1.6 Firewall

**Nada a fazer.** As portas 80 e 443 já estão abertas (é por elas que o Flux
responde), e a 8081 não precisa ser exposta — a aplicação só escuta em
`localhost`.

Essa é a vantagem de entrar atrás do Nginx: zero mudança de firewall.

---

## Parte 2: conectar o GitHub à VM

O workflow precisa entrar na VM por SSH. Gera-se um par de chaves: a pública
fica na VM, a privada vira segredo no GitHub.

### 2.1 Gerar o par de chaves

**Na sua máquina**, não na VM:

```bash
ssh-keygen -t ed25519 -C "github-actions-rota-vital" -f ~/.ssh/rota_vital_deploy -N ""
```

Gera dois arquivos:

| Arquivo | Vai para |
|---|---|
| `rota_vital_deploy.pub` | a VM (pode ser vista por qualquer um) |
| `rota_vital_deploy` | GitHub Secrets (**nunca** commitar) |

> `-N ""` cria a chave sem senha. Uma automação não tem como digitar senha.
> Por isso a chave privada precisa ficar em segredo: quem a tiver, entra na VM.

**Use uma chave nova, dedicada ao deploy.** Não reaproveite a que você usa
para entrar na máquina: se vazar, dá para revogar só ela, sem perder seu
próprio acesso.

### 2.2 Instalar a chave pública na VM

```bash
ssh-copy-id -i ~/.ssh/rota_vital_deploy.pub SEU_USUARIO@IP_DA_VM
```

Ou, manualmente, dentro da VM:

```bash
mkdir -p ~/.ssh && chmod 700 ~/.ssh
echo "CONTEUDO_DO_ARQUIVO_.pub" >> ~/.ssh/authorized_keys
chmod 600 ~/.ssh/authorized_keys
```

Teste antes de seguir:

```bash
ssh -i ~/.ssh/rota_vital_deploy SEU_USUARIO@IP_DA_VM "echo funcionou"
```

Se não imprimir `funcionou`, o deploy também não vai funcionar.

### 2.3 Cadastrar os segredos

No GitHub: **Settings > Secrets and variables > Actions > New repository secret**

| Nome | Valor |
|---|---|
| `VM_HOST` | o IP externo da VM |
| `VM_USUARIO` | seu usuário de SSH na VM |
| `VM_CHAVE_SSH` | o conteúdo **inteiro** de `~/.ssh/rota_vital_deploy` |

Para copiar a chave privada:

```bash
# Windows (Git Bash)
cat ~/.ssh/rota_vital_deploy | clip

# Linux / macOS
cat ~/.ssh/rota_vital_deploy
```

> Cole **tudo**, incluindo `-----BEGIN OPENSSH PRIVATE KEY-----` e
> `-----END OPENSSH PRIVATE KEY-----`, sem espaço sobrando no fim.

Para descobrir o IP externo:

```bash
gcloud compute instances describe flux --zone=us-west1-a \
  --format='get(networkInterfaces[0].accessConfigs[0].natIP)'
```

**O IP muda a cada reinício** se for efêmero. Para fixar: VPC network >
Endereços IP > reserve o IP da instância como estático.

---

## Parte 3: publicar

Com tudo configurado, qualquer push na `main` dispara o pipeline.

Para disparar sem commit novo: aba **Actions** > workflow **CI/CD** >
**Run workflow**.

### Confirmar que funcionou

```bash
curl http://IP_DA_VM/rota-vital/actuator/health
# {"status":"UP"}

curl http://IP_DA_VM/rota-vital/api/v1/hemocentros
# a lista de hemocentros da carga inicial

curl -o /dev/null -w "%{http_code}\n" http://IP_DA_VM/rota-vital/h2-console
# 404 — precisa estar inacessível
```

E confirme que a outra aplicação continua de pé:

```bash
curl -o /dev/null -w "%{http_code}\n" http://IP_DA_VM/
```

---

## Segurança: o que foi decidido e por quê

### O console do H2 desligado em produção

É o critério de aceite mais importante da story.

O H2 console é uma página web com um terminal SQL. Em desenvolvimento é
prático. Exposto na internet, qualquer pessoa que o acesse pode ler, alterar e
apagar qualquer dado, **sem senha** — porque o H2 em memória sobe com usuário
`sa` e senha vazia.

Onde está desligado:
[`application-prod.properties`](../src/main/resources/application-prod.properties).

```properties
spring.h2.console.enabled=false
```

**Quatro camadas** garantem que isso não se perca:

1. o perfil de produção desliga
2. `PerfilProducaoTest` quebra o build se alguém religar
3. o Nginx bloqueia o caminho explicitamente (`deny all`)
4. o workflow confere na URL pública depois do deploy

Uma linha de configuração é fácil de reverter sem querer numa sessão de
depuração. Por isso as outras três.

### A aplicação não é alcançável de fora

`server.address=127.0.0.1` faz o sistema operacional recusar conexões vindas
de outra máquina. O único caminho até a aplicação é o Nginx, que decide o que
passa.

### A aplicação não roda como root

O serviço roda com o usuário `rotavital`, criado sem shell de login e sem
diretório home. Se alguém explorar uma falha, fica limitado ao que esse
usuário pode fazer, que é quase nada.

O systemd acrescenta: `ProtectSystem=strict` (sistema de arquivos somente
leitura, exceto `/opt/rota-vital`), `PrivateTmp=true` (`/tmp` isolado) e
`NoNewPrivileges=true`.

Numa VM compartilhada isso importa em dobro: protege também a aplicação
vizinha.

### Sudo restrito a um script

O usuário de deploy precisa reiniciar o serviço, o que exige root. Em vez de
`NOPASSWD: ALL`, a regra libera apenas `/opt/rota-vital/publicar.sh`:

```
usuario ALL=(root) NOPASSWD: /opt/rota-vital/publicar.sh
```

Se a chave SSH vazar, o atacante consegue publicar um jar — já é ruim — mas
não vira root da máquina, e não alcança a outra aplicação.

### Nenhum segredo no repositório

IP, usuário e chave privada ficam em GitHub Secrets.
`PerfilProducaoTest.semSegredoVersionado` recusa qualquer propriedade de senha
ou token que não venha de variável de ambiente.

---

## Quando algo dá errado

### O job de deploy é pulado com aviso amarelo

Falta cadastrar `VM_HOST`, `VM_USUARIO` ou `VM_CHAVE_SSH`. É o comportamento
esperado enquanto a VM não está configurada: o pipeline segue verde de
propósito, para o time não perder a referência de "verde = está tudo bem".

### `Permission denied (publickey)`

A chave pública não está na VM, ou o `VM_USUARIO` está errado. Teste na mão:

```bash
ssh -i ~/.ssh/rota_vital_deploy SEU_USUARIO@IP_DA_VM
```

### `Host key verification failed`

O IP da VM mudou. Se for efêmero, reserve um estático e atualize `VM_HOST`.

### Passou a verificação interna e falhou a pública

A aplicação subiu, mas o Nginx não está encaminhando. Confira:

```bash
grep -r "rota-vital" /etc/nginx/sites-enabled/    # o include está lá?
sudo nginx -t                                      # a config é válida?
curl localhost:8081/actuator/health                # a app responde localmente?
```

O mais comum é ter esquecido o `include snippets/rota-vital.conf;` dentro do
bloco `server`, ou tê-lo colocado fora dele.

### A aplicação não responde `UP` em 3 minutos

Entre na VM e leia o log:

```bash
sudo systemctl status rota-vital
sudo journalctl -u rota-vital -n 50 --no-pager
```

| Sintoma no log | Causa |
|---|---|
| `Port 8081 was already in use` | outro processo tomou a porta; `sudo ss -tlnp \| grep 8081` |
| `Killed` ou `OutOfMemoryError` | RAM insuficiente; reduza `-Xmx` no serviço |
| `Unable to access jarfile` | o envio falhou; confira `/opt/rota-vital/rota-vital.jar` |

### A outra aplicação parou de responder

Nada no provisionamento toca nela, mas se coincidir com o deploy, o suspeito é
memória. Confira quem está consumindo:

```bash
ps -eo pid,rss,comm --sort=-rss | head -5
```

Se a JVM estiver grande demais, reduza o `-Xmx` em
`/etc/systemd/system/rota-vital.service`, depois:

```bash
sudo systemctl daemon-reload && sudo systemctl restart rota-vital
```

### Voltar para a versão anterior

O `publicar.sh` guarda o jar substituído:

```bash
sudo cp /opt/rota-vital/rota-vital-anterior.jar /opt/rota-vital/rota-vital.jar
sudo systemctl restart rota-vital
```

### Remover tudo

Se precisar desfazer o provisionamento sem afetar o resto da VM:

```bash
sudo systemctl disable --now rota-vital
sudo rm /etc/systemd/system/rota-vital.service /etc/sudoers.d/rota-vital-deploy
sudo rm /etc/nginx/snippets/rota-vital.conf
sudo rm -rf /opt/rota-vital && sudo userdel rotavital
sudo systemctl daemon-reload
```

E remova a linha `include snippets/rota-vital.conf;` do site do Nginx.

---

## Comandos de referência

**Na VM:**

```bash
sudo systemctl status rota-vital      # estado
sudo systemctl restart rota-vital     # reiniciar
sudo journalctl -u rota-vital -f      # log ao vivo
curl localhost:8081/actuator/health   # testar localmente
```

**Arquivos:**

| Caminho | O que é |
|---|---|
| `/opt/rota-vital/rota-vital.jar` | a aplicação em uso |
| `/opt/rota-vital/rota-vital-anterior.jar` | a versão anterior |
| `/opt/rota-vital/publicar.sh` | troca o jar e reinicia |
| `/etc/systemd/system/rota-vital.service` | definição do serviço |
| `/etc/nginx/snippets/rota-vital.conf` | encaminhamento do proxy |
| `/etc/sudoers.d/rota-vital-deploy` | permissão do deploy |

**A porta aparece em três lugares que precisam concordar:**

| Onde | O quê |
|---|---|
| `application-prod.properties` | `server.port=8081` |
| `infra/nginx-rota-vital.conf` | `proxy_pass http://127.0.0.1:8081/` |
| `infra/provisionar.sh` | `PORTA=8081` |

`PerfilProducaoTest.portaConsistenteComOProxy` compara os dois primeiros e
quebra o build se divergirem.

---

## O que ficou fora

| Item | Onde entra |
|---|---|
| HTTPS (certificado TLS) | o Nginx já está lá; falta o certificado |
| PostgreSQL no lugar do H2 | Entrega 02 |
| Deploy sem interrupção | exige duas instâncias e balanceador |
| VM dedicada | uma máquina só, compartilhada por escolha |

O deploy reinicia o serviço, então há alguns segundos de indisponibilidade a
cada publicação. Para o escopo do projeto, é aceitável.
