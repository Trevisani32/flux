# ADR 0013 — Lançamentos e transferências

- **Status:** Aceito
- **Data:** 2026-11-09

## Contexto

A Fatia 03b traz o módulo `lancamentos`: receitas e despesas manuais, transferências entre
contas do usuário e a lista do mês. Três pontos pediam decisão:

- **O formato da transferência.** O [ADR 0004](0004-transferencia-par-lancamentos.md) previa uma
  tabela `transferencias` com data, valor, contas e descrição, além dos dois lançamentos. Na
  Fatia 04, um lançamento importado vai poder ser ligado a outro, da outra conta, para virar
  transferência. Os dois vêm de extratos diferentes: uma TED pode sair num dia e cair no
  seguinte, e cada banco escreve a descrição do seu jeito. Data e valor de importado não mudam
  ([produto.md](../produto.md), módulo 6). Com data, valor e descrição repetidos na tabela da
  transferência, a conversão ficaria presa à mesma data e a dados em dois lugares.
- **Integridade e corrida.** O [ADR 0012](0012-categorias-padrao-e-interfaces-para-lancamentos.md)
  definiu que quem grava um uso de conta ou de categoria pega antes a trava do usuário e usa FK
  `RESTRICT`. Faltava dizer como `lancamentos` chega a essa trava sem conhecer a chave dela.
- **A lista do mês.** Filtros por conta, categoria, tipo e texto, totais do filtro e, com uma
  conta, o saldo do fim de cada dia. O volume é pessoal.

## Decisão

### Transferência como vínculo

- `transferencias (id, usuario_id, criada_em)` é só o vínculo. Data, valor, conta, descrição e
  observação ficam em cada um dos dois lançamentos, com `tipo = TRANSFERENCIA` e
  `transferencia_id`. Isso substitui a tabela descrita no ADR 0004; o resto dele continua
  valendo (dois lançamentos, saída negativa e entrada positiva, mudança sempre no par e na
  mesma transação, saldo com todos os tipos, relatórios só com receitas e despesas).
- O banco garante o par: `CHECK ((tipo = 'TRANSFERENCIA') = (transferencia_id IS NOT NULL))` e o
  índice único `(transferencia_id, (valor > 0))`, que deixa no máximo um lado de cada sinal.
- Uma transferência manual grava os dois lados com a mesma data, o mesmo valor e a mesma
  descrição. A Fatia 04 pode ligar dois lançamentos importados com a data e a descrição de cada
  banco sem mudar o esquema.
- Na API, a transferência é um item só, com as duas contas: na lista do mês e nas respostas de
  `/api/transferencias`, o `valor` é o valor movido, **positivo**, e a tela o mostra sem sinal.
  Com o filtro de uma das contas, o item diz a `direcao` (`SAIDA` ou `ENTRADA`). O sinal de cada
  lado continua nos dois lançamentos, saída negativa e entrada positiva, e é deles que o saldo
  soma. É uma exceção ao [ADR 0002](0002-representacao-dinheiro.md), em que a resposta traz o
  valor com sinal: um item com dois lados não tem um sinal só.

### Integridade no banco

- Toda FK de `lancamentos` leva `usuario_id`: `(conta_id, usuario_id)` → `contas` e
  `(transferencia_id, usuario_id)` → `transferencias`. O banco recusa referência a dado de outro
  usuário, além do service ([ADR 0007](0007-multiusuario-isolamento.md)).
- A FK da categoria é `(categoria_id, usuario_id, tipo)` → `categorias (id, usuario_id,
  natureza)`: o banco também recusa uma despesa com categoria de receita. A transferência não
  tem categoria, e com `categoria_id` nulo a FK não é conferida.
- O uso de conta e de categoria é `ON DELETE RESTRICT` (ADR 0012); `usuario_id` e o vínculo da
  transferência são `CASCADE`. Excluir o usuário apaga tudo dele ([ADR 0009](0009-contas-de-usuario-publicas.md)).
- O sinal acompanha o tipo (`CHECK`): receita positiva, despesa negativa
  ([ADR 0002](0002-representacao-dinheiro.md)). A Fatia 05 afrouxa `conta_id` (lançamento em
  fatura) e o sinal (estorno), numa migration própria.
- `origem` aceita `MANUAL` e `IMPORTADO` desde já.

### Travas

- `contas` e `categorias` expõem `ContasConsulta.travar` e `CategoriasConsulta.travar`
  (`Propagation.MANDATORY`). A chave da trava continua dentro do módulo dono.
- Toda escrita de lançamento pega a trava de contas e depois a de categorias; a de
  transferência, só a de contas. Só depois disso o serviço confere a conta (do usuário e
  ativa), a data desde o saldo inicial e a categoria. Assim, excluir ou arquivar uma conta,
  mudar a data do saldo inicial ou excluir uma categoria não corre com a gravação de um
  lançamento.
- O uso de uma conta é dito em duas partes, lançamentos e transferências, por duas
  implementações de `UsoDaConta`; o `ContaService` junta as frases e manda o resultado no campo
  `uso` do `422`.

### Lista do mês

- A lista lê os lançamentos do mês do usuário (índice `(usuario_id, data)`) e faz filtros,
  totais e agrupamento em memória. O saldo do fim do dia soma o saldo inicial, o que veio antes
  do mês (índice `(usuario_id, conta_id, data)`) e o movimento do mês da conta.
- Cada usuário tem no máximo 1.000 lançamentos por mês e 100.000 no total; por isso a lista
  vem inteira, sem paginação.
- A busca compara a descrição normalizada (`descricao_chave`, a mesma regra dos nomes de conta
  e categoria) sem índice: o mês é pequeno.

## Alternativas consideradas

- **ADR 0004 ao pé da letra:** data, valor, contas e descrição também em `transferencias`.
  Descartado: os dados ficam em dois lugares, e a conversão da Fatia 04 só funcionaria com a
  mesma data nos dois extratos.
- **Vínculo entre os próprios lançamentos (`par_id`), sem tabela:** descartado. Ligar e
  desligar exigiria atualizar as duas linhas, e não haveria um id do vínculo com `usuario_id`
  para a FK.
- **Filtros e totais em SQL dinâmico, com paginação:** descartado. É mais código e mais casos
  de teste para um volume que cabe na memória, com o limite por mês.
- **`lancamentos` chamando `pg_advisory_xact_lock` com a chave copiada:** descartado. A chave
  vazaria para fora do módulo dono, e uma mudança nela quebraria a trava em silêncio.

## Consequências

- O saldo continua sendo `SUM(valor)` por conta, com transferências
  ([ADR 0003](0003-saldo-derivado.md)).
- Editar ou excluir um lado sozinho continua proibido: a API só expõe a transferência inteira
  (`/api/transferencias`), e `/api/lancamentos/{id}` não alcança um lado dela.
- A Fatia 04 pode converter importados em transferência criando o vínculo e ligando os dois
  lados, sem migration de `transferencias`.
- Lançamentos de conta arquivada ficam só leitura: o histórico das contas arquivadas não muda
  sem reativá-las.
- Se a lista ficar grande demais para a memória, o limite por mês é o que muda primeiro, junto
  com a paginação, num ADR novo.
