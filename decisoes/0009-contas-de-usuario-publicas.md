# ADR 0009 — Contas de usuário públicas

- **Status:** Aceito. Os detalhes de implementação (biblioteca do limite de
  tentativas, captcha e provedor de e-mail) ficam para o plano da Fatia 02.
- **Data:** 2026-11-06

## Contexto

O plano inicial tinha um único usuário real, criado por variável de ambiente, sem
cadastro público nem recuperação de senha por e-mail.

O Flux também é portfólio e precisa se comportar como um produto usado por
outras pessoas. Isso traz três coisas:

- **requisitos de produto:** cadastro, recuperação de senha e perfil;
- **obrigações legais:** a LGPD garante o direito de excluir a conta e de levar
  os dados (portabilidade);
- **um risco novo:** endpoints públicos que criam conta e enviam e-mail são alvo
  de abuso e de enumeração de e-mails.

## Decisão

### Cadastro controlado por configuração

- O cadastro existe no produto e é ligado pela variável `APP_CADASTRO_PUBLICO`,
  que vem desligada por padrão. Desligado, a tela some e o endpoint responde `404`.
- O usuário inicial criado por variável de ambiente continua existindo. É ele que
  dá acesso a uma instância com o cadastro desligado.

### E-mail confirmado antes do primeiro login

- A tabela `usuarios` ganha `email_confirmado_em`. O usuário inicial já nasce
  confirmado.
- Login com e-mail ainda não confirmado: `403` com "Confirme seu e-mail para
  entrar.". Isso só acontece **depois de a senha conferir**. Quem não sabe a senha
  continua recebendo "E-mail ou senha inválidos".

### Tokens de e-mail de uso único

Um único mecanismo serve a três finalidades:

| Finalidade | Validade |
|---|---|
| Confirmação de e-mail | 24 horas |
| Redefinição de senha | 30 minutos |
| Troca de e-mail | 24 horas |

- O link leva um valor aleatório de 256 bits. O banco guarda só o hash SHA-256,
  junto com a finalidade, a expiração e a data de uso. É o mesmo desenho do
  refresh token ([ADR 0008](0008-autenticacao.md)).
- Pedir um link novo ou usar um link invalida os outros links pendentes do mesmo
  usuário e da mesma finalidade.

### Respostas que não revelam quais e-mails existem

- "Esqueci a senha" e "reenviar confirmação" respondem sempre igual (`202`).
- Cadastro com e-mail já em uso: a resposta é a mesma do sucesso, e o dono do
  e-mail recebe um aviso de tentativa de cadastro.
- No login continua valendo a regra do ADR 0008: mesma mensagem e mesmo tempo de
  resposta.

### Credenciais trocadas encerram sessões

- Redefinir a senha revoga todos os refresh tokens do usuário, como já faz a troca
  de senha.
- Trocar o e-mail: o novo e-mail só passa a valer depois de confirmado, e o antigo
  recebe um aviso.

### Exclusão de conta definitiva

- A exclusão pede a senha atual.
- Ela apaga o usuário e, em cascata, todos os dados dele. **Toda FK para
  `usuarios` usa `ON DELETE CASCADE`**, e essa regra vale para todas as migrations
  futuras. Não há exclusão lógica.
- O e-mail de confirmação da exclusão é montado antes de apagar e enviado depois.

### Portabilidade

Na Fatia 06 entra a exportação, em JSON, de todos os dados do usuário.

### Limite de tentativas

- **Onde vale:** entrar, cadastrar, reenviar confirmação, "esqueci a senha" e
  redefinir senha, contando por IP e por e-mail.
- **Resposta ao estourar:** `429` com `ProblemDetail` e o cabeçalho `Retry-After`.
- **Quando entra:** sai da Fatia 08 e vem para a Fatia 02, porque o cadastro
  público é a primeira porta aberta do sistema. Isso substitui a previsão do
  ADR 0008.
- **IP atrás do nginx:** vem de `X-Forwarded-For`, aceito só quando a requisição
  chega pelo proxy.

### Envio de e-mail

- Uma interface própria (`EnvioDeEmail`) sobre o Spring Mail.
  - Dev e testes usam o **Mailpit**, um container que captura e mostra os
    e-mails.
  - Produção usa SMTP configurável.
- O e-mail sai **depois do commit**, por um evento de domínio com listener
  assíncrono. É o primeiro listener assíncrono do sistema, então entra o registro
  persistente de eventos do Modulith ([ADR 0001](0001-monolito-modular.md)). Assim
  nenhum e-mail se perde se a aplicação cair entre o commit e o envio.

### Limpeza

Um job diário apaga:

- cadastros não confirmados há mais de 7 dias;
- tokens de e-mail expirados;
- refresh tokens expirados.

### Termos

O aceite registra a versão dos textos e a data (`termos_versao` e
`termos_aceitos_em`).

## Alternativas consideradas

- **Manter sem cadastro, como no plano original:** descartado. O portfólio pede
  um produto completo, e a demo sozinha não mostra os fluxos de conta.
- **Login com Google no lugar da senha:** fica para depois do MVP. Exige registrar
  um app no Google Cloud e não dispensa a recuperação para quem usa senha.
- **Link mágico por e-mail, sem senha:** descartado. Deixa o acesso 100%
  dependente do e-mail e foge do que se espera de um produto financeiro.
- **Exclusão lógica da conta:** descartada. A LGPD pede a eliminação, e manter
  dados "escondidos" contradiz o que a tela promete.

## Consequências

- Deixa de valer o "No MVP não há cadastro público" do `produto.md` original.
- Toda tabela de negócio nova precisa de `ON DELETE CASCADE` para `usuarios`.
  Cada módulo ganha um teste de exclusão de conta, como o teste de isolamento do
  [ADR 0007](0007-multiusuario-isolamento.md).
- O limite de tentativas passa a ser pré-requisito do cadastro, e não mais do
  deploy.
- O ambiente de dev ganha o Mailpit no `docker-compose.dev.yml`.
- Mais superfície fica exposta. Ela é contida pela confirmação de e-mail, pelo
  limite de tentativas e pelas respostas uniformes. O captcha fica como ponto em
  aberto da Fatia 02.
