# ADR 0021 — Exportação dos dados: formato e módulo

- **Status:** Aceito. Complementa os ADRs 0001, 0007 e 0009.
- **Data:** 2026-11-24

## Contexto

A LGPD garante a portabilidade. O ADR 0009 previu a exportação em JSON na Fatia 06, e a política de privacidade dizia
que ela chegaria numa próxima versão.

## Decisão

- A pessoa baixa um JSON completo, com `formato` `flux-exportacao`, `versao` 1, uma lista por tabela e as relações pelos
  ids, e um CSV só dos lançamentos para planilha. O CSV usa UTF-8 com BOM, `;`, vírgula decimal, `DD/MM/AAAA`, CRLF e
  apóstrofo antes de texto que a planilha interpretaria como fórmula.
- A senha atual é obrigatória e conta no mesmo limite das outras conferências da conta. Senha, sessões, links de e-mail,
  hashes e chaves internas ficam fora; o campo `foraDoArquivo` explica a exclusão.
- O catálogo enumera todas as tabelas e colunas. Um teste compara o catálogo ao banco, para que toda migration nova
  decida explicitamente se o dado vai ou fica fora.
- `exportacao` é um módulo só de leitura por SQL. Depende de `compartilhado` e apenas da interface
  `ConferenciaDaSenhaAtual` do `acesso`; nenhum módulo depende dele.
- A escrita é em fluxo no servlet direto, com cursor de `fetchSize` dentro de transação de leitura em `REPEATABLE READ`.
  Uma resposta já começada não recebe `ProblemDetail`: a conexão cai e o navegador vê o download incompleto.
- Termos e política usam a versão `2026-11-24`, sem novo aceite.

## Alternativas consideradas

- Só JSON ou um ZIP com um CSV por tabela.
- Baixar sem senha ou incluir saldos e totais calculados.
- Colocar a exportação em `relatorios` ou em `acesso`.
- `StreamingResponseBody` com o tempo-limite assíncrono, ou montar todo o arquivo na memória.

## Consequências

O arquivo cresce com os dados, e a conexão do banco fica presa apenas enquanto a API o gera; o nginx guarda a resposta
para o cliente lento. Cada download conta no limite da senha, e uma coluna nova quebra o teste até entrar no catálogo. O
CSV não é caminho de importação. A queda no meio chega ao navegador porque a API responde em `chunked` por HTTP/1.1: o
nginx precisa falar HTTP/1.1 com a API (o padrão desde o nginx 1.29.7; uma imagem mais antiga pede
`proxy_http_version 1.1`).
