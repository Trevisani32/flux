# ADR 0005 — Cartão de crédito, fatura e parcelas

- **Status:** Proposto. Confirmar no planejamento da Fatia 05.
- **Data:** 2026-11-06

## Contexto

O cartão de crédito tem regras de data que confundem a maioria dos apps simples:

- a compra entra na fatura conforme o dia de fechamento;
- uma compra parcelada se espalha por várias faturas futuras;
- pagar a fatura não é uma nova despesa, porque a despesa aconteceu na compra;
- o limite disponível depende das parcelas futuras.

O sistema também precisa decidir em que mês o gasto do cartão aparece nos
relatórios.

## Decisão

### Modelo

- `cartoes`: `limite`, `dia_fechamento`, `dia_vencimento` e
  `conta_pagamento_id` (conta de pagamento padrão).
- `faturas`: `cartao_id`, `mes_referencia` (ano e mês **do vencimento**),
  `data_fechamento` e `data_vencimento`.
  - São criadas sob demanda, quando o primeiro lançamento cai nelas.
  - As datas ficam gravadas em cada fatura, para permitir ajustar um mês
    específico.
- `compras`: `cartao_id`, `data_compra`, `descricao`, `valor_total`, `parcelas` e
  `categoria_id`.
- **Cada parcela é um lançamento** com `fatura_id` (e `conta_id` nulo), `compra_id`,
  `numero_parcela` e valor negativo. A regra
  `CHECK ((conta_id IS NULL) <> (fatura_id IS NULL))` garante que um lançamento
  está numa conta **ou** numa fatura.
- `pagamentos_fatura`: `fatura_id` + `lancamento_id`. O lançamento correspondente
  fica na conta, com `tipo = PAGAMENTO_FATURA` e valor negativo.

### Regras

- **Dia que não existe no mês** (31 em fevereiro): vale o último dia do mês.
- **Fatura de uma data:** se o dia da compra é menor ou igual ao dia de fechamento
  daquele mês, a compra entra na fatura que fecha no mês. Senão, entra na
  seguinte.
- **Mês de referência:** se o dia de vencimento é maior que o dia de fechamento, o
  vencimento é no mesmo mês do fechamento. Senão, é no mês seguinte.
- **Parcela k** entra na fatura da compra + (k − 1) meses.
- **Divisão:** cada parcela vale `valor_total / N` truncado no centavo
  (`RoundingMode.DOWN`), e a diferença vai para a 1ª parcela.
  Exemplo: R$ 100,00 em 3x dá 33,34 + 33,33 + 33,33.
- **Totais da fatura:**
  - total = −Σ(lançamentos da fatura);
  - pago = Σ(pagamentos);
  - pendente = total − pago.
- **Situação da fatura:** é calculada, não gravada.
  - aberta: hoje ≤ fechamento;
  - fechada: já fechou e ainda há pendente;
  - paga: pendente ≤ 0.
- **Limite disponível** = limite − Σ(pendente de todas as faturas não quitadas,
  inclusive as futuras).
- **Relatórios:** o gasto no cartão conta no **mês de referência da fatura**, e
  cada parcela conta no mês da sua fatura.
- **Pagamento de fatura** sai da conta, mas fica fora de receitas e despesas.

## Alternativas consideradas

- **Relatórios pela data da compra** (regime de competência): descartado como
  visão padrão. Uma compra grande parcelada distorceria o mês da compra e não
  bateria com o dinheiro que realmente sai. Pode virar uma visão alternativa no
  futuro.
- **Gerar só a parcela corrente e criar as próximas com um job mensal:**
  descartado. O limite disponível e a projeção de saldo precisam das parcelas
  futuras já existentes.
- **Fatura como uma "conta" com saldo negativo:** descartado. Misturaria regras
  de conta (saldo inicial, arquivamento) com regras de fatura (fechamento,
  vencimento, pagamento).

## Consequências

- O limite disponível e a projeção de saldo ficam exatos, porque as parcelas
  futuras existem de verdade.
- Editar uma compra parcelada regrava as parcelas que estão em faturas não pagas.
  Se alguma parcela já está numa fatura paga, só descrição e categoria podem mudar.
- Os cálculos de data (fechamento, mês de referência, último dia do mês) ficam numa
  classe pura, sem banco, com testes unitários para as bordas: compra no dia do
  fechamento, fechamento no dia 31, virada de ano, vencimento antes do fechamento.
