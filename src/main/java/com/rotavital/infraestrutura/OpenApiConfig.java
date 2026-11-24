package com.rotavital.infraestrutura;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Rota Vital API",
                version = "0.1.0",
                description = """
                        ## O que é o Rota Vital?

                        Plataforma web para distribuição de hemocomponentes, gestão de estoque,
                        alocação de bolsas compatíveis e roteirização respeitando cadeia fria e
                        janelas de tempo.

                        A rede de sangue precisa garantir o componente certo (compatível e dentro
                        da validade), no lugar certo, no tempo certo e na temperatura certa.
                        Falhas geram desabastecimento, descarte por vencimento e risco ao paciente.
                        O **Rota Vital** integra estoque, compatibilidade e roteirização em um só lugar.

                        ---

                        ## Recursos principais

                        | Recurso | Base path | Descrição |
                        |---|---|---|
                        | Hemocentros | `/api/v1/hemocentros` | Cadastro e estoque de hemocomponentes |
                        | Hospitais | `/api/v1/hospitais` | Unidades receptoras e suas requisições |
                        | Bolsas | `/api/v1/bolsas` | Ciclo de vida de cada bolsa de sangue |
                        | Requisições | `/api/v1/requisicoes` | Pedidos hospitalares e alocações |
                        | Rotas | `/api/v1/rotas` | Roteirização de entregas |
                        | Indicadores | `/api/v1/indicadores` | Métricas operacionais, estatísticas descritivas e cobertura |

                        ---

                        ## Códigos de resposta

                        | Código | Significado |
                        |---|---|
                        | `200` | OK — consulta retornou resultado |
                        | `201` | Created — recurso criado; header `Location` aponta para ele |
                        | `204` | No Content — operação realizada, sem corpo de retorno |
                        | `400` | Bad Request — corpo inválido (campos apontados na resposta) |
                        | `404` | Not Found — recurso inexistente |
                        | `409` | Conflict — regra de negócio violada (bolsa incompatível, status proibido, vínculo existente) |

                        ---

                        ## Dados sintéticos

                        Ao subir, a aplicação carrega automaticamente **4 hemocentros, 6 hospitais,
                        100 bolsas e requisições** para que a API já responda com conteúdo real.
                        Todos os dados são sintéticos, sem informações reais de doadores ou
                        pacientes (conformidade com a LGPD).

                        ---

                        ## Avisos

                        - A compatibilidade ABO/Rh implementada é **didática** e não substitui
                          protocolo clínico.
                        - A telemetria de temperatura/GPS é **simulada**.
                        - Em desenvolvimento, o banco é **H2 em memória,** os dados são perdidos
                          al reiniciar a aplicação.
                        """,
                contact = @Contact(
                        name = "Equipe RSC-3 · CESAR School",
                        email = "rsc3@cesar.school",
                        url = "https://github.com/rsc3-pixel/Projeto3-2026.2"
                ),
                license = @License(
                        name = "Projeto acadêmico — uso restrito",
                        url = "https://github.com/rsc3-pixel/Projeto3-2026.2"
                )
        ),
        servers = {
                @Server(
                        url = "https://rsc3-rotavital.duckdns.org",
                        description = "Produção (deploy automático via GitHub Actions)"
                ),
                @Server(
                        url = "http://localhost:8080",
                        description = "Desenvolvimento local (H2 em memória)"
                )
        },
        tags = {
                @Tag(name = "Hemocentros", description = "Cadastro de hemocentros e consulta de estoque de bolsas e rotas vinculadas"),
                @Tag(name = "Hospitais", description = "Cadastro de hospitais e consulta das requisições por unidade"),
                @Tag(name = "Bolsas", description = "Ciclo de vida de bolsas de hemocomponentes: cadastro, consulta e atualização de status (FEFO)"),
                @Tag(name = "Requisições", description = "Pedidos hospitalares de hemocomponentes, itens e alocações de bolsas compatíveis"),
                @Tag(name = "Rotas", description = "Roteirização de entregas com controle de cadeia fria e embarque de bolsas"),
                @Tag(name = "Indicadores", description = "Métricas operacionais, medidas de tendência central, dispersão, descarte e cobertura de demanda")
        }
)
public class OpenApiConfig {
}
