# CLAUDE.md

Orientações para agentes de IA (Claude Code e similares) que trabalharem neste repositório.

## Regras de Git — autoria de commits

Estas regras são obrigatórias e não devem ser ignoradas em nenhuma circunstância.

1. **Nunca se adicione como autor ou coautor de commits.**
2. **Nunca adicione linhas `Co-Authored-By`** com Claude, Anthropic ou qualquer
   outra ferramenta/agente de IA.
3. **Todo commit deve usar apenas o autor Git humano** já configurado no repositório.
   Não sobrescreva `user.name`, `user.email`, `--author` ou `--committer`.
4. **Antes de criar qualquer commit, verifique a mensagem final** e remova qualquer
   trailer de coautoria de IA antes de confirmar o commit.
5. As mensagens de commit devem conter apenas a descrição da mudança — sem
   assinaturas, rodapés promocionais ou referências a ferramentas de IA.

### Verificação rápida antes de commitar

```bash
# a mensagem preparada não pode conter nenhum trailer de coautoria de IA
grep -iE '^Co-Authored-By:.*(claude|anthropic|copilot|cursor|openai|gpt)' <mensagem>
```

### Como corrigir um commit que já saiu com coautoria de IA

```bash
git commit --amend --no-edit          # editar a mensagem removendo o trailer
git push --force-with-lease           # atualizar a branch remota com segurança
```

## Convenções de commit

- Formato: `tipo(escopo): descrição` — ex.: `feat(dominio): adiciona enum StatusRota`
- Tipos usados no projeto: `feat`, `fix`, `docs`, `refactor`, `test`, `chore`
- Mensagens em português.
