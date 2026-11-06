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

**Fatia 02** ([ADR 0009](decisoes/0009-contas-de-usuario-publicas.md)):

- **Cadastro:**
  - campos: nome (até 120 caracteres), e-mail e senha (mesma regra da troca de
    senha), mais o aceite dos termos de uso e da política de privacidade. O
    aceite fica registrado com a versão dos textos e a data;
  - só existe quando `APP_CADASTRO_PUBLICO=true`. Desligado, a tela e o endpoint
    não ficam disponíveis.
- **Confirmação de e-mail:**
  - o cadastro envia um link de confirmação, que vale 24 horas e pode ser usado
    uma vez;
  - sem confirmar, o login responde "Confirme seu e-mail para entrar.", mas só
    quando a senha está certa, e oferece reenviar o link;
  - cadastros não confirmados em 7 dias são apagados.
- **Cadastro com e-mail que já existe:** a tela mostra a mesma resposta de sucesso
  ("Enviamos um link para o seu e-mail"), e o dono daquele e-mail recebe um aviso
  de que alguém tentou criar uma conta com ele. Assim o cadastro não revela quais
  e-mails estão em uso.
- **Esqueci a senha:**
  - a pessoa informa o e-mail e recebe um link de redefinição, que vale 30 minutos
    e pode ser usado uma vez;
  - a resposta da tela é sempre a mesma, exista ou não o e-mail;
  - redefinir a senha encerra todas as sessões e invalida outros links pendentes.
- **Perfil:**
  - editar o nome;
  - trocar o e-mail: o novo só passa a valer depois de confirmado pelo link
    enviado a ele, e o e-mail antigo recebe um aviso da troca;
  - trocar a senha (já existe desde a Fatia 00).
- **Excluir minha conta:** pede a senha atual e apaga o usuário e todos os dados
  dele, de forma definitiva. O e-mail recebe a confirmação da exclusão.
- **Termos de uso e política de privacidade:** páginas públicas, em linguagem
  simples.
- **Limite de tentativas:**
  - vale para login, cadastro, reenvio de confirmação e "esqueci a senha", por IP
    e por e-mail;
  - quando o limite estoura, a resposta é "Muitas tentativas. Tente de novo em
    alguns minutos.".
- **E-mails:** em português, com o nome do produto e um link. Nunca levam senha
  nem dado financeiro.

**Fatia 06:** **exportar meus dados**, um arquivo com todos os dados do usuário
(LGPD, direito de portabilidade).

### 2. Contas — Fatia 03

- Tipos: `CORRENTE`, `POUPANCA`, `CARTEIRA` (dinheiro em espécie) e `INVESTIMENTO`
  (só o saldo, sem cotação).
- Campos: nome, tipo, saldo inicial, data do saldo inicial e cor (opcional, só visual).
- O nome é único entre as contas ativas do usuário.
- **Saldo atual** = saldo inicial + soma dos lançamentos efetivados da conta com
  data até hoje ([ADR 0003](decisoes/0003-saldo-derivado.md)). Um lançamento com
  data futura só entra no saldo quando a data chegar.
- Um lançamento com data anterior à data do saldo inicial é recusado.
- O saldo pode ficar negativo (cheque especial). A interface mostra em vermelho.
- **Arquivar:** a conta some dos seletores e do saldo total, mas o histórico continua
  nos relatórios. Se o saldo não for zero, o sistema avisa antes de arquivar.
- **Excluir:** só é permitido em conta sem lançamentos.
- Cartão de crédito não é conta. Ele tem módulo próprio.

### 3. Categorias — Fatia 03

- Cada categoria tem natureza `RECEITA` ou `DESPESA`.
- São dois níveis: categoria e subcategoria. A subcategoria herda a natureza da mãe.
- Todo usuário novo recebe categorias padrão:
  - **Despesa:** Moradia (Aluguel, Condomínio, Contas da casa), Alimentação
    (Mercado, Restaurante, Delivery), Transporte (Combustível, App de transporte,
    Transporte público), Saúde, Educação, Lazer, Assinaturas, Compras,
    Impostos e taxas, Outras despesas.
  - **Receita:** Salário, Freelance, Rendimentos, Reembolsos, Outras receitas.
- Um lançamento pode ficar **sem categoria**, por exemplo um importado que nenhuma
  regra reconheceu. Nos relatórios, "Sem categoria" aparece como item próprio.
- Renomear é livre.
- Categoria em uso pode ser arquivada. Só pode ser excluída se não tiver
  lançamentos, regras nem orçamentos.

### 4. Lançamentos — Fatia 03

- Receita ou despesa numa conta. Na Fatia 05 passam a existir também lançamentos
  em fatura de cartão.
- Campos: data, descrição, valor, tipo (`RECEITA` ou `DESPESA`), categoria
  (opcional), conta e observação.
- O usuário sempre digita o valor positivo. O sistema guarda com sinal: entrada
  positiva, saída negativa ([ADR 0002](decisoes/0002-representacao-dinheiro.md)).
- Valor zero não é permitido.
- A categoria precisa ter a mesma natureza do lançamento. Uma despesa com
  categoria de receita é recusada.
- A origem é `MANUAL` ou `IMPORTADO` (Fatia 04).
- A listagem mostra um mês por vez, começando no mês atual. Há filtros por conta,
  categoria, tipo e texto da descrição, e os totais do filtro (entradas, saídas e
  resultado).
- Lançamentos manuais podem ser editados e excluídos livremente. As regras dos
  importados estão no módulo de importação.

### 5. Transferências — Fatia 03

- Movem dinheiro entre duas contas do usuário e não são receita nem despesa
  ([ADR 0004](decisoes/0004-transferencia-par-lancamentos.md)).
- Cada transferência gera dois lançamentos ligados, com a mesma data e o mesmo
  valor: saída na origem e entrada no destino.
- Editar ou excluir uma transferência altera os dois lançamentos juntos.
- A conta de origem precisa ser diferente da de destino.
- Transferências ficam fora dos totais de receita e despesa e dos gastos por categoria.

### 6. Importação de extratos — Fatia 04

- Formatos: OFX (versões 1.x em SGML e 2.x em XML) e CSV com mapeamento de colunas.
- **Perfil de CSV**, salvo por conta, informa:
  - o separador de colunas;
  - a coluna de data e o formato da data;
  - a coluna de descrição e a de valor;
  - o separador decimal;
  - se as saídas vêm negativas ou numa coluna separada;
  - quantas linhas de cabeçalho pular.
- O fluxo tem duas etapas: **envio → pré-visualização → confirmação**. Nada é
  gravado antes da confirmação.
- A pré-visualização classifica cada linha:
  - **nova**;
  - **duplicada:** já importada antes, é descartada;
  - **provável duplicada:** mesma data, valor e descrição de um lançamento já
    existente, mas sem a mesma chave. Vem desmarcada e o usuário decide;
  - **anterior ao saldo inicial da conta:** é ignorada.

  Cada linha já vem com a categoria sugerida pelas regras. O usuário pode
  desmarcar linhas e trocar categorias antes de confirmar.
- Reimportar o mesmo arquivo, ou um arquivo com período sobreposto, não duplica
  lançamentos ([ADR 0006](decisoes/0006-importacao-idempotente.md)).
- O arquivo enviado não é guardado. Fica só um registro da importação: data,
  conta, nome do arquivo, hash e quantidades. Se o mesmo arquivo for enviado de
  novo, o sistema avisa quando ele foi importado.
- Num lançamento importado o usuário pode editar categoria, descrição e
  observação. Data e valor não mudam, porque vêm do banco.
- Excluir um lançamento importado só o marca como **ignorado**. Assim ele não volta
  numa reimportação.
- **Desfazer importação** apaga todos os lançamentos daquela importação, depois de
  uma confirmação.
- **Transferência entre contas próprias** aparece nos dois extratos. Um lançamento
  importado pode ser convertido em transferência, ligando-o a um lançamento da
  outra conta com o mesmo valor e sinal oposto. Sem isso, a transferência contaria
  como receita e como despesa.
- O arquivo pode ter no máximo 5 MB.
- A importação de fatura de cartão em CSV entra na Fatia 05.

### 7. Regras de categorização — Fatia 04

- A condição é aplicada à descrição: **contém**, **começa com** ou **igual a**. A
  comparação ignora maiúsculas e acentos.
- Filtros opcionais: conta ou cartão, e tipo (receita ou despesa).
- A ação define a categoria e, opcionalmente, uma descrição mais legível
  ("PAG*UBERTRIP 1234" → "Uber").
- O usuário define a ordem de prioridade, e vale a primeira regra que casar.
- As regras são aplicadas na pré-visualização da importação e sob demanda ("aplicar
  regras aos lançamentos sem categoria").
- Uma regra nunca sobrescreve uma categoria escolhida pelo usuário.
- Quando o usuário categoriza à mão um lançamento importado sem categoria, o
  sistema sugere criar uma regra com aquela descrição.

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
| Texto dos termos de uso e da política de privacidade | Fatia 02 |
| Limite de tentativas: contado em memória ou no banco | Fatia 02 |
| Desafio anti-robô (captcha) no cadastro | Fatia 02 |
| Formatos de CSV dos bancos usados | Fatia 04 |
| Guardar a pré-visualização da importação no servidor ou reenviar o arquivo na confirmação | Fatia 04 |
| Importar fatura de cartão que já lista as parcelas ("3/10") | Fatia 05 |
| Biblioteca de gráficos | Fatia 06 |
| Recorrência: gravar as ocorrências no banco ou calcular quando precisar | Fatia 07 |
| Demo: conta compartilhada, usuário temporário por visitante ou cadastro com dados de exemplo | Fatia 08 |
| Provedor de e-mail em produção (SMTP) | Fatia 08 |
| Hospedagem e domínio | Fatia 08 |

## Entregas

| Fatia | Conteúdo | Situação |
|---|---|---|
| 00 | Base: repositório, login, multiusuário, CI, Docker | Implementada, aguardando publicação |
| 01 | Base visual: mockup aprovado, tema, componentes base e menu lateral responsivo (pendência da Fatia 00) | — |
| 02 | Conta de usuário: cadastro, confirmação de e-mail, esqueci a senha, perfil, exclusão da conta, termos e limite de tentativas | — |
| 03 | Contas, categorias, lançamentos manuais e transferências | — |
| 04 | Importação OFX/CSV e regras de categorização | — |
| 05 | Cartão de crédito: faturas e parcelamento | — |
| 06 | Dashboard, relatórios e exportação dos dados | — |
| 07 | Recorrências, orçamento e projeção de saldo | — |
| 08 | Deploy, demo pública e README completo | — |
