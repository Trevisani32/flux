# ADR 0014 — Importação de extratos

- **Status:** Aceito, com os ajustes do [ADR 0016](0016-conversao-de-importados-em-transferencia.md)
- **Data:** 2026-11-10

## Contexto

A Fatia 04 foi dividida em duas: a 04a traz a importação de extratos OFX e CSV, e a 04b, as regras
de categorização e a conversão de importados em transferência. O [ADR 0006](0006-importacao-idempotente.md)
propôs a chave de idempotência, os dois níveis de duplicidade, o importado "ignorado" e o desfazer,
e deixou abertos o formato dos bancos e se a pré-visualização fica no servidor. Ao planejar a 04a,
quatro pontos do 0006 precisaram de ajuste:

- **`FITID` instável.** Há indícios de que alguns bancos (o Itaú, entre eles) montam o `FITID` com
  a data e a sequência do dia. Quando o banco encaixa uma linha nova no meio do dia, os números
  andam: confiar só na chave faria uma linha nova parecer "já importada" (e sumir) e uma linha
  importada parecer nova (e duplicar).
- **Lançamento digitado à mão.** O "provável duplicado" só com a mesma descrição não reconhece o
  "Uber" que o usuário lançou antes de importar o "UBER *TRIP" do extrato.
- **Excluído e categoria.** Um importado ignorado que mantivesse a categoria seguraria a exclusão
  dela pela FK `RESTRICT` da V4, sem aparecer em lugar nenhum.
- **Identificador do CSV.** O CSV da conta do Nubank traz um identificador por movimentação, que
  serve como o `FITID`.

## Decisão

### Leitura

- O arquivo é reconhecido pelo conteúdo, não pela extensão: OFX 1.x (SGML) e 2.x (XML) por um
  tokenizador próprio e tolerante (sem parser de XML, e portanto sem XXE), e CSV no padrão RFC 4180,
  com o perfil da conta. Os dois ficam atrás da `LeitorExtrato` do ADR 0006.
- Charset: o BOM decide; sem BOM, UTF-8 estrito e, se não for UTF-8 válido, windows-1252. O
  `CHARSET` do cabeçalho do OFX é ignorado.
- Data do OFX: os 8 primeiros dígitos do `DTPOSTED`, sem converter fuso. Valor: ponto ou vírgula
  decimal; mais de 2 casas torna a linha ilegível.
- Descrição: `MEMO`, senão `NAME`, senão "Sem descrição"; espaços colapsados como nos nomes;
  cortada em 255. A descrição original fica em `lancamentos.descricao_banco`, para o provável
  duplicado e para as regras da 04b.
- No CSV, o valor segue o separador decimal do perfil, com ou sem separador de milhar: com vírgula
  decimal, "-6.00" é ilegível (nunca -600,00); com ponto decimal, "-6,00" também.
- Valor acima de 9.999.999.999.999,99 (o teto do lançamento manual e do `NUMERIC(19,2)`) torna a
  linha ilegível. Quem decide é o valor, e não a quantidade de dígitos: um identificador numérico
  escolhido por engano como coluna de valor só fica ilegível acima desse valor (com zeros à esquerda,
  pode ter mais dígitos e ainda ser lido como valor), e a primeira linha lida da amostra mostra isso.
- No CSV, o número da linha é o do registro: o cabeçalho conta, linhas em branco não contam e um
  campo entre aspas com quebra de linha é um registro só. Por isso ele pode não bater com a linha
  que um editor de texto mostra.
- Um arquivo tem no máximo 5 MB e 3.000 movimentações. Cartão de crédito e extrato de mais de uma
  conta são recusados.

### Chave e classificação

- **Chave pelo identificador do banco** (o `FITID` do OFX ou a coluna de identificador do perfil de
  CSV), quando ele existe e não se repete no arquivo: `ID:{contaId}:{identificador}`, o mesmo
  prefixo nos dois formatos. Sem ele, a chave do ADR 0006, com ordinal:
  `CON:{contaId}:{sha256(data | valor | descrição | ordinal)}`. A descrição é normalizada como os
  nomes de conta e categoria (`TextoComparavel.chave`: sem acento, minúsculas, espaços colapsados).
- **Salvaguarda:** a linha só é "já importada" quando a chave do identificador existe **e** o
  lançamento dela tem a mesma data e o mesmo valor. Se não tem, o identificador não vale para a
  linha, e ela usa a chave do conteúdo.
- **Ordem da classificação:** fica de fora (ilegível, valor zero, linha de saldo, data além de
  hoje + 12 meses) → já importada (inclusive a excluída) → antes do saldo inicial → provável
  duplicada → nova.
- **Provável duplicada:** mesma conta, data e valor de um lançamento existente (inclusive excluído),
  com a mesma descrição (a do banco ou a atual) ou de qualquer descrição, se ele foi lançado à mão.
  Cada lançamento existente casa com uma linha só. A linha vem desmarcada, e a futura também.

### Revisão e confirmação

- A revisão não guarda nada no servidor. Na confirmação, o navegador manda o arquivo de novo, com as
  linhas escolhidas e o hash do arquivo revisado. Com o mesmo hash, a API pega a trava de contas,
  lê, classifica outra vez e só então grava, pela API pública de `lancamentos`
  (`LancamentosImportados`), que pega a trava de categorias e confere os limites (tudo ou nada) e a
  categoria de cada linha. Uma linha que outra importação gravou no meio do caminho fica de fora.
- Fica um registro por importação confirmada (`importacoes`): conta, nome e hash do arquivo, um
  hash do extrato e os 4 últimos dígitos do `ACCTID` (nunca o número inteiro), o período e as
  quantidades. O hash do arquivo avisa "já importado"; o do extrato avisa quando ele já foi
  importado em outra conta ativa (a arquivada não recebe importação).
- **O hash do extrato é um HMAC-SHA256**, não um SHA-256 puro: a mensagem é o id do usuário, o
  `BANKID` e o `ACCTID`, e a chave é derivada do segredo do servidor (`JWT_SEGREDO`, que já é
  obrigatório e tem ao menos 32 bytes) por outro HMAC com o rótulo `flux:importacao:extrato`. O
  espaço de números de conta é pequeno, e os 4 últimos dígitos ficam ao lado: um SHA-256 puro seria
  revertido por força bruta por quem lesse o banco. Com o HMAC, sem o segredo, não. Com o usuário na
  mensagem, o mesmo extrato dá hashes diferentes para usuários diferentes.

### Ignorar, restaurar e desfazer

- Excluir um importado marca `ignorado_em` e **limpa a categoria**; a chave continua. O ignorado
  fica fora da lista, do saldo, dos totais, do lançamento mais antigo da conta e do uso dela, e
  conta nos limites. O banco garante (`CHECK`) que só o importado é ignorado e que ele não tem
  categoria.
- Restaurar devolve o ignorado à lista, com a categoria pedida se ela ainda servir. Qualquer outra
  (arquivada, excluída, de outra natureza, inexistente ou de outro usuário) é ignorada em silêncio, e
  o lançamento volta sem categoria: o "Desfazer" do aviso curto não pode falhar porque a categoria
  saiu no meio do caminho. É a única exceção à regra do `422` para id que não serve.
- Desfazer apaga todos os lançamentos da importação, inclusive editados e ignorados, e marca
  `desfeita_em`; o registro fica no histórico e sai do aviso de arquivo repetido.
- O registro e o perfil de CSV não são uso da conta: saem com ela (`ON DELETE CASCADE`). Os
  ignorados também saem, pela interface `LimpezaDaConta`, que `contas` declara e `lancamentos`
  implementa; a exclusão da conta a chama depois de conferir o uso, dentro da trava.

### Banco (V5)

- `lancamentos` ganha `importacao_id`, `chave_importacao`, `descricao_banco` e `ignorado_em`, com o
  índice único parcial `(usuario_id, chave_importacao)` do ADR 0006 e a FK
  `(importacao_id, conta_id, usuario_id)` → `importacoes` `RESTRICT`: o importado não muda de conta,
  e a importação não some com lançamentos.

## Alternativas consideradas

- **Guardar a pré-visualização no servidor:** descartado. Seria dado do extrato guardado fora da
  confirmação, com limpeza e expiração para cuidar; reenviar até 5 MB é barato.
- **Confiar só na chave do `FITID`:** descartado pela salvaguarda acima.
- **Hash do extrato sem segredo, ou com uma variável de ambiente nova:** o primeiro seria revertido
  por força bruta; o segundo só acrescentaria configuração, porque a chave derivada do `JWT_SEGREDO`
  com um rótulo próprio já separa os usos. O custo aceito: trocar o `JWT_SEGREDO` desliga o aviso de
  "extrato de outra conta" para as importações anteriores à troca (o aviso de arquivo repetido, pelo
  hash do arquivo, continua).
- **Prefixos `OFX:` e `CSV:`:** descartado. O mesmo identificador nos dois formatos evita duplicar
  quando o usuário alterna entre o OFX e o CSV do mesmo banco.
- **Manter a categoria do ignorado:** descartado: a categoria não poderia mais ser excluída.
- **Biblioteca de OFX:** descartada. O formato que importa aqui é pequeno, e as bibliotecas
  conhecidas estão sem manutenção ativa e trazem dependências.

## Consequências

- Reimportar é seguro mesmo com `FITID` de sequência do dia: o pior caso vira "provável
  duplicada", que o usuário decide.
- A 04b aplica as regras sobre `descricao_banco` e pode ligar dois importados num vínculo de
  `transferencias` sem migration. Uma importação com importado convertido em transferência pede que
  a conversão seja desfeita antes do desfazer.
- Os formatos reais do Nubank e do Itaú não têm documentação pública: a leitura aceita as variações
  conhecidas, e o teste com os arquivos do dono, fora do repositório, confirma o resto.
