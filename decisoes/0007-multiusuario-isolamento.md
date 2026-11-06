# ADR 0007 — Multiusuário com isolamento por usuário

- **Status:** Aceito
- **Data:** 2026-11-06

## Contexto

O dono é o único usuário real, mas a demonstração precisa de um usuário com dados
fictícios, e no futuro pode haver finanças compartilhadas. Transformar um sistema
de um usuário só em multiusuário depois exige mexer em todas as tabelas e
consultas. Fazer isso desde o início custa uma coluna e um parâmetro.

Num sistema financeiro, deixar vazar um dado de outro usuário é a falha mais grave
possível.

## Decisão

- **Toda tabela de negócio** tem `usuario_id BIGINT NOT NULL REFERENCES usuarios(id)`,
  e os índices começam por `usuario_id`.
- **Repositórios recebem o usuário explicitamente:**
  `findByIdAndUsuarioId(id, usuarioId)`, `findAllByUsuarioIdAnd...`. O código de
  negócio nunca usa `findById` puro.
- **O usuário logado** vem de `UsuarioAtual.id()`, que lê o `sub` do JWT no
  `SecurityContext`. O id do usuário nunca é aceito pela URL nem pelo corpo da
  requisição.
- **Recurso de outro usuário é tratado como inexistente:**
  - na URL (`GET /api/contas/42`): `404` com o mesmo corpo de quando não existe;
  - como referência no corpo (`contaId: 42`): `422` com a mesma mensagem de "conta
    não encontrada".
- **Teste obrigatório por recurso:** uma classe de apoio cria dois usuários e cada
  módulo testa que o usuário B não lista, não lê, não edita, não exclui e não
  referencia um recurso do usuário A.
- No MVP não há papéis. Todo usuário só vê o que é dele.

## Alternativas consideradas

- **Row Level Security do PostgreSQL:** é a garantia mais forte, mas exige definir
  o usuário na conexão a cada requisição, o que complica o pool e os testes. Fica
  como possível reforço futuro, somado a esta decisão e não no lugar dela.
- **`@TenantId` / `@Filter` do Hibernate:** filtra automaticamente, mas não cobre
  consultas nativas, que serão comuns nos relatórios, e esconde a regra. Foi
  preferido o parâmetro explícito, que fica visível na revisão de código e é
  testável.

## Consequências

- Esquecer o filtro é o risco principal. Ele é mitigado pela convenção de nomes dos
  repositórios e pelo teste de isolamento obrigatório.
- O usuário da demo é um usuário comum, sem caminho de código especial.
- Finanças compartilhadas, se vierem, exigirão um ADR novo (ex.: tabela de acesso
  compartilhado a contas).
