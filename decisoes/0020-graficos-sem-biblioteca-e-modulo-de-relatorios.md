# ADR 0020 — Gráficos sem biblioteca e o módulo de relatórios

- **Status:** Aceito. Complementa os ADRs 0001 e 0003; ajusta o 0002.
- **Data:** 2026-11-24

## Contexto

A Fatia 06 decide a biblioteca de gráficos e põe em prática o módulo `relatorios` do ADR 0001. O módulo de produto não
fechava a faixa de meses, o saldo passado com contas arquivadas, a média com poucos meses nem o arredondamento da comparação.

## Decisão

- Não há biblioteca de gráficos. Três componentes em `shared/graficos/` desenham SVG, com escalas puras testadas, cores
  pelos tokens `--flux-receita`, `--flux-despesa` e `--flux-barra-neutra`, teclado, leitor de tela e tabela em HTML comum.
  A ordem fixa, a legenda e a tabela não dependem só do par verde e vermelho.
- Desenhar não é fazer conta com dinheiro: os números vêm prontos da API e a escala só os converte em posição.
- O módulo `relatorios` é só de leitura, consulta SQL com `JdbcClient`, sempre filtra pelo usuário, depende só de
  `compartilhado` e ninguém depende dele. Cada pedido roda numa transação de leitura em `REPEATABLE READ`.
- Os meses, o saldo, a média e a comparação seguem as regras da Fatia 06a. O percentual de comparação dos relatórios é
  inteiro, com `HALF_UP` sobre a média arredondada. A regra de uma casa do ADR 0002 continua para o orçamento da Fatia 07.
- “Próximas faturas” não pertence a `relatorios`: vem de `GET /api/cartoes`, pela situação e pelo pendente da 05b, sem
  duplicar a regra em SQL.

## Alternativas consideradas

- ECharts: peso, tema em JavaScript e acessibilidade automática.
- Chart.js: canvas que o leitor de tela não lê e cores fora dos tokens.
- Dashboard no Início, ou contar no saldo só contas ativas de hoje ou todas as contas.

## Consequências

Não há dependência nem peso novo. Os gráficos permanecem simples, sem zoom e sem animação. Reativar uma conta a devolve a
todos os meses; mudar a principal de uma subcategoria muda o passado, como na lista. A evolução do saldo lê o banco em
cada pedido; se necessário, um cache derivado pode ser decidido depois.
