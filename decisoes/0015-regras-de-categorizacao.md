# ADR 0015 — Regras de categorização

- **Status:** Aceito
- **Data:** 2026-11-11

## Contexto

A Fatia 04 ficou em três partes: a 04a trouxe a importação de extratos
([ADR 0014](0014-importacao-de-extratos.md)), a 04b traz as regras de categorização e a 04c vai
trazer a conversão de importados em transferência. O `produto.md` (módulo 7) já dizia o essencial:
condição sobre a descrição, a primeira regra que casar vale, as regras agem na revisão da importação e
sob demanda, e nunca sobrescrevem uma categoria escolhida. Faltava decidir:

- **Onde as regras moram.** Elas leem lançamentos (para aplicar e para a prévia), são usadas pela
  importação e referenciam categorias e contas, que têm as travas e as interfaces de uso do
  [ADR 0012](0012-categorias-padrao-e-interfaces-para-lancamentos.md).
- **A comparação.** Os bancos escrevem a mesma coisa com pontuação diferente ("UBER *TRIP" no
  Nubank, "UBER* TRIP" e "PAG*UBERTRIP 8812" no Itaú).
- **A ordem.** "Vale a primeira" pede uma posição única por usuário que se possa trocar.
- **O tipo da regra.** O `produto.md` previa um filtro de tipo (receita ou despesa), mas a categoria
  já tem natureza, e o banco já recusa lançamento com categoria de outra natureza.
- **O que acontece com a regra** quando a categoria ou a conta dela é arquivada ou excluída.
- **Como a regra não sobrescreve** a escolha do usuário, sem guardar de onde veio cada categoria.

## Decisão

### Módulo e dependências

- Módulo `categorizacao`, com a tabela `regras_categorizacao` (V6). Depende de `compartilhado`,
  `contas`, `categorias` e `lancamentos`; a importação passa a depender dele. `lancamentos` não
  conhece as regras.
- API pública de `categorizacao`: `RegrasDeCategorizacao.sugerir`, usada pela revisão da importação.
- API pública nova de `lancamentos`: `LancamentosParaRegras`, com as receitas e despesas para casar e
  `categorizar`, que grava a categoria (e a descrição legível) dentro das travas e só nos lançamentos
  que ainda estão sem categoria.
- `categorizacao` implementa `UsoDaCategoria` ("2 regras") e `LimpezaDaConta` (as regras só de uma
  conta saem antes dela, e as outras são renumeradas).

### A regra

- Condição `CONTEM`, `COMECA_COM` ou `IGUAL_A`, texto de até 255 caracteres, conta opcional,
  categoria obrigatória e descrição legível opcional.
- **Comparação:** pontuação e símbolos viram espaço e, depois, vale a chave dos nomes
  (`TextoComparavel.chave`: sem maiúsculas, acentos e espaços repetidos). O texto precisa de ao menos
  2 letras ou números depois disso. A chave da regra fica gravada (`texto_chave`).
- **Alvo:** a descrição do extrato (`descricao_banco`) no importado, que não muda quando o usuário
  edita a descrição; a descrição no manual.
- **Natureza:** a da categoria. A FK `(categoria_id, usuario_id, natureza)` →
  `categorias (id, usuario_id, natureza)` garante que as duas andam juntas; a regra de despesa só
  casa com valor negativo, e a de receita, com positivo. Não há campo "tipo" à parte.
- **Ordem:** `posicao` com `UNIQUE (usuario_id, posicao) DEFERRABLE INITIALLY DEFERRED`: as posições
  se trocam em vários `UPDATE`s e só são conferidas no fim da transação. A regra nova entra em 1º;
  reordenar recebe a lista inteira de ids e recusa uma lista que não seja exatamente a do usuário.
- A mesma condição (natureza, condição, texto e conta, com "todas as contas" contando como uma) não
  se repete: índice único com `NULLS NOT DISTINCT`, mais a conferência no serviço.
- Até 200 regras por usuário.

### Integridade

- A regra é uso da categoria: FK `RESTRICT`, como os lançamentos. Excluir uma categoria com regras dá
  o `422` de uso, e a tela oferece arquivar.
- O filtro de conta não é uso dela: a FK é `CASCADE`, de reserva, e a `LimpezaDaConta` tira as regras
  da conta antes, para renumerar as outras.
- Categoria ou conta arquivada deixa a regra **pausada**: ela continua na lista, não casa e volta a
  valer com a reativação.
- Criar, editar, excluir, reordenar e aplicar pegam a trava de contas e depois a de categorias
  (ADR 0012).
- Id que não serve (inexistente, excluído ou de outro usuário) segue o ADR 0007: `404` na URL e `422`
  no corpo. Vale também para a prévia do diálogo (categoria e conta), para "Aplicar" (a regra) e para
  as sugestões (categoria e conta). Na prévia, a categoria e a conta arquivadas valem, porque a regra
  que se edita pode ter uma; nas sugestões, a categoria arquivada só fica sem sugestão.
- A prévia de "Aplicar" de uma regra só (`GET /api/regras/aplicacao?regra=`) também dá `422` com a
  regra que não serve. É decisão desta fatia: o ADR 0007 não trata parâmetro de consulta. A
  `?regra=` não filtra uma lista; ela identifica a regra que se vai aplicar, como o corpo do "Aplicar",
  e por isso segue a regra do corpo. Os filtros da lista de Lançamentos (`?conta=`, `?categoria=`)
  continuam devolvendo a lista vazia.

### Onde as regras agem

- **Na revisão da importação:** cada linha para importar recebe a sugestão da primeira regra ativa
  que casa. A confirmação grava a categoria e a descrição que a revisão mostrou, sem rodar as regras
  de novo; a descrição do extrato continua indo para `descricao_banco`.
- **Em "Aplicar regras":** receitas e despesas sem categoria, manuais e importadas, de contas ativas e
  de qualquer data, cada uma com a primeira regra que casa. A prévia não grava; a aplicação refaz a
  conta dentro das travas. Desmarcar uma regra deixa os lançamentos dela sem categoria.
- **"Escolhida pelo usuário" é qualquer categoria:** as regras só preenchem quem está sem categoria. A
  descrição legível só troca a do importado que ainda está igual à do extrato. Nada novo é guardado
  no lançamento.
- O casamento é feito em memória: a chave da regra já está gravada, e a do lançamento é calculada uma
  vez por pedido. No limite (200 regras e 100.000 lançamentos), a conta leva frações de segundo. A
  gravação do "Aplicar" vai para o banco em lotes de 1.000, e cada lote sai do contexto de
  persistência antes do próximo.

### Sugestão de regra

- O texto sugerido sai da API: a descrição do extrato até o primeiro trecho com número ou "•" (o CPF
  mascarado), sem separador sobrando; com menos de 3 letras, a descrição inteira.
- Não há sugestão para categoria arquivada, quando uma regra ativa já casa com a descrição ou quando
  já existe a mesma regra. No resultado da importação, no máximo 5.

## Alternativas consideradas

- **Regras dentro de `categorias`:** descartado. `categorias` não depende de `lancamentos`, e aplicar
  regras precisa ler e gravar lançamentos (ADR 0012).
- **Comparação só com a chave dos nomes:** descartada. "UBER *TRIP" e "UBER* TRIP" seriam textos
  diferentes, e cada banco pediria uma regra.
- **Casamento em SQL (`LIKE`):** descartado. Reproduzir no banco a normalização do Java (acentos,
  pontuação) seria frágil, e a lista cabe na memória.
- **Campo "tipo" separado da categoria:** descartado. Repete a categoria e permite combinação que o
  banco recusa.
- **Guardar a regra em cada lançamento (`regra_id`):** descartado. Serviria para mostrar "pela regra"
  e desfazer, mas acrescenta uma FK entre módulos e uma limpeza a mais, sem pedido do produto.
- **Arquivar a categoria exclui as regras dela:** descartado. Reativar a categoria traria a categoria
  de volta sem as regras.
- **Regra da conta excluída vira "todas as contas":** descartado. A regra passaria a pegar outras
  contas sem aviso.

## Consequências

- O `ModularidadeTest` confere a direção nova: `categorizacao` → `contas`, `categorias`,
  `lancamentos`; `importacao` → `categorizacao`; ninguém depende da importação.
- Aplicar uma regra a um importado conta como edição no histórico da importação (`atualizado_em`
  muda), como uma edição à mão.
- Mudar a comparação (a chave dos nomes ou a pontuação) exige uma migration que recalcule
  `texto_chave`.
- A Fatia 05 acrescenta o cartão como filtro, numa migration própria.

