# ADR 0003 — Saldo calculado a partir dos lançamentos

- **Status:** Aceito
- **Data:** 2026-11-06

## Contexto

O saldo de uma conta muda em toda inclusão, edição, exclusão e importação de
lançamento, inclusive em edições retroativas. Guardar o saldo numa coluna obriga a
atualizar essa coluna em todos esses caminhos. Esquecer um deles, ou duas
operações concorrentes, deixa o saldo divergente sem ninguém perceber.

O volume é pessoal: alguns milhares de lançamentos por ano.

## Decisão

- **Não existe coluna de saldo atual.**
- Saldo de uma conta numa data X = `saldo_inicial` +
  `SUM(valor)` dos lançamentos efetivados da conta com `data <= X`.
- Saldo atual = saldo na data de hoje, segundo o `Clock`. Um lançamento com data
  futura aparece na lista, mas só entra no saldo quando a data chegar.
- Um lançamento com data anterior à `data_saldo_inicial` da conta é recusado. Na
  importação, ele aparece na pré-visualização como "anterior ao saldo inicial" e é
  ignorado. Isso evita ambiguidade sobre o que o saldo inicial já inclui.
- Índice `(usuario_id, conta_id, data)` em `lancamentos`.
- A evolução do saldo (Fatia 06) e a projeção (Fatia 07) usam a mesma regra, a
  partir da mesma fonte.

## Alternativas consideradas

- **Coluna `saldo_atual` atualizada a cada operação:** descartada pelo risco de
  divergência e pela complexidade de concorrência.
- **Fotografias mensais de saldo desde já:** descartadas como otimização
  prematura nesse volume.

## Consequências

- O saldo está sempre consistente com os lançamentos, por construção.
- Edições retroativas se refletem sozinhas em todos os saldos e relatórios.
- Se um dia ficar lento, o que é improbável, dá para adicionar fotografias mensais
  **como cache derivado**, sem mudar a fonte da verdade.
