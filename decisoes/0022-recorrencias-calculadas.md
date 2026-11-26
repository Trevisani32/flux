# ADR 0022 — Recorrências calculadas e a chave do período

- **Status:** Aceito. Complementa os ADRs 0002, 0003, 0012 e 0017.
- **Data:** 2026-11-25

## Contexto

Uma recorrência precisa aparecer antes da confirmação, sem contaminar saldos, relatórios, limites, importações ou a exportação de lançamentos.

## Decisão

- As ocorrências são calculadas sob demanda; só as tratadas são persistidas em
  `ocorrencias_tratadas`: efetivada, ligada a lançamento ou compra, ou pulada.
- A chave é o período: o primeiro dia do mês, a segunda-feira da semana ou o primeiro dia do
  ano. Há no máximo uma ocorrência tratada por recorrência e período.
- As datas saem sempre da âncora. Uma mensal no dia 31 cai no último dia dos meses curtos; a
  anual de 29/02 cai em 28/02 nos anos não bissextos.
- `ocorrencias_desde` guarda o cadastro ou a retomada. A não tratada aparece dos últimos 60
  dias até 12 meses à frente; a pausa por conta, cartão ou categoria arquivados é calculada.
- Confirmar grava um lançamento na conta ou uma compra à vista no cartão. O vínculo é com a
  compra, pois editá-la regrava parcelas. As FKs do lançamento e da compra usam `CASCADE`;
  ignorar um importado solta o vínculo sem apagar a ocorrência tratada.
- O módulo `recorrencias` depende de contas, cartões, categorias e lançamentos. Ele implementa
  `PrevistosNaLista` e `UsoDaCategoria`; lançamentos expõe `LancamentosDasRecorrencias`.
- As ações que gravam usam as travas de contas, cartões, categorias e recorrências nessa ordem.

## Alternativas consideradas

- Gravar os previstos em `lancamentos`.
- Usar a data prevista como chave.
- Usar uma tabela única com JSON para o histórico das ocorrências.
- Guardar ocorrências desde o início, inclusive antes do cadastro.
- Fazer a recorrência ser uso da conta.

## Consequências

Os previstos ficam fora dos totais e do saldo até serem confirmados. Mudar o dia não recria período já tratado. A lista
de lançamentos consulta previstos por `ObjectProvider`, evitando ciclo de beans. O custo é calculado em memória: no
pior caso, 200 recorrências semanais em 12 meses somam 10.600 datas. Saldo, relatórios, exportação de lançamentos,
importação e limites não ganham uma nova situação. Projeção, importação e orçamento podem reutilizar esta base.
