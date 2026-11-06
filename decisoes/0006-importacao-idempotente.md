# ADR 0006 — Importação idempotente de extratos

- **Status:** Proposto. Confirmar no planejamento da Fatia 04.
- **Data:** 2026-11-06

## Contexto

A importação de extratos é o que torna o uso diário viável. No uso real:

- o mesmo arquivo é enviado duas vezes por engano;
- extratos de períodos sobrepostos são importados (dia 1 a 15 e depois dia 10 a 31);
- dois lançamentos legítimos idênticos acontecem no mesmo dia (dois cafés de
  R$ 6,00);
- alguns bancos geram o identificador de transação do OFX (`FITID`) de forma
  instável entre exportações, ou nem o enviam.

Importar duas vezes não pode duplicar nada, e dois lançamentos legítimos iguais
não podem virar um só.

## Decisão

- Cada lançamento importado guarda `importacao_id` e `chave_importacao`, com índice
  único `(usuario_id, chave_importacao)` onde a chave não é nula.
- **Chave do OFX:** `OFX:{contaId}:{FITID}`.
- **Chave do CSV, ou do OFX sem FITID:**
  `CSV:{contaId}:{sha256(data | valor | descrição normalizada | ordinal)}`.
  - **Ordinal** é a posição da linha entre as linhas idênticas (mesma data, valor e
    descrição) dentro do mesmo arquivo: 1, 2, 3…
  - Os dois cafés recebem ordinais 1 e 2 e viram dois lançamentos.
  - Num arquivo com período sobreposto, os mesmos dois cafés recebem de novo os
    ordinais 1 e 2 e são reconhecidos como já importados.
- **Normalização da descrição:** maiúsculas, sem acentos, espaços repetidos
  colapsados, sem espaços nas pontas.
- **Dois níveis de duplicidade na pré-visualização:**
  - **duplicado certo:** a chave já existe. A linha é descartada;
  - **provável duplicado:** chave diferente, mas existe um lançamento com a mesma
    conta, data, valor e descrição normalizada. A linha vem desmarcada e o usuário
    decide. Isso cobre os bancos com `FITID` instável.
- **Excluir um importado** marca `ignorado = true` e mantém a chave, para que ele
  não volte numa reimportação.
- **Desfazer a importação** apaga os lançamentos de verdade e libera as chaves.
- O arquivo **não é armazenado**. Só o `sha256` do arquivo inteiro é guardado, para
  avisar "este arquivo já foi importado em dd/mm".
- A leitura fica atrás de uma interface (`LeitorExtrato`, com uma implementação
  para OFX e outra para CSV). Uma fonte futura, como um agregador de Open Finance,
  é só mais uma implementação.

## Alternativas consideradas

- **Dedupe só por data + valor:** descartado, porque junta os dois cafés.
- **Dedupe por data + valor + descrição, sem ordinal:** descartado pelo mesmo
  motivo.
- **Guardar o arquivo original:** descartado por privacidade. O repositório é
  público e a demo é aberta, então quanto menos dado sensível guardado, melhor.

## Consequências

- Reimportar é seguro, e o usuário pode importar "o mês inteiro de novo" sem medo.
- Limitação conhecida: um arquivo que corta um dia no meio (só um dos dois cafés)
  pode gerar ordinais diferentes num arquivo posterior. Esse caso aparece como
  "provável duplicado", e o usuário decide.
- A pré-visualização precisa ser recalculada na confirmação, ou ficar guardada
  temporariamente no servidor. Isso será decidido no plano da Fatia 04.
