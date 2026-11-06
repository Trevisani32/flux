# ADR 0008 — Autenticação: JWT de acesso curto e refresh token em cookie

- **Status:** Aceito
- **Data:** 2026-11-06

## Contexto

O sistema guarda dados financeiros e ficará exposto na internet. Um JWT único, de
longa duração e guardado em `localStorage` tem dois problemas:

- qualquer XSS consegue lê-lo e usá-lo por horas;
- não há como revogá-lo, nem mesmo no logout ou na troca de senha.

## Decisão

### Token de acesso

- JWT HS256 que vale **15 minutos**, com `sub` = id do usuário.
- É emitido e validado pelo próprio Spring Security (OAuth2 Resource Server, com
  `NimbusJwtEncoder` e `NimbusJwtDecoder` e um segredo simétrico). Não há filtro
  JWT próprio.
- O segredo vem de `JWT_SEGREDO`. A aplicação não sobe com um segredo menor que
  32 bytes.
- O frontend guarda o token **só em memória** e o envia no header `Authorization`.

### Refresh token

- Valor aleatório opaco de 256 bits. O banco guarda **só o hash SHA-256**, com a
  data de expiração (**30 dias**).
- Vai num cookie `HttpOnly`, `SameSite=Strict`, `Path=/api/auth` e `Secure` em
  produção.
- **Rotação:** cada `POST /api/auth/renovar` revoga o token usado e emite um novo.
- **Reuso:** se chegar um token que já foi rotacionado, todos os tokens do usuário
  são revogados. É sinal de que o token foi roubado.
- **Logout** revoga o token atual e apaga o cookie.
- **Troca de senha** revoga todos os tokens do usuário.
- Ao abrir ou recarregar a página, o frontend chama `/renovar` para recuperar a
  sessão.

### Origem e CSRF

- Web e API ficam no mesmo domínio: nginx em produção, proxy do Angular em dev.
  Com isso o CORS fica desligado.
- O cookie `SameSite=Strict` restrito a `/api/auth` mitiga o CSRF no único
  endpoint que usa cookie. Os demais endpoints usam só o header `Authorization`,
  que um site de terceiros não consegue enviar.

### Senhas

- BCrypt.
- O login roda o BCrypt mesmo quando o e-mail não existe, para o tempo de resposta
  não revelar quais e-mails estão cadastrados. A mensagem de erro é a mesma nos
  dois casos.

## Alternativas consideradas

- **JWT único de longa duração em `localStorage`**, o padrão mais comum em
  tutoriais: descartado pelos motivos do contexto.
- **Sessão no servidor com cookie de sessão** (Spring Session JDBC + token CSRF):
  seria igualmente segura e até mais simples para uma SPA da mesma origem. A opção
  por JWT + refresh foi tomada porque esse desenho aparece com frequência nas vagas
  de backend, e o projeto também é portfólio. A revogação continua possível pelo
  refresh token.
- **Filtro JWT próprio com a biblioteca jjwt:** descartado. O Resource Server do
  Spring Security já valida assinatura, expiração e formato, com menos código.

## Consequências

- Um token de acesso vazado vale no máximo 15 minutos, e uma sessão pode ser
  revogada a qualquer momento.
- O interceptor do frontend precisa renovar o token quando recebe `401` e repetir a
  requisição, sem disparar várias renovações ao mesmo tempo.
- O limite de tentativas de login entra na Fatia 02, junto com o cadastro público
  ([ADR 0009](0009-contas-de-usuario-publicas.md)), antes de expor o sistema na
  internet.
