# ADR 0017 — Cartão de crédito, faturas e parcelas

- **Status:** Aceito. Substitui o [ADR 0005](0005-cartao-fatura-parcelas.md).
- **Data:** 2026-11-12

## Contexto

O [ADR 0005](0005-cartao-fatura-parcelas.md) propôs o modelo do cartão antes de existir código de
lançamentos. Do ADR 0005 até a Fatia 05, a Fatia 03 fez da transferência um vínculo entre dois
lançamentos ([ADR 0013](0013-lancamentos-e-transferencias.md)), e a Fatia 04 trouxe a importação, as regras
e a conversão em transferência ([ADR 0014](0014-importacao-de-extratos.md),
[ADR 0015](0015-regras-de-categorizacao.md) e [ADR 0016](0016-conversao-de-importados-em-transferencia.md)).
As 55 decisões da Fatia 05 foram aprovadas pelo dono em 2026-11-12, depois de o modelo ser conferido rodando. A
fatia ficou em três partes: 05a (cartões, faturas, compras e estornos), 05b (pagamento e limite disponível) e 05c
(importação de fatura e regras com cartão).

A conferência achou o que o ADR 0005 não fechava:

- **Onde moram os dados.** A organização prevista no início do projeto punha tudo em `cartoes`, mas as
  faturas e as compras são grupos de lançamentos, e o ajuste de fechamento move lançamentos: com tudo em
  `cartoes`, `lancamentos` teria de declarar interfaces para os nomes dos cartões, as faturas e a recolocação
  das compras.
- **A data da parcela.** Com só `fatura_id`, uma parcela podia ficar numa data qualquer, e a lista do mês
  não bateria com a fatura.
- **A FK da importação.** A FK `(importacao_id, conta_id, usuario_id)` da V5 não confere nada quando
  `conta_id` é nulo (`MATCH SIMPLE`): um importado de fatura aceitaria uma importação inexistente.
- **O código que supõe conta.** A lista do mês, os "últimos lançamentos", o `/api/lancamentos/{id}`, os
  pares da conversão e a soma por conta quebravam (500) ou ganhavam um grupo nulo com um lançamento sem
  conta.
- **A troca de dias.** Com os dias novos valendo já na fatura aberta, 23% das combinações conferidas faziam a
  aberta fechar antes de hoje.

## Decisão

### Módulos

- `cartoes` fica só com o cartão, como `contas`: cadastro, dias, limite, a classe pura
  `CalendarioDoCartao` e a API `CartoesConsulta` (`travar`, `buscar` e `listar`). Ele declara
  `MovimentoDosCartoes` (a fatura aberta e a seguinte de cada cartão; na 05b, o pendente), `UsoDoCartao` e
  `TrocaDeDias`, e implementa o `UsoDaConta` de `contas` (a conta de pagamento é uso da conta).
- Faturas, compras, parcelas e, na 05b, pagamentos ficam em `lancamentos`, como a transferência.
  `lancamentos` implementa `MovimentoDosCartoes`, `UsoDoCartao` e `TrocaDeDias`.
- Direção: `lancamentos → cartoes → contas`; na 05c, `importacao` e `categorizacao → cartoes`. Sem ciclo.

### Modelo (V8)

- `cartoes`: nome (único entre os ativos, pela chave dos nomes de conta), `limite > 0`, `dia_fechamento`,
  `dia_vencimento`, `conta_pagamento_id` (FK com `usuario_id`, `RESTRICT`), cor e `arquivado_em`. O
  `CHECK` dos dias recusa o vencimento no mesmo mês com fechamento depois do dia 27.
- `faturas`: única por cartão e `mes_referencia` (o primeiro dia do mês do vencimento), com
  `data_fechamento < data_vencimento`, o vencimento dentro do mês de referência, `fechamento_ajustado` e
  `fechamento_normal` (o fechamento pelos dias que valiam para ela).
  É gravada sob demanda, com as datas do momento, e não é apagada sozinha: sai com o cartão (`CASCADE`) ou,
  sem nada dentro, na troca de dias do cartão vazio (seção Datas). A que tem lançamento segura o cartão pelas
  FKs dos lançamentos. Um índice por `usuario_id` serve à lista dos cartões, que lê todas as faturas do
  usuário, e à exclusão dele.
- `compras`: `tipo` `COMPRA` ou `ESTORNO`, `data_compra`, `valor_total`, `parcelas` (1 a 48),
  `parcela_inicial` (1 a `parcelas`) e no mínimo 1 centavo por parcela. O estorno tem 1 parcela. A
  descrição, a categoria e a observação ficam nas parcelas.
- Em `lancamentos`, `conta_id` aceita nulo e entram `cartao_id`, `fatura_id`, `compra_id` e
  `numero_parcela`, com:
  - `CHECK ((conta_id IS NULL) <> (fatura_id IS NULL))`: numa conta ou numa fatura;
  - cartão, fatura, compra e número sempre juntos; na fatura, só receita e despesa;
  - o importado só em conta até a 05c (por causa do `MATCH SIMPLE`);
  - a FK `(fatura_id, cartao_id, usuario_id, data) → faturas (id, cartao_id, usuario_id,
    data_vencimento) ON UPDATE CASCADE`: a parcela está no cartão da fatura e na data do vencimento dela,
    e mudar o vencimento da fatura leva a data das parcelas junto;
  - `(compra_id, cartao_id, usuario_id) → compras` e `(cartao_id, usuario_id) → cartoes`, `RESTRICT`;
  - uma parcela de cada número por compra.
- Toda FK leva `usuario_id`, e as de `usuarios` são `CASCADE` ([ADR 0007](0007-multiusuario-isolamento.md)).

### Datas (`CalendarioDoCartao`)

- Dia que não existe vira o último do mês. Vencimento maior que o fechamento: vence no mesmo mês; senão, no
  seguinte. O mês de referência é o do vencimento.
- **As datas de um mês:** as da fatura gravada; sem fatura gravada, as padrão, com o fechamento preso entre o
  dia seguinte ao fechamento da gravada anterior e a véspera do fechamento da gravada seguinte. Assim os
  fechamentos nunca saem de ordem depois de uma troca de dias.
- **A fatura de uma data:** a primeira que fecha no dia da data ou depois, com os fechamentos gravados. A
  busca começa no mês da data, porque a fatura de uma data nunca vence antes dele; começar na fatura padrão − 1
  perderia as faturas gravadas com os dias antigos depois de uma troca.
- **A situação**, calculada: futura, aberta ou fechada (na 05b, paga).
- **Os limites do ajuste:** do dia seguinte ao fechamento da anterior à véspera do vencimento desta e do
  fechamento da seguinte, com as vizinhas lidas sem esta fatura. "Voltar ao fechamento normal" usa o
  fechamento normal da fatura, preso nesses limites: o das datas com que ela foi gravada ou, quando uma troca
  de dias a recalcula, o dos dias novos (`fechamento_normal`). Sem fatura gravada, o dos dias do cartão.
  Assim, depois de uma troca, o normal da aberta e das anteriores continua o dos dias de antes.
- **A troca de dias** vale da fatura seguinte à aberta em diante; a aberta e as anteriores ficam com os dias de
  antes. Da véspera do mês mais antigo entre a primeira fatura com lançamento e o mês de 10 anos atrás (a data
  mais antiga que ainda se lança) até a aberta, as faturas que ainda não existem são gravadas com as datas de
  agora. Assim nenhum período com lançamento muda, e a compra esquecida, lançada depois num mês passado, cai
  pelos dias que valiam na época. As gravadas vazias, de um ajuste ou de uma troca anterior, não puxam esse
  começo para trás. As gravadas depois da aberta voltam às datas dos dias novos, sem ajuste, e a primeira delas
  nunca fecha antes do dia seguinte ao fechamento da aberta. As compras com data depois do fechamento da aberta
  trocam de fatura quando for o caso.
- **Exceção da troca, aprovada pelo dono em 2026-11-12:** no cartão vazio, sem nenhum lançamento (parcela ou
  estorno) e sem nenhuma fatura com o fechamento ajustado, como o cartão novo ou aquele cujas compras foram
  todas excluídas, os dias novos valem já, inclusive na fatura aberta. As faturas que restaram, todas sem nada
  dentro, são apagadas na mesma transação, com a trava de cartões, e a próxima compra grava as dela com os
  dias novos. É também a única exceção à regra de que a fatura não é apagada sozinha, e vale só para faturas
  sem nada dentro. A prévia dos dias diz isso ao diálogo (`valemJa`). Com qualquer lançamento ou ajuste, vale
  a regra acima.
- **O que conta como ajuste:** a fatura com `fechamento_ajustado` verdadeiro. A gravada só por uma compra
  (mesmo excluída depois), por uma troca de dias ou por um ajuste desfeito ("Voltar ao fechamento normal" põe
  a coluna de volta em falso) não conta.

### Compras, parcelas e estornos

- **Data:** a da compra e a do estorno vão de 10 anos antes de hoje a 12 meses depois de hoje, as duas pontas
  valendo, pelo `Clock` (`hoje.minusYears(10)` e `hoje.plusMonths(12)`). A faixa vale para a data que se
  lança: ao criar, na prévia e, na edição, só quando a data muda; a compra que passou de 10 anos ainda se edita
  com a data que tem.
- **Quantas faturas uma troca grava:** só os meses que ainda não existem, da véspera do mês de 10 anos atrás (ou
  da primeira fatura com lançamento, se for antes) até a aberta, que pelos dias do cartão fica até 2 meses à
  frente. A primeira troca de um cartão grava no máximo 124 faturas enquanto nenhuma compra dele tem mais de 10
  anos; esse teto cresce um por mês em que a compra mais antiga passa da janela (com 20 anos de uso, uns 244).
  As trocas seguintes gravam só os meses desde a anterior, porque os outros já estão gravados.
- **Divisão** (`Parcelamento.dividir`): o total ÷ N truncado no centavo (`RoundingMode.DOWN`), com o resto na
  1ª parcela.
- A parcela k da compra cai na fatura da data da compra + (k − 1) meses de referência, na data do vencimento
  dela. A compra em andamento grava só as parcelas de `parcela_inicial` em diante.
- **Editar** com outro cartão, data, valor, parcelas ou primeira parcela apaga as parcelas e grava de novo,
  na mesma transação, conferindo os limites só pelo que entra; descrição, categoria e observação atualizam as
  parcelas que existem. Com a data e o cartão iguais, a 1ª parcela fica no mês em que está, sem a fatura ser
  buscada pela data de novo: na compra em andamento, a fatura da data não tem lançamento e pode não estar
  gravada com os dias de antes.
- **Ajuste e troca de dias** recolocam as compras do trecho que mudou: a compra inteira anda, e cada parcela
  muda de fatura e de data.
- **Estorno:** uma compra `ESTORNO` com 1 parcela, receita positiva na fatura da data dele, com categoria de
  receita opcional. O `CHECK` de sinal, a FK pela natureza e as regras não mudam.
- **Limites:** cada parcela e cada estorno conta 1 no total e 1 no mês do vencimento dele.

### Lançamentos

- A lista do mês mostra uma linha por parcela e por estorno, na data do vencimento da fatura, com o cartão,
  a compra e a parcela. O filtro de conta vira "Conta ou cartão" (`?cartao=`, nunca com `?conta=`); com um
  cartão, a lista traz a fatura do mês.
- O `/api/lancamentos/{id}` responde `404` para o lançamento de fatura: ele se edita pela compra.
- Os "últimos lançamentos" do Início, os pares e o achado da conversão, a soma por conta e as regras (até a
  05c) só olham lançamentos de conta.

### Travas

A ordem é contas → cartões → categorias, com `pg_advisory_xact_lock(hashtextextended('cartoes:' ||
usuarioId, 0))` para os cartões ([ADR 0012](0012-categorias-padrao-e-interfaces-para-lancamentos.md)).
Compra e estorno travam cartões e categorias; cartão novo ou editado trava contas e cartões; ajuste, troca de
dias, arquivar, reativar e excluir o cartão travam os cartões.

### API (05a)

- `GET`/`POST /api/cartoes`, `GET`/`PUT`/`DELETE /api/cartoes/{id}`, `POST /api/cartoes/{id}/arquivar` e
  `/reativar`, e `POST /api/cartoes/{id}/previa-dos-dias`, que diz a partir de qual fatura os dias novos
  valem, ou que valem já, sem gravar.
- `GET /api/cartoes/{id}/faturas/{mes}` e `PUT`/`DELETE /api/cartoes/{id}/faturas/{mes}/fechamento`.
- `POST /api/compras/previa`, `POST /api/compras`, `GET`/`PUT`/`DELETE /api/compras/{id}`, e o mesmo em
  `/api/estornos`.
- `GET /api/lancamentos?cartao=`.

### O que fica para a 05b e a 05c

- **05b (V9):** o tipo `PAGAMENTO_FATURA` nos `CHECK`s de tipo e de sinal, a coluna `fatura_paga_id` no
  lançamento da conta (no lugar da tabela `pagamentos_fatura` do ADR 0005) e `faturas.paga_fora_em`. A
  situação paga, a trava da fatura paga, "Converter em pagamento de fatura" e o limite disponível (limite −
  pendente de todas as faturas, inclusive as futuras). Uma fatura com pagamento ou marcada como paga fora do
  Flux também deixa de ser vazia: o cartão com ela segue a troca de dias normal, e a exceção do cartão vazio
  não a apaga.
- **05c (V10):** `importacoes` com cartão e fatura, a FK do importado de fatura, `compras.importacao_id` e
  as regras com cartão. A chave da parcela importada, `CPAR:{cartaoId}:{hash}:{k}`, é gravada também nas
  parcelas futuras. A linha importada segue a mesma faixa de datas da compra: de 10 anos antes de hoje a 12
  meses depois.

## Alternativas consideradas

- **Tudo em `cartoes`, como previa a organização do início do projeto:** descartado. Para não haver ciclo,
  `lancamentos` teria de declarar interfaces para os nomes dos cartões, as faturas e a recolocação das compras.
- **Só `fatura_id` na parcela, como no ADR 0005:** descartado. A parcela poderia ficar numa data qualquer, ou
  numa fatura de outro cartão.
- **A parcela na data da compra** (a 1ª) e um mês depois para cada parcela: descartado. O mês da lista não
  bateria com o da fatura nem com os relatórios.
- **Os dias novos valendo já na fatura aberta:** descartado pela conferência: a aberta podia fechar antes de
  hoje. Fica só no cartão vazio, sem lançamento nem ajuste, onde não há compra a proteger.
- **O cartão vazio como o que não tem nenhuma fatura gravada:** descartado pelo dono. O cartão cujas compras
  foram todas excluídas ficava fora da exceção, e esse é o jeito comum de descobrir os dias errados: lançar
  uma compra, ver que ela caiu na fatura errada, excluí-la e corrigir os dias.
- **Compra sem data mínima:** descartado. Uma data muito antiga faria a troca de dias seguinte gravar
  milhares de faturas numa transação, com a trava de cartões presa.
- **O estorno como despesa positiva** com a categoria da compra: descartado. Exigiria mudar o `CHECK` de
  sinal, os totais da lista e as regras.
- **Regravar as parcelas uma a uma, mantendo os ids:** descartado. Apagar e gravar de novo é mais simples, e
  nada referencia a parcela.
- **A tabela `pagamentos_fatura`:** descartada para a 05b. A coluna no lançamento da conta faz o mesmo com um
  vínculo a menos.

## Consequências

- O lançamento deixa de ter sempre uma conta. Todo código que lê `conta_id` precisa saber que o lançamento
  pode ser de fatura; os testes de cada consulta conferem isso.
- O banco garante a data e o cartão de cada parcela. Mudar o vencimento de uma fatura gravada muda a data das
  parcelas dela no mesmo comando.
- Trocar os dias do cartão grava faturas que não existiam (da véspera do mês de 10 anos atrás, ou da primeira
  com lançamento, até a aberta). Elas ficam vazias se não tiverem lançamento e saem com o cartão. A primeira
  troca grava até 124 delas, mais uma por mês em que a compra mais antiga passa dos 10 anos; as seguintes, só
  os meses desde a anterior. No cartão vazio, sem lançamento e sem ajuste de fechamento, a troca não grava
  nada: apaga as faturas que restaram, todas sem nada dentro, e os dias novos valem já, inclusive na aberta.
- A Fatia 06 recebe o gasto do cartão no mês de referência, e o estorno como receita.
- O ADR 0005 fica como registro, com o status "Substituído pelo ADR 0017".
