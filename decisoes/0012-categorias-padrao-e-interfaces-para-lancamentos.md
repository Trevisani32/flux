# ADR 0012 — Categorias padrão por evento síncrono e interfaces para lançamentos

- **Status:** Aceito
- **Data:** 2026-11-09

## Contexto

A Fatia 03 foi dividida em duas. A 03a traz contas e categorias; a 03b traz lançamentos,
o saldo calculado a partir deles ([ADR 0003](0003-saldo-derivado.md)) e a regra de que
conta ou categoria com lançamento não pode ser excluída.

Duas coisas precisavam de decisão de arquitetura:

- **Categorias padrão.** Todo usuário novo recebe 24 categorias. O usuário nasce no
  módulo `acesso`, e as categorias são do módulo `categorias`. O
  [ADR 0001](0001-monolito-modular.md) já previa um evento de domínio (`UsuarioCriado`)
  para isso. Na Fatia 02, os e-mails passaram a sair depois do commit, pelo registro
  persistente de eventos do Spring Modulith ([ADR 0011](0011-limite-de-tentativas-e-envio-de-email.md)).
- **Direção das dependências.** O saldo de uma conta depende dos lançamentos, e o
  lançamento depende da conta (para validar a conta e a data do saldo inicial). Se o
  módulo `contas` chamasse `lancamentos` e `lancamentos` chamasse `contas`, haveria um
  ciclo, que o `ApplicationModules.verify()` recusa.

## Decisão

### Categorias padrão por evento síncrono

- O `acesso` publica `com.flux.acesso.UsuarioCriado(usuarioId)` logo depois de gravar um
  usuário novo, nos dois caminhos que fazem isso: o cadastro público e o usuário inicial.
- O `categorias` reage com um `@EventListener` comum (síncrono) marcado com
  `@Transactional(propagation = MANDATORY)`: as categorias são gravadas na mesma
  transação do usuário. Ou os dois são gravados, ou nada é. Publicar o evento fora de uma
  transação é erro.
- Esse ouvinte não entra no registro persistente de eventos: o Modulith só registra
  ouvintes que rodam depois do commit.
- Os usuários que já existiam receberam as mesmas categorias pela migration V3. Um teste
  compara a lista da V3 com a do Java.

### Interfaces declaradas por quem é usado

- `contas` declara, no pacote raiz, `MovimentoDasContas` (soma dos lançamentos efetivados
  por conta até uma data e data do lançamento mais antigo) e `UsoDaConta` (descrição do
  uso de uma conta por outro módulo).
- `categorias` declara `UsoDaCategoria`.
- `lancamentos` (Fatia 03b) implementa as três. Depois, cartões, regras, recorrências e
  orçamentos implementam as de uso, se referenciarem contas ou categorias.
- Os services de contas e categorias já consultam as interfaces por `ObjectProvider`.
  Sem implementação, o saldo atual é o saldo inicial e a exclusão é livre. Pode existir
  uma implementação só de `MovimentoDasContas`: com duas, a aplicação não sobe.
- A exclusão confere o uso e exclui dentro da trava do usuário
  (`pg_advisory_xact_lock`, chave `'contas:'` ou `'categorias:'` + id). Quem implementa
  uma interface de uso grava um uso novo pegando antes a mesma trava (com as duas, primeiro
  a de contas), e a FK do uso é `ON DELETE RESTRICT`, nunca `CASCADE`.
- Resultado: `lancamentos` → `contas`, `categorias`; `categorias` → `acesso`; `contas` não
  depende de nenhum módulo de negócio. O `ModularidadeTest` confere essa direção.

## Alternativas consideradas

- **Ouvinte depois do commit (`@ApplicationModuleListener`), com o registro de eventos:**
  descartado. Haveria um intervalo em que o usuário existe sem categorias, e o ouvinte
  precisaria aguentar repetição e usuário já excluído. O executor assíncrono também é
  dividido com os e-mails.
- **Rotina na subida que dá categorias a quem não tem:** descartada. Sem uma marca por
  usuário, devolveria as categorias a quem apagou todas.
- **Saldo e trava calculados em `lancamentos`, com a tela de contas dependendo dele:**
  descartado. Inverte a dependência natural e espalha a regra de saldo.
- **SQL dentro de `contas` lendo a tabela de lançamentos:** descartado. Fura as fronteiras
  do ADR 0001; consultas que atravessam módulos ficam em `relatorios`.

## Consequências

- Um usuário novo sempre tem as categorias padrão, inclusive o cadastro ainda não
  confirmado. Cadastros abandonados levam as categorias junto quando são apagados (7
  dias, em cascata).
- Uma falha ao gravar as categorias faz o cadastro falhar (`500`) em vez de criar um
  usuário pela metade.
- A Fatia 03b acrescenta as implementações e a API de leitura de `contas` e `categorias`
  para outros módulos (`ContasConsulta`/`CategoriasConsulta`); as consultas que a 03a já
  faz não mudam. Se a 03b quiser a FK composta `(conta_id, usuario_id)` para contas, como a
  da principal da categoria, a migration dela cria o `UNIQUE (id, usuario_id)` em `contas`.
- O saldo inicial da conta chega à API com sinal ("-120" no cheque especial): é uma
  posição, não um movimento. A regra do [ADR 0002](0002-representacao-dinheiro.md) (o
  cliente envia o valor positivo e o tipo) vale para os movimentos.
- Mudar a regra de comparação de nomes (`TextoComparavel.chave`) exige uma migration que
  recalcule a coluna `nome_chave`.
