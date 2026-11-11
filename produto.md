# produto.md — Flux

> Este arquivo guarda as regras de negócio e os módulos. As
> decisões de arquitetura ficam em [`decisoes/`](decisoes/).

## Visão

Sistema web de finanças pessoais com dois objetivos de mesmo peso:

1. **Uso real.** Controlar contas, cartões e orçamento no dia a dia, digitando o
   mínimo possível. Para isso existem a importação de extratos e a categorização
   automática.
2. **Portfólio público.** O código fica aberto no GitHub e há uma demonstração
   navegável com dados fictícios.

O que isso exige do produto:

- Nenhum dado financeiro real entra no repositório: extratos, prints e seeds são
  sempre fictícios.
- O sistema é multiusuário e isola os dados de cada usuário desde a primeira
  versão ([ADR 0007](decisoes/0007-multiusuario-isolamento.md)).
- Ele é tratado como **um produto para qualquer pessoa**: cadastro, confirmação de
  e-mail, recuperação de senha, termos de uso e os direitos da LGPD (excluir a
  conta e exportar os dados). A instância do dono é só mais um usuário
  ([ADR 0009](decisoes/0009-contas-de-usuario-publicas.md)).
- Toda tela precisa funcionar bem com os dados fictícios da demonstração.

## Usuários

| Perfil | Como entra | O que vê |
|---|---|---|
| Usuário cadastrado | Cria a conta pelo cadastro público e confirma o e-mail | Só os próprios dados |
| Usuário inicial | É criado na subida da aplicação a partir de variáveis de ambiente, já com o e-mail confirmado. É assim que o dono entra numa instância com o cadastro desligado | Só os próprios dados |
| Visitante da demo | Botão "Entrar como demo", que só existe na instância de demonstração | Dados fictícios, restaurados todo dia |

- O cadastro público é controlado pela variável `APP_CADASTRO_PUBLICO`. Na
  demonstração fica ligado; na instância do dono, ele decide.
- Não há papéis (admin, colaborador): todo usuário é igual e só enxerga o que é dele.

## Regras gerais

- **Moeda:** só real (BRL). Valores têm 2 casas decimais e são tratados como
  decimal exato, nunca ponto flutuante ([ADR 0002](decisoes/0002-representacao-dinheiro.md)).
- **Datas:** lançamentos têm data sem hora. "Hoje" é calculado no fuso
  `America/Sao_Paulo`.
- **Formatação:** a interface usa pt-BR (R$ 1.234,56 e 06/11/2026).
  - Nos lançamentos, entradas aparecem com "+" e saídas com "−", e a cor só
    reforça o sinal. Transferências entre contas próprias aparecem sem sinal
    e em cor neutra.
  - Saldos aparecem sem "+" e, quando negativos, com "−" (em vermelho).
  - Totais de despesa em relatórios seguem o
    [ADR 0002](decisoes/0002-representacao-dinheiro.md): aparecem sem sinal
    ("gastou R$ 50", não "−R$ 50").
  - Um valor ausente aparece como "—".
- **Isolamento:** um usuário nunca lê nem altera dado de outro. Pedir um recurso de
  outro usuário dá a mesma resposta que pedir um que não existe.
- **Exclusão:** registros com histórico são arquivados, não apagados. A regra de
  cada módulo está abaixo. A exceção é a exclusão da conta de usuário, que apaga
  tudo de forma definitiva (LGPD).

## Glossário

| Termo | Significado |
|---|---|
| Conta | Lugar onde o dinheiro fica: conta corrente, poupança, carteira ou investimento |
| Lançamento | Uma movimentação com data, descrição e valor, numa conta ou numa fatura |
| Categoria | Classificação do lançamento (Mercado, Salário…), com até dois níveis |
| Transferência | Dinheiro que passa de uma conta sua para outra. Não é receita nem despesa |
| Cartão | Cartão de crédito com limite, dia de fechamento e dia de vencimento |
| Fatura | O conjunto de lançamentos de um cartão que vencem num mesmo mês |
| Parcelamento | Compra no cartão dividida em N parcelas, uma por fatura |
| Importação | Leitura de um extrato OFX ou CSV que vira lançamentos |
| Regra de categorização | "Se a descrição contém X, use a categoria Y" |
| Recorrência | Lançamento que se repete (salário, aluguel, assinatura) |
| Previsto / efetivado | Previsto é algo que ainda vai acontecer. Efetivado já aconteceu e conta no saldo |
| Orçamento | Limite mensal de gasto por categoria |
| Projeção | Saldo esperado nos próximos dias, somando o que está previsto |

## Módulos

### 1. Acesso e conta de usuário — Fatias 00 e 02

**Entregue na Fatia 00:**

- Login com e-mail e senha. A sessão usa um token de acesso curto, renovado
  automaticamente ([ADR 0008](decisoes/0008-autenticacao.md)).
- Sair encerra a sessão no servidor, não só no navegador.
- O usuário inicial é criado na subida da aplicação quando as variáveis
  `USUARIO_INICIAL_*` estão definidas e o e-mail ainda não existe. O e-mail
  precisa ser aceito pelos mesmos critérios do login, e a senha precisa seguir a
  regra de senha. Se não, a aplicação não sobe e informa o motivo, em vez de criar
  um usuário que não consegue entrar.
- O usuário pode trocar a própria senha, informando a atual. A troca encerra
  todas as sessões abertas.
- A nova senha precisa ter de 10 a 72 caracteres. O máximo vem do BCrypt, que só
  considera 72 bytes: com acentos, o limite efetivo é um pouco menor, e o sistema
  avisa quando a senha é longa demais.
- O e-mail é guardado em minúsculas e sem espaços nas pontas.
- Errar o e-mail ou errar a senha dá a mesma mensagem: "E-mail ou senha inválidos".

   **Fatia 02** ([ADR 0009](decisoes/0009-contas-de-usuario-publicas.md) e
   [ADR 0011](decisoes/0011-limite-de-tentativas-e-envio-de-email.md)):

   - **Cadastro:**
     - campos: nome (até 120 caracteres), e-mail e senha (mesma regra da troca de
       senha), mais o aceite dos termos de uso e da política de privacidade. O
       aceite fica registrado com a versão dos textos e a data;
     - só existe quando `APP_CADASTRO_PUBLICO=true`. Desligado, a tela volta para o
       Entrar, o Entrar não mostra "Criar conta" e o endpoint responde `404`.
   - **Confirmação de e-mail:**
     - o cadastro envia um link de confirmação, que vale 24 horas e pode ser usado
       uma vez. Pedir um link novo invalida o anterior;
     - o link confirma e mostra o botão "Entrar": ele não faz login;
     - sem confirmar, o login responde "Confirme seu e-mail para entrar.", mas só
       quando a senha está certa, e oferece reenviar o link;
     - cadastros não confirmados em 7 dias são apagados.
   - **Cadastro com e-mail que já existe:** a tela mostra a mesma resposta de sucesso
     ("Enviamos um link para o seu e-mail").
     - Se a conta já confirmou o e-mail, o dono recebe um aviso de que alguém tentou
       criar uma conta com ele. Assim o cadastro não revela quais e-mails estão em uso.
     - Se a conta ainda não confirmou, vai um link de confirmação novo, como no
       reenvio. O cadastro existente continua como está (nome, senha e aceite do
       primeiro pedido).
   - **Esqueci a senha:**
     - a pessoa informa o e-mail e recebe um link de redefinição, que vale 30 minutos
       e pode ser usado uma vez;
     - a resposta da tela é sempre a mesma, exista ou não o e-mail;
     - redefinir a senha encerra todas as sessões, invalida outros links pendentes e
       cancela a troca de e-mail pendente. Como o link chegou naquele endereço,
       redefinir também confirma o e-mail.
   - **Perfil (Minha conta):**
     - editar o nome;
     - trocar o e-mail: pede a senha atual; o novo só passa a valer depois de
       confirmado pelo link enviado a ele (24 horas, uso único), e o e-mail atual
       recebe um aviso já no pedido. Se o e-mail novo já for de outra conta, a tela
       responde igual, mas nenhum link é enviado e o dono desse endereço não é
       avisado: a troca não revela quais e-mails têm conta;
     - trocar a senha (já existe desde a Fatia 00).
   - **Excluir minha conta:** pede a senha atual e apaga o usuário e todos os dados
     dele, de forma definitiva. O e-mail recebe a confirmação da exclusão.
   - **Termos de uso e política de privacidade:** páginas públicas, em linguagem
     simples, na versão `2026-11-06`. Mudar o texto é mudar a versão. O nome e o
     e-mail do responsável pela instância vêm de `APP_CONTROLADOR_NOME` e
     `APP_CONTROLADOR_EMAIL`; com o cadastro ligado, os dois são obrigatórios.
   - **Limite de tentativas:**

     | Ação | Por e-mail | Por IP |
     |---|---|---|
     | Entrar (só as tentativas erradas contam) | 10 em 15 min | 50 em 15 min |
     | Criar conta | 3 por hora | 10 por hora |
     | Reenviar confirmação | 3 por hora | 10 por hora |
     | Esqueci a senha | 3 por hora | 10 por hora |
     | Redefinir senha | — | 10 em 15 min |
     | Conferir a senha atual (trocar senha, trocar e-mail, excluir conta) | 10 em 15 min, por usuário | — |
     | Pedir a troca de e-mail (além da senha atual) | 3 por hora por endereço novo e 3 por hora por conta | — |

     - quando o limite estoura, a resposta é "Muitas tentativas. Tente de novo em
       alguns minutos.";
     - os contadores ficam só na memória do servidor;
     - não há desafio anti-robô (captcha) por enquanto.
   - **E-mails:** em português, com o nome do produto e um link. Nunca levam senha
     nem dado financeiro. Saem depois que a operação é gravada; se o envio falhar,
     são reenviados. Tratam a pessoa pelo primeiro nome só quando ele tem apenas
     letras, apóstrofos e hífens; senão, por "Usuário".


**Fatia 06:** **exportar meus dados**, um arquivo com todos os dados do usuário
(LGPD, direito de portabilidade).

### 2. Contas — Fatia 03a
- Tipos: `CORRENTE`, `POUPANCA`, `CARTEIRA` (dinheiro em espécie) e `INVESTIMENTO`
  (só o saldo, sem cotação). O tipo pode mudar depois de criada a conta.
- Campos: nome (até 60 caracteres), tipo, saldo inicial, data do saldo inicial e cor
  (opcional, só visual).
- **Nome:** único entre as contas ativas do usuário, sem diferenciar maiúsculas,
  acentos e espaços nas pontas ("Poupança" e "poupanca " são o mesmo nome). Espaços
  repetidos no meio viram um só: "Conta   conjunta" é gravada como "Conta conjunta".
- **Saldo inicial:** é o saldo no começo do dia escolhido; os lançamentos desse dia
  entram no saldo. Pode ser negativo (cheque especial). Padrão: R$ 0,00 na data de
  hoje. A data não pode ser futura.
- O campo de valor aceita "1.234,56", "1234,56" e "-120", com até 2 casas decimais e
  até 13 dígitos antes da vírgula.
- **Saldo atual** = saldo inicial + soma dos lançamentos efetivados da conta com
  data até hoje ([ADR 0003](decisoes/0003-saldo-derivado.md)). Um lançamento com
  data futura só entra no saldo quando a data chegar.
- Um lançamento com data anterior à data do saldo inicial é recusado. O saldo inicial
  e a data podem ser editados, mas a data não pode passar do lançamento mais antigo da
  conta.
- O saldo pode ficar negativo (cheque especial). A interface mostra em vermelho, com "−".
- **Saldo total:** soma das contas ativas, inclusive as de investimento. Ao lado
  aparecem "Disponível" (corrente, poupança e carteira) e "Investimentos".
- **Cor:** de uma paleta fixa de 8 (Ouro, Laranja, Rosa, Roxo, Índigo, Azul, Ciano e
  Cinza), sem verde e vermelho, que indicam entrada e saída.
- **Arquivar:** a conta some dos seletores e do saldo total, mas o histórico continua
  nos relatórios. Se o saldo não for zero, o sistema avisa antes de arquivar; com saldo
  zero, arquiva direto e oferece "Desfazer". As regras de categorização só desta conta ficam
  pausadas até ela ser reativada.
- **Reativar:** a conta arquivada volta pela seção "Arquivadas". Se já houver uma conta
  ativa com o mesmo nome, a reativação é recusada. Conta arquivada não se edita.
- **Excluir:** só é permitido em conta sem lançamentos e sem transferências. Com eles, o
  sistema diz quantos são e oferece arquivar. As regras de categorização só desta conta saem
  junto, e as outras regras sobem de posição.
- As contas aparecem em ordem alfabética. Cada usuário pode ter até 50 contas,
  contando as arquivadas.
- Cartão de crédito não é conta. Ele tem módulo próprio.
### 3. Categorias — Fatia 03a
- Cada categoria tem natureza `RECEITA` ou `DESPESA`, que nunca muda.
- São dois níveis: categoria principal e subcategoria. A subcategoria herda a natureza
  da principal. Uma subcategoria pode mudar de principal (na mesma natureza) ou virar
  principal; uma principal sem subcategorias pode virar subcategoria.
- Todo usuário novo recebe categorias padrão, gravadas junto com o usuário:
  - **Despesa:** Moradia (Aluguel, Condomínio, Contas da casa), Alimentação
    (Mercado, Restaurante, Delivery), Transporte (Combustível, App de transporte,
    Transporte público), Saúde, Educação, Lazer, Assinaturas, Compras,
    Impostos e taxas, Outras despesas.
  - **Receita:** Salário, Freelance, Rendimentos, Reembolsos, Outras receitas.
- **Nome:** até 60 caracteres, único entre as irmãs ativas (mesma natureza e mesma
  principal, ou entre as principais), com a mesma comparação das contas. Renomear é
  livre.
- Um lançamento pode ficar **sem categoria**, por exemplo um importado que nenhuma
  regra reconheceu. Nos relatórios, "Sem categoria" aparece como item próprio.
- Uma categoria principal com subcategorias também recebe lançamentos. Nos relatórios,
  o que foi lançado direto nela aparece como item próprio.
- **Arquivar:** categoria em uso pode ser arquivada. Arquivar a principal arquiva junto
  as subcategorias ativas; reativar a principal traz de volta as que foram arquivadas
  com ela. A subcategoria de uma principal arquivada não se reativa sozinha. As regras de
  categorização de uma categoria arquivada ficam pausadas até ela ser reativada.
- **Excluir:** só se a categoria não tiver lançamentos, regras nem orçamentos. Excluir a
  principal exclui as subcategorias junto. Com lançamentos ou regras, o sistema diz quantos
  são e oferece arquivar.
- As categorias não têm ícone nem cor. Aparecem em ordem alfabética, e cada usuário
  pode ter até 200, contando as arquivadas.
### 4. Lançamentos — Fatia 03b

- Receita ou despesa numa conta. Na Fatia 05 passam a existir também lançamentos
  em fatura de cartão.
- Campos: data, descrição (até 255 caracteres), valor, tipo (`RECEITA` ou `DESPESA`),
  categoria (opcional), conta e observação (opcional, até 1.000 caracteres).
- O usuário sempre digita o valor positivo. O sistema guarda com sinal: entrada
  positiva, saída negativa ([ADR 0002](decisoes/0002-representacao-dinheiro.md)).
- Valor zero não é permitido. O valor tem até 2 casas decimais e até 13 dígitos antes
  da vírgula.
- A categoria precisa ter a mesma natureza do lançamento. Uma despesa com
  categoria de receita é recusada. A categoria principal também recebe lançamentos.
- **Data:** a partir da data do saldo inicial da conta e até 12 meses depois de hoje.
  Um lançamento com data futura aparece na lista como "Futuro" e só entra no saldo
  quando a data chegar.
- **Conta arquivada:** não recebe lançamento novo, e os lançamentos dela ficam só para
  leitura até a conta ser reativada.
- **Categoria arquivada:** não pode ser escolhida, mas o lançamento que já a tem
  continua com ela e pode ser editado.
- Ao editar, despesa e receita trocam entre si; um lançamento não vira transferência,
  nem o contrário.
- A origem é `MANUAL` ou `IMPORTADO` (Fatia 04a).
- **Lista:** um mês por vez, começando no mês atual e indo até 12 meses à frente,
  agrupada por dia, com o mais recente primeiro (no mesmo dia, o último cadastrado em
  cima). Há filtros por conta, categoria (a principal inclui as subcategorias, e há
  "Sem categoria"), tipo (receitas, despesas ou transferências) e texto da descrição,
  sem diferenciar maiúsculas e acentos. O mês e os filtros ficam no endereço da página.
- **Totais do filtro:** entradas, saídas e resultado, só de receitas e despesas. Os
  lançamentos futuros do mês entram.
- Com o filtro de uma conta, cada dia mostra o saldo da conta no fim do dia.
- Cada usuário pode ter até 1.000 lançamentos com data no mesmo mês e 100.000 no total;
  a transferência conta dois.
- Lançamentos manuais podem ser editados e excluídos, com confirmação, menos os de conta
  arquivada. As regras dos importados estão no módulo de importação.

### 5. Transferências — Fatia 03b

- Movem dinheiro entre duas contas do usuário e não são receita nem despesa
  ([ADR 0004](decisoes/0004-transferencia-par-lancamentos.md) e
  [ADR 0013](decisoes/0013-lancamentos-e-transferencias.md)).
- Cada transferência gera dois lançamentos ligados, com a mesma data e o mesmo valor:
  saída na origem e entrada no destino.
- Não têm categoria. A descrição é opcional: vazia, fica "Transferência".
- A data precisa ser a partir da data do saldo inicial das duas contas.
- Editar ou excluir uma transferência altera os dois lançamentos juntos, e a edição
  pode trocar as contas. Com uma das contas arquivada, a transferência fica só para
  leitura.
- A conta de origem precisa ser diferente da de destino.
- Na lista, cada transferência é uma linha, sem sinal e em cor neutra. Ficam fora dos
  totais de receita e despesa e dos gastos por categoria, mas entram no saldo das contas.

### 6. Importação de extratos — Fatia 04a

Ver [ADR 0006](decisoes/0006-importacao-idempotente.md) e [ADR 0014](decisoes/0014-importacao-de-extratos.md).

- **Formatos:** OFX (versões 1.x em SGML e 2.x em XML) e CSV com perfil, reconhecidos pelo
  conteúdo, não pela extensão. O arquivo pode ter no máximo 5 MB e 3.000 movimentações.
  - Charset: o BOM decide; sem ele, UTF-8 e, se não for UTF-8 válido, windows-1252.
  - Data do OFX: o dia do `DTPOSTED`, sem converter fuso. O valor aceita ponto ou vírgula decimal.
  - Descrição: o `MEMO`, senão o `NAME`, senão "Sem descrição", cortada em 255 caracteres. A
    descrição original do banco fica guardada.
  - Fatura de cartão (OFX de cartão ou o CSV da fatura do Nubank) e extrato de mais de uma conta
    são recusados. A importação de fatura entra na Fatia 05.
- **Conta:** escolhida à mão, antes do arquivo; vem preenchida pelo atalho da conta ou pela última
  usada na sessão. Se o mesmo extrato OFX já foi importado em outra conta ativa, a revisão avisa e
  oferece trocar. Do OFX fica só um hash protegido por um segredo do servidor e os 4 últimos dígitos da conta,
  nunca o número.
- **Perfil de CSV**, salvo por conta, criado na primeira importação de CSV dela e editável na
  revisão. Informa:
  - o separador de colunas (vírgula, ponto e vírgula ou tabulação);
  - quantas linhas de cabeçalho pular;
  - a coluna de data e o formato da data (DD/MM/AAAA, DD/MM/AA ou AAAA-MM-DD);
  - a coluna de descrição e a de valor;
  - o separador decimal;
  - se as saídas vêm negativas ou numa coluna separada;
  - a coluna do identificador do banco, opcional.

  O modelo pronto "Nubank (conta)" (`Data,Valor,Identificador,Descrição`) é sugerido quando o
  cabeçalho do arquivo bate. Nas importações seguintes da conta, o perfil aparece resumido, com
  "Editar perfil", antes da revisão.
- O fluxo tem duas etapas: **envio → revisão → confirmação**. Nada é gravado antes da confirmação,
  e nada da revisão fica guardado: na confirmação, o arquivo é enviado de novo e classificado outra
  vez.
- A revisão classifica cada linha, nesta ordem:
  - **fica de fora:** linha ilegível, valor zero, linha de saldo ("SALDO ANTERIOR") ou data além de
    12 meses depois de hoje;
  - **já importada:** a chave já existe (inclusive num lançamento excluído). É descartada;
  - **anterior ao saldo inicial da conta:** é descartada;
  - **provável duplicada:** mesma data e valor de um lançamento existente, com a mesma descrição, ou
    de qualquer descrição se ele foi lançado à mão. Vem desmarcada e diz com qual se parece;
  - **nova.** Vem marcada, menos a de data futura (um agendamento do banco pode não acontecer).

  Cada linha para importar já vem com a categoria e a descrição legível da primeira regra de
  categorização que casar (módulo 7). Sem regra, a categoria é escolhida à mão, com a opção de usar
  a mesma nas outras linhas de mesma descrição; a escolha à mão vale sobre a da regra. O usuário pode
  desmarcar linhas antes de confirmar. A revisão conta as marcadas, sem somar valores.
- Os limites de lançamentos (1.000 por mês e 100.000 no total) valem na confirmação, para todas as
  linhas marcadas: ou entram todas, ou nenhuma.
- Reimportar o mesmo arquivo, ou um arquivo com período sobreposto, não duplica lançamentos. Um
  identificador do banco que mudou (o mesmo número com outra data ou outro valor) não é confiável:
  a linha vira nova ou provável duplicada, e nunca some.
- O arquivo enviado não é guardado. Fica só um registro da importação: data, conta, nome do
  arquivo, hash, período e quantidades. Se o mesmo arquivo for enviado de novo, a revisão avisa
  quando e em que conta ele foi importado, e deixa seguir.
- O histórico das importações fica na tela de Importação, a mais recente primeiro.
- Num lançamento importado o usuário pode editar categoria, descrição e observação. Tipo, data,
  valor e conta não mudam, porque vêm do banco.
- Excluir um lançamento importado só o marca como **ignorado**, e ele perde a categoria. Assim ele
  não volta numa reimportação. O ignorado fica fora da lista, do saldo e dos totais, não conta
  como uso da conta e conta nos limites. Ele pode ser restaurado: pelo "Desfazer" logo depois de
  excluir (volta com a categoria) ou na lista de excluídos da importação (volta sem categoria).
- **Desfazer importação** apaga todos os lançamentos daquela importação, inclusive os editados e os
  ignorados, depois de uma confirmação que diz quantos são. Com a conta arquivada, é recusado. O
  registro fica no histórico como desfeito.
- O registro da importação e o perfil de CSV não impedem excluir a conta: saem junto com ela, como
  os importados ignorados.
- **Transferência entre contas próprias** aparece nos dois extratos. Na Fatia 04c, um lançamento
  importado poderá ser convertido em transferência, ligando-o a um lançamento da outra conta com o
  mesmo valor e sinal oposto. Até lá, ela entra como despesa numa conta e receita na outra.
- A importação de fatura de cartão em CSV entra na Fatia 05.

### 7. Regras de categorização — Fatia 04b

Ver [ADR 0015](decisoes/0015-regras-de-categorizacao.md).

- **Condição:** **contém**, **começa com** ou **igual a**, aplicada à descrição do extrato no
  lançamento importado e à descrição no lançamento manual. A comparação ignora maiúsculas, acentos,
  espaços repetidos e pontuação: "UBER *TRIP" e "UBER* TRIP" são o mesmo texto, e "pag uber" casa com
  "PAG*UBERTRIP 8812". O texto tem até 255 caracteres e ao menos 2 letras ou números.
- **Categoria:** obrigatória e ativa. A natureza dela define o tipo: uma regra com categoria de
  despesa só pega despesas, e uma de receita, só receitas.
- **Conta:** todas as contas ou uma conta ativa. O cartão entra na Fatia 05.
- **Descrição legível**, opcional: troca a descrição do lançamento importado ("PAG*UBERTRIP 8812" →
  "Uber"); a do extrato continua guardada. No lançamento manual, a regra só põe a categoria.
- **Ordem:** a lista é numerada, e vale a primeira regra que casar. A regra nova entra em 1º lugar;
  "Subir" e "Descer" mudam a posição.
- Cada usuário pode ter até 200 regras. A mesma condição (mesmo texto, condição, natureza e conta)
  não se repete.
- O diálogo da regra mostra, enquanto ela é escrita, com quantos lançamentos ela casa, quantos deles
  estão sem categoria e até 3 exemplos.
- **Onde as regras agem:**
  - na revisão da importação, cada linha para importar vem com a categoria e a descrição da primeira
    regra que casar, marcada "pela 2ª regra". Trocar a categoria no seletor vale e não volta, e a
    confirmação grava o que a revisão mostrou;
  - em "Aplicar regras": na tela de Regras, no aviso que aparece depois de criar uma regra e em
    Lançamentos com o filtro "Sem categoria". Valem as receitas e despesas sem categoria, manuais e
    importadas, de contas ativas e de qualquer data. Uma prévia agrupada por regra diz quantos
    lançamentos cada uma pega; desmarcar uma regra deixa os lançamentos dela sem categoria.
- Uma regra nunca troca a categoria que um lançamento já tem, posta à mão ou por outra regra. A
  descrição legível só troca a do importado que ainda está igual à do extrato.
- **Sugestão de regra:** no resultado da importação, as descrições que ganharam categoria à mão na
  revisão (até 5) aparecem com "Criar regra"; ao salvar um importado que estava sem categoria e
  ganhou uma, o aviso oferece "Criar regra". O texto vem preenchido com a descrição do extrato até o
  primeiro trecho com número ("PAG*UBERTRIP 8812" → "PAG*UBERTRIP"), e tudo pode ser editado antes de
  salvar. Não há sugestão quando uma regra ativa já casa com a descrição.
- **Categoria e conta da regra:** a regra é uso da categoria; com regras, a categoria não se exclui,
  só se arquiva. Uma regra com a categoria ou a conta arquivada fica pausada até a reativação.
  Excluir a conta exclui as regras só dela.
- Excluir uma regra não muda os lançamentos que ela já categorizou.

### 8. Cartões de crédito — Fatia 05

Ver [ADR 0005](decisoes/0005-cartao-fatura-parcelas.md).

- **Cartão:** nome, limite, dia de fechamento, dia de vencimento e conta de
  pagamento padrão.
- **Fatura:** existe uma por cartão por mês de referência. O mês de referência é o
  mês do vencimento ("fatura de novembro" vence em novembro).
- Quando o dia de fechamento ou de vencimento não existe no mês (31 em fevereiro),
  vale o último dia do mês.
- Uma fatura pode ter a data de fechamento ajustada só para ela, porque os bancos
  às vezes mudam o fechamento de um mês.
- **Fatura de uma compra:** se a compra foi feita até o fechamento, entra na fatura
  que fecha naquele mês. Se foi depois, entra na fatura seguinte.
- **Compra:** data, descrição, valor total, categoria e número de parcelas (1 a 48).
- **Parcelamento:**
  - a parcela 1 entra na fatura da compra, e a parcela k entra k−1 faturas depois;
  - o valor de cada parcela é o total dividido por N, truncado no centavo. A
    diferença vai para a 1ª parcela. Exemplo: R$ 100,00 em 3x dá 33,34 + 33,33 + 33,33.
- Editar ou excluir uma compra parcelada altera todas as parcelas. Se alguma
  parcela já está numa fatura paga, só é possível editar descrição e categoria.
- **Estorno ou crédito** é um lançamento positivo na fatura e abate o total.
- **Situação da fatura:**
  - aberta: o período ainda está em curso;
  - fechada: já passou o fechamento e falta pagar;
  - paga: os pagamentos cobrem o total.
- **Pagamento:** sai de uma conta e abate a fatura. Pode ser parcial, e o restante
  fica como pendente. Não há cálculo automático de juros no MVP. Pagamento de
  fatura não é despesa, porque a despesa já foi contada na compra.
- **Limite disponível** = limite − tudo o que está pendente nas faturas não
  quitadas, inclusive as futuras.
- **Relatórios:** o gasto no cartão conta no mês de referência da fatura, que é
  quando o dinheiro sai. Cada parcela conta no mês da sua fatura.

### 9. Dashboard e relatórios — Fatia 06

- Para o mês escolhido: receitas, despesas, resultado e saldo total das contas.
- Gastos por categoria no mês, com as subcategorias ao expandir, comparados com a
  média dos 3 meses anteriores.
- Receitas × despesas dos últimos 12 meses.
- Evolução do saldo total, no fim de cada mês, nos últimos 12 meses.
- Próximas faturas, com valor e vencimento.
- Clicar num número abre a lista de lançamentos já filtrada.
- Transferências e pagamentos de fatura ficam fora de receitas e despesas.

### 10. Recorrências — Fatia 07

- Campos: descrição, valor, tipo, categoria, conta ou cartão, frequência (mensal no
  dia D, semanal ou anual), data de início e data de fim (opcional).
- As ocorrências futuras aparecem como **previstas**. Elas não mexem no saldo
  atual, só na projeção.
- **Um previsto vira efetivado** quando:
  - o usuário confirma, podendo ajustar valor e data; ou
  - uma importação traz um lançamento compatível (mesma conta, mesmo tipo, valor
    próximo, data até 3 dias de diferença). Nesse caso o sistema sugere a ligação
    na pré-visualização e o usuário confirma.
- Uma ocorrência pode ser pulada só naquele mês.
- Para valores variáveis, como a conta de luz, o previsto é uma estimativa. A
  efetivação usa o valor real.

### 11. Orçamento — Fatia 07

- Limite mensal por categoria de despesa. A categoria mãe soma as subcategorias.
- Progresso = gasto efetivado no mês ÷ limite. A interface alerta em 80% e em 100%.
- Há a opção "copiar orçamento do mês anterior".
- No MVP, o que sobra num mês não passa para o seguinte.

### 12. Projeção de saldo — Fatia 07

- A projeção vai até uma data escolhida: por padrão o fim do mês seguinte, e no
  máximo 12 meses à frente.
- Saldo projetado em cada dia = saldo atual das contas + recorrências previstas −
  faturas a pagar. Cada fatura sai da conta de pagamento padrão do cartão, na data
  de vencimento.
- O sistema mostra o **menor saldo previsto** no período e o dia em que ele acontece.
- Por padrão, contas de investimento e contas arquivadas ficam fora da projeção.

### 13. Demonstração — Fatia 08

- Só existe na instância de demonstração, ligada pela variável `APP_DEMO=true`.
- O usuário demo tem 12 meses de dados fictícios criados por um seed determinístico:
  contas, cartão com parcelas, importações, regras, recorrências e orçamento.
- Os dados são restaurados todo dia e a cada subida da aplicação.
- Um aviso fixo diz "Ambiente de demonstração — dados fictícios".
- Na demo não é possível trocar a senha, trocar o e-mail nem excluir o usuário demo.

## Fora do escopo por enquanto

- Open Finance ou agregadores. A importação fica atrás de uma interface para ligar
  um deles depois.
- Investimentos com cotação, multimoeda, impostos e imposto de renda.
- Juros e rotativo do cartão.
- Finanças compartilhadas entre usuários, como as de um casal. O modelo
  multiusuário deixa essa porta aberta.
- Notificações e app nativo.
- Para depois do MVP: anexo de comprovante, metas, login em dois fatores e login
  com Google.

## Pontos em aberto

| Ponto | Decidir em |
|---|---|
| Importar fatura de cartão que já lista as parcelas ("3/10") | Fatia 05 |
| Biblioteca de gráficos | Fatia 06 |
| Recorrência: gravar as ocorrências no banco ou calcular quando precisar | Fatia 07 |
| Demo: conta compartilhada, usuário temporário por visitante ou cadastro com dados de exemplo | Fatia 08 |
| Provedor de e-mail em produção (SMTP) | Fatia 08 |
| Hospedagem e domínio | Fatia 08 |
| Desafio anti-robô (captcha) no cadastro, se aparecer abuso | Fatia 08 |
| Trechos da política de privacidade que dependem da publicação (hospedagem, envio de e-mail, cópias de segurança) | Fatia 08 |
| Servir a fonte Manrope pelo nginx, para a política deixar de citar o Google Fonts | Fatia 08 |

## Entregas

| Fatia | Conteúdo | Situação |
|---|---|---|
| 00 | Base: repositório, login, multiusuário, CI, Docker | Concluída |
| 01 | Base visual: tema escuro com dourado, componentes base, menu lateral responsivo e e2e no CI | Concluída |
| 02 | Conta de usuário: cadastro, confirmação de e-mail, esqueci a senha, perfil, exclusão da conta, termos e limite de tentativas | Concluída |
| 03a | Contas e categorias: cadastro, arquivamento, categorias padrão e telas | Concluída |
| 03b | Lançamentos manuais, transferências e saldo pelos lançamentos | Concluída |
| 04a | Importação de extratos OFX e CSV: revisão, histórico, ignorar e desfazer | Concluída |
| 04b | Regras de categorização: cadastro, sugestão na revisão da importação, "Aplicar regras" e "Criar regra" | Implementada, aguardando publicação |
| 04c | Conversão de importados em transferência | — |
| 05 | Cartão de crédito: faturas e parcelamento | — |
| 06 | Dashboard, relatórios e exportação dos dados | — |
| 07 | Recorrências, orçamento e projeção de saldo | — |
| 08 | Deploy, demo pública e README completo | — |

