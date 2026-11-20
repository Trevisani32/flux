# ADR 0018 — Pagamento da fatura e limite disponível

- **Status:** Aceito. Complementa o [ADR 0017](0017-cartao-de-credito-faturas-e-parcelas.md) no que ele deixou para a
  Fatia 05b.
- **Data:** 2026-11-20

## Contexto

O [ADR 0017](0017-cartao-de-credito-faturas-e-parcelas.md) definiu o cartão, as faturas, as compras e os estornos
(Fatia 05a) e deixou para a 05b o pagamento: o tipo `PAGAMENTO_FATURA`, a coluna `fatura_paga_id` no lançamento da
conta, `faturas.paga_fora_em`, a situação paga, a trava da fatura paga, "Converter em pagamento de fatura" e o limite
disponível. As decisões 30 a 38 da Fatia 05 foram aprovadas pelo dono em 2026-11-12.

Ao implementar, apareceram pontos que o ADR 0017 não fechava:

- **O que é "paga".** A decisão 34 diz "depois do fechamento, pendente ≤ 0". Lida ao pé da letra, toda fatura fechada
  vazia, ou só com um estorno, ficaria "Paga" sem nenhum pagamento, e o estorno lançado num mês passado ficaria travado
  na hora (decisão 35).
- **Qual fatura recebe pagamento.** A decisão 30 permite pagar antes de fechar; a fatura futura (o período ainda não
  começou) não tem o que pagar.
- **O pagamento que veio do extrato.** A decisão 37 fala de converter e desfazer, mas não de excluir, editar ou desfazer
  a importação dele.
- **O que mais o pagamento toca.** A lista do mês, os filtros, as regras, a conversão em transferência, os usos da conta
  e do cartão e a exclusão.

## Decisão

### Modelo (V9)

- O pagamento é um lançamento da conta, do tipo `PAGAMENTO_FATURA`, negativo, com `fatura_paga_id` apontando para a
  fatura que paga (FK `(fatura_paga_id, usuario_id) → faturas (id, usuario_id)`, `RESTRICT`). Os `CHECK`s de tipo e de
  sinal ganham o tipo; `ck_lancamentos_pagamento` amarra o tipo à coluna; `ck_lancamentos_pagamento_nao_ignorado` impede
  o pagamento excluído (ignorado), como o lado de transferência da V7.
- Não tem categoria: a FK pela natureza (`fk_lancamentos_categoria`) recusa, porque nenhuma categoria tem essa natureza.
  Não tem cartão nem fatura própria: o `ck_lancamentos_conta_ou_fatura` e o `ck_lancamentos_tipo_na_fatura` da V8 já
  recusam o pagamento dentro de uma fatura.
- `faturas.paga_fora_em` marca a fatura paga fora do Flux (decisão 36).
- Um índice parcial em `lancamentos (fatura_paga_id)` serve à FK e às somas por fatura.

### Situação, pendente e limite

- **Pago** = a soma dos pagamentos da fatura, positiva. **Pendente** = total − pago, nunca negativo; zero na fatura
  marcada como paga fora do Flux. **Pago a mais** = o que passou do total (o total negativo, um crédito, conta como zero).
- **Paga** é a fatura fechada pelas datas com pagamento que cobre o total (pago maior que zero e maior ou igual ao
  total) ou marcada como paga fora do Flux. A fechada sem pagamento, vazia ou só com crédito, continua "Fechada": não
  houve pagamento, e o pendente dela já é zero. A aberta com pagamento antecipado continua "Aberta".
- O calendário do cartão (`CalendarioDoCartao`) só dá futura, aberta e fechada, pelas datas; a situação paga é decidida
  em `lancamentos`, que guarda os pagamentos. O enum ganha `PAGA`.
- **Limite disponível** = limite − a soma dos pendentes de todas as faturas do cartão, inclusive as futuras (decisão
  38). Pode ficar negativo ("Acima do limite") e não bloqueia nada. `MovimentoDosCartoes` passa o pendente ao módulo de
  cartões, e a resposta do cartão leva o pendente, o disponível e a parte do limite tomada (de 0 a 100, para a barra),
  calculada num lugar só (`MovimentoDosCartoes.usoDoLimite`), que a fatura também usa.

### Pagar, editar, excluir

- Recebem pagamento a fatura aberta e as que já fecharam; a futura, não (`422 FATURA_FUTURA`). A conta é do usuário,
  ativa, corrente, poupança ou carteira; a data vai do saldo inicial da conta até hoje; o valor pode ser parcial ou
  passar do pendente (decisões 30 e 31). O pagamento conta 1 nos limites de lançamentos.
- Com a conta ou o cartão arquivado, o pagamento fica só para leitura, e o cartão arquivado não recebe pagamento novo
  (decisões 5 e 33).
- O lançado à mão muda conta, data, valor e observação, e se exclui. O convertido do extrato muda só a observação, não
  se exclui e se desfaz (decisão 37).

### Trava da fatura paga (decisão 35)

- A compra ou o estorno com alguma parcela numa fatura paga só muda descrição, categoria e observação, e não se exclui
  (`422 FATURA_PAGA`, "A parcela 1/3 está na fatura de novembro, que já foi paga: …").
- A compra nova numa fatura paga entra (decidido pelo dono em 2026-11-20). Quando o total novo passa do pago, a fatura
  volta a ter pendente. Quando o pago ainda cobre o total novo, ou a fatura está marcada como paga fora do Flux (a marca
  vale para a fatura inteira, e o pendente continua zero), ela continua paga, e a compra nova já entra travada; o estorno
  novo também. A prévia da compra (`POST /api/compras/previa`, com o `tipo` COMPRA ou ESTORNO) avisa antes de lançar
  (`avisoDaFaturaPaga`), e o engano se corrige desmarcando a fatura ou excluindo o pagamento. O aviso vale sempre que o
  lançamento fica numa fatura paga: a que já estava paga e continua, e a que passa a ser paga com ele (o estorno que
  cobre o pendente; a edição que diminui o valor, com o `compraId` na prévia, que desconta as parcelas atuais).
- A edição que regrava as parcelas de uma compra ou de um estorno livre (a data, o cartão, o valor, as parcelas ou a
  primeira parcela) é recusada quando poria uma parcela numa fatura paga ou marcada como paga fora do Flux (`422
  FATURA_PAGA`, "A edição levaria a compra para a fatura de agosto, que já foi paga. …"), como o ajuste (decidido pelo
  dono em 2026-11-20). Para lançar nela de propósito, exclui-se e lança-se de novo.
- A fatura paga não se ajusta, nem volta ao fechamento normal. O ajuste de outra fatura que moveria uma compra de ou
  para uma fatura paga é recusado.
- A troca de dias não precisa da trava: ela recalcula só as faturas seguintes à aberta, e só a fechada pode estar paga.
- O cartão com pagamento, ou com uma fatura marcada como paga fora do Flux, não é vazio na troca de dias (ADR 0017): a
  exceção "os dias novos valem já" não vale, e as faturas dele não são apagadas.

### Converter em pagamento de fatura (decisão 37)

- Só a despesa importada, não excluída, fora de transferência, numa conta ativa corrente, poupança ou carteira, com data
  até hoje, vira pagamento de uma fatura que já fechou ou está aberta. Ela perde a categoria; data, valor, conta,
  descrição, descrição do extrato e chave ficam, e reimportar o extrato continua dando "já importada".
- As faturas oferecidas são as dos dois meses antes ao mês depois da data da despesa, em cada cartão ativo; vêm primeiro
  as sugeridas (fechadas, com o pendente igual ao valor e o vencimento a até 5 dias da data).
- "Desfazer" volta a despesa, sem categoria, com a descrição que tinha.
- O pagamento não vira transferência nem serve de par na conversão em transferência ([ADR
  0016](0016-conversao-de-importados-em-transferencia.md)).
- Desfazer a importação apaga também o pagamento convertido de uma despesa dela, e a fatura volta a ter o pendente. Com o
  cartão dele arquivado, o desfazer inteiro é recusado, como o da transferência convertida com a outra conta arquivada.
  Como no ADR 0016, o histórico (`GET /api/importacoes`) diz, em cada importação, os pagamentos convertidos dela, com a
  data, o cartão (e se está arquivado) e o mês da fatura: a confirmação avisa que o pagamento sai e a fatura volta a ter
  o pendente, e o cartão arquivado bloqueia antes da confirmação.

### Lançamentos, regras e usos

- Na lista do mês, o pagamento é uma linha da conta, com o "−" em cor neutra, fora das entradas e saídas e de qualquer
  filtro de categoria ("Sem categoria" incluído); entra no saldo do dia com o filtro da conta. O filtro de tipo ganha
  "Pagamentos de fatura" (`?tipo=pagamento`). Com o filtro de um cartão, o pagamento não aparece: a lista é a fatura.
- O pagamento abre pelo diálogo dele; `/api/lancamentos/{id}` responde `404` para ele, e `POST /api/lancamentos` recusa o
  tipo.
- As regras de categorização não olham o pagamento: não é receita nem despesa.
- O pagamento é uso da conta (conta como lançamento) e do cartão ("tem 1 compra e 2 pagamentos").

### Travas

O pagamento (lançar, editar, excluir, converter e desfazer) pega a trava de contas e depois a de cartões (decisão 49).
A marca de paga fora do Flux pega a de cartões, como o ajuste. Desfazer a importação, que já pega a de contas, pega a de
cartões quando apaga um pagamento.

### API (05b)

- `POST /api/cartoes/{id}/faturas/{mes}/pagamentos`; `GET`, `PUT` e `DELETE /api/pagamentos/{id}`;
  `POST /api/pagamentos/{id}/desfazer`.
- `PUT` e `DELETE /api/cartoes/{id}/faturas/{mes}/paga-fora`.
- `GET /api/lancamentos/{id}/faturas-para-pagamento` e `POST /api/lancamentos/{id}/converter-em-pagamento`.
- A fatura, o cartão, a lista do mês e a prévia da compra ganham os campos do pagamento e do limite; a prévia, também o
  `tipo`, o `compraId` da edição e o aviso da fatura paga; o histórico da importação, os pagamentos convertidos.

## Alternativas consideradas

- **"Paga" com o pendente zero, mesmo sem pagamento** (a decisão 34 ao pé da letra): descartada. A fatura fechada vazia,
  ou só com um estorno, ficaria "Paga", e o estorno lançado num mês passado ficaria travado na hora.
- **Pagar a fatura futura:** descartado. Ela ainda não tem período; o pagamento antecipado é o da aberta.
- **Excluir o pagamento convertido:** descartado, como na transferência convertida (ADR 0016). Ele se desfaz e volta a ser
  a despesa do extrato, que se exclui como qualquer importado.
- **Recusar o desfazer da importação que tem um pagamento convertido:** descartado. O pagamento é um lançamento dela, e o
  desfazer apaga todos os lançamentos da importação; a fatura volta a ter o pendente, como na exclusão de um pagamento.
- **Avisar na edição e deixar a compra entrar travada:** descartada pelo dono em 2026-11-20. A compra editada ficaria
  presa na fatura paga, sem voltar nem se excluir; a recusa deixa o caminho explícito (excluir e lançar de novo).
- **A compra nova numa fatura paga fora do Flux voltar a ter pendente:** descartada pelo dono em 2026-11-20. A marca diz
  que a fatura foi paga por fora, inteira; a compra esquecida que entra nela também foi.
- **A tabela `pagamentos_fatura`:** descartada no ADR 0017.

## Consequências

- O lançamento de conta passa a ter três famílias: receita e despesa, lado de transferência e pagamento de fatura. Toda
  consulta nova sobre lançamentos decide se o pagamento entra.
- A situação paga, o pendente e o limite são calculados a cada leitura, a partir das faturas, dos totais e dos
  pagamentos: nada disso é gravado, além da marca de paga fora do Flux.
- A Fatia 06 recebe o pagamento fora de receitas e despesas, como a transferência, e a Fatia 07 pode projetar as faturas
  pelo pendente.
