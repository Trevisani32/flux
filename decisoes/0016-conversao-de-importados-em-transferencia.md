# ADR 0016 — Conversão de importados em transferência

- **Status:** Aceito
- **Data:** 2026-11-11

## Contexto

A Fatia 04 ficou em três partes: a 04a trouxe a importação de extratos
([ADR 0014](0014-importacao-de-extratos.md)), a 04b, as regras de categorização
([ADR 0015](0015-regras-de-categorizacao.md)), e a 04c traz a conversão de importados em transferência.
Uma transferência entre contas próprias aparece nos dois extratos: até aqui, ela entrava como despesa
numa conta e receita na outra, nos totais e nos gastos por categoria.

O [ADR 0013](0013-lancamentos-e-transferencias.md) já fez da transferência um vínculo
(`transferencias`) entre dois lançamentos, com a data, o valor e a descrição em cada lado, para que a
Fatia 04 pudesse ligar dois importados com a data e a descrição de cada banco sem migration. Faltava
decidir:

- **De onde a conversão parte e quem pode ser o outro lado**, inclusive quando o outro lado não tem
  extrato (o saque que vai para a carteira, a aplicação num investimento).
- **O que acontece com cada lado:** a categoria, a descrição, a chave de importação e a observação.
- **Editar, excluir e desfazer.** O vínculo tem `ON DELETE CASCADE` para os lados (V4): apagar o vínculo
  primeiro apaga os dois lançamentos, e com eles as chaves de importação. A conferência do mockup mostrou
  isso rodando.
- **Desfazer a importação de um lado.** O desfazer da 04a apaga os lançamentos da importação por
  `importacao_id`; com um lado convertido, o outro ficaria como uma transferência órfã. O ADR 0014
  previa recusar o desfazer até o usuário desfazer cada conversão à mão.

As decisões 26 a 36 do mockup da 04b, aprovadas pelo dono em 2026-11-11, fecham o comportamento; este ADR
registra como elas ficam no código.

## Decisão

### Onde mora

- Em `lancamentos`, num `ConversaoService`: a conversão só lê e grava lançamentos e o vínculo. Não há
  módulo novo, e as dependências entre módulos não mudam.
- A importação continua usando só a API pública `LancamentosImportados`, que ganha os pares sugeridos do
  resultado (`paresSugeridos`), os importados convertidos do histórico (`convertidos`) e um `desfazer`
  que desfaz as conversões antes de apagar.

### O par e o lado novo

- A conversão parte de um importado (receita ou despesa) que não está excluído nem numa transferência,
  numa conta ativa. Dois lançamentos manuais não se convertem: o usuário exclui e cria a transferência.
- O par tem o mesmo valor com o sinal oposto, fica em outra conta ativa, é receita ou despesa (manual ou
  importada) que não está excluída nem numa transferência e tem até 5 dias de diferença, para antes ou
  para depois. O mais perto da data vem primeiro; no empate, o mais antigo e depois o menor id.
  `GET /api/lancamentos/{id}/pares` devolve no máximo 20.
- Sem par, o lado novo é um lançamento manual com a mesma data e o valor oposto, numa conta ativa que
  não é a do importado e já existia na data (a data do saldo inicial dela). Só ele conta nos limites de
  lançamentos (1.000 por mês e 100.000 no total). Os `422` da conta do lado novo levam `"campo": "contaId"`,
  para a tela pôr a mensagem no campo da conta sem recarregar os pares; a conta que começou depois da data
  aparece desabilitada no seletor, com o motivo.
- `POST /api/lancamentos/{id}/converter` recebe o par (`parId`) ou a conta do lado novo (`contaId`),
  nunca os dois, e a descrição. A escolha é conferida de novo dentro da trava: o par inexistente, de
  outro usuário, excluído ou que não serve mais dá `422` `PAR_INVALIDO`, com a mesma mensagem.

### O que muda em cada lado

- Os dois lados viram `TRANSFERENCIA` e perdem a categoria (a FK da natureza exige). Data, valor, conta,
  observação, descrição do extrato (`descricao_banco`) e chave de importação ficam: reimportar o extrato
  continua dando "já importada". Uma descrição vale para os dois lados; vazia, "Transferência".
- Converter e desfazer mudam `atualizado_em`: no histórico da importação, o importado conta como
  editado, como no "Aplicar regras" (ADR 0015). Vale também para o importado de outra importação que o
  desfazer da importação solta (o outro lado da transferência): ele continua contando como editado.
- Na API, o item da transferência ganha `lados` (a saída e a entrada, com a conta, a data, a origem, a
  descrição do extrato e a observação de cada uma): a convertida tem datas diferentes e um "No extrato"
  por lado. A lista não muda: sem filtro de conta, a transferência aparece na data da saída; com o filtro
  de uma conta, na data do lado dela (ADR 0013).

### Editar, excluir e desfazer

- **Convertida** é a transferência com um lado importado; a lançada à mão tem os dois lados manuais.
- Editar a convertida usa o `PUT /api/transferencias/{id}` de sempre: o pedido repete o valor, as contas e
  a data da saída, e só a descrição e a observação mudam, nos dois lados. Qualquer outra mudança é o `422`
  `IMPORTADO_TRAVADO`. Com uma das contas arquivada, ela fica só leitura, como a manual, e a mensagem manda
  reativar a conta para "editar ou desfazer" (a manual continua com "editar ou excluir").
- A convertida não se exclui (`DELETE` dá `422` `TRANSFERENCIA_CONVERTIDA`, antes de olhar as contas): apagar
  os lados levaria as chaves de importação. Ela se desfaz, por `POST /api/transferencias/{id}/desfazer`: os dois lados voltam a
  ser receita e despesa, pelo sinal, sem categoria; o importado volta com a descrição do extrato, e o
  manual (inclusive o lado novo) fica com a descrição que tem. A transferência manual não se desfaz
  (`422` `TRANSFERENCIA_MANUAL`), e nenhuma se desfaz com uma das contas arquivada.
- **Primeiro os lados se soltam, depois o vínculo sai:** os lados são gravados sem `transferencia_id`
  (com `flush`) antes do `DELETE` do vínculo. O `CASCADE` da V4 continua como está.
- Um lado de transferência nunca fica excluído (ignorado): `CHECK (transferencia_id IS NULL OR ignorado_em
  IS NULL)`, na V7. A API já não alcança um lado por `/api/lancamentos/{id}`; o `CHECK` segura o resto.

### Desfazer a importação de um lado

- Substitui o que o ADR 0014 previa: desfazer a importação desfaz junto as conversões dos lançamentos
  dela, na mesma transação e dentro da trava de contas, antes de apagá-los. O outro lado volta a ser
  receita ou despesa, sem categoria.
- Se o outro lado está numa conta arquivada, o desfazer inteiro é recusado (`422` `CONTA_ARQUIVADA`, com
  a data da transferência e a conta).
- O histórico (`GET /api/importacoes`) diz, em cada importação, os lançamentos dela que estão em
  transferências, com a conta do outro lado e o tipo que ele volta a ter, para a confirmação avisar.

### Pares sugeridos

- O resultado da importação (`POST /api/importacoes`) traz `pares`: as linhas gravadas que têm um só par
  possível. Um par que serviria a mais de uma linha fica de fora, porque converter uma tiraria o par da
  outra. Nada se converte sozinho: "Converter" abre o diálogo com o par escolhido.

### Travas, isolamento e regras

- Converter e desfazer pegam só a trava de contas, como a transferência (ADR 0012): a categoria só sai, e
  tirar um uso não corre com a exclusão dela. Dentro da trava, a conta de cada lado é conferida de novo.
- Id que não serve segue o [ADR 0007](0007-multiusuario-isolamento.md): `404` na URL (o lançamento e a
  transferência de outro usuário) e `422` no corpo (o par e a conta do lado novo de outro usuário, com a
  mesma mensagem da inexistente).
- As regras de categorização nunca tocam um lado de transferência: `LancamentosParaRegras` já deixa as
  transferências de fora.

## Alternativas consideradas

- **Converter qualquer lançamento:** descartado pelo dono. Dois manuais se resolvem excluindo e criando a
  transferência.
- **Só um importado do outro lado, só o mesmo dia ou 3 dias, como a recorrência:** descartados. A TED de
  sexta cai na segunda, com feriado no meio; o lado manual é o de quem lança antes de importar.
- **Só ligar lançamentos que já existem:** descartado. A carteira e o investimento sem extrato pediriam o
  lançamento à mão antes.
- **Cada lado com a sua descrição, ou uma marca de "convertida" na lista:** descartados. A convertida se
  comporta como a manual.
- **Deixar mudar a data do lado manual:** descartado. Contas, datas e valor vêm dos extratos.
- **Desfazer apagando o lado lançado na conversão, ou "Excluir" que desfaz e ignora:** descartados.
  Nenhum lado some sem o usuário pedir.
- **Recusar o desfazer da importação até cada conversão ser desfeita à mão (ADR 0014):** descartado pelo
  dono; o desfazer avisa e desfaz junto.
- **Um endpoint próprio para editar a convertida:** descartado. O `PUT` de sempre já recebe a descrição e
  a observação; repetir os campos travados deixa a recusa explícita.
- **Trocar o `CASCADE` do vínculo por `RESTRICT`:** descartado. Exigiria migration de `transferencias` e
  mudaria a exclusão da transferência manual; soltar os lados antes resolve.

## Consequências

- A conta com um lado convertido tem uma "transferência" no uso: não se exclui, só se arquiva. Depois de
  desfeita, o lado volta a contar como lançamento.
- O desfazer da importação passa a mexer em lançamentos de outras importações e de lançamentos manuais
  (o outro lado), sempre dentro da trava de contas.
- Os relatórios da Fatia 06 recebem a convertida como transferência: fora de receitas e despesas.
- O ADR 0014 continua valendo, com o desfazer da importação ajustado por este ADR.