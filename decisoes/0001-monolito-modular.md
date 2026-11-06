# ADR 0001 — Monólito modular com Spring Modulith

- **Status:** Aceito
- **Data:** 2026-11-06

## Contexto

O sistema tem várias áreas de negócio que conversam entre si: contas,
lançamentos, cartões, importação, categorização, orçamento e relatórios. Um único
desenvolvedor mantém tudo, e há um único usuário real, mais a demonstração.

Microsserviços trariam vários deploys, chamadas de rede e consistência eventual
entre bancos, sem nenhum ganho nessa escala. Já um monólito sem fronteiras tende
a virar um emaranhado em que qualquer classe usa qualquer outra.

## Decisão

Uma única aplicação Spring Boot, dividida em **módulos de aplicação do Spring
Modulith**.

- Cada pacote direto abaixo de `com.flux` é um módulo (`contas`,
  `lancamentos`, `cartoes`…).
- **O pacote raiz do módulo é a API pública.** Ali ficam as interfaces de consulta,
  os DTOs dessas interfaces e os eventos publicados. Todo o resto fica em
  `interno/` e é privado.
- **Comunicação entre módulos:**
  - consultas síncronas chamam a API pública do outro módulo;
  - eventos de domínio são usados quando quem publica não deve conhecer quem reage.
    Exemplo: `UsuarioCriado` faz o módulo de categorias criar as categorias padrão.
- **Referência entre módulos só por id.** Não há relacionamento JPA atravessando
  módulos. As chaves estrangeiras continuam no banco, para garantir a integridade.
- Um teste com `ApplicationModules.of(...).verify()` roda no build e falha se um
  módulo usar o `interno/` de outro ou se houver dependência circular.
- O `Documenter` do Modulith gera o diagrama de módulos usado no README.
- `compartilhado` é um módulo aberto, só para infraestrutura sem regra de negócio:
  tratamento de erros, `UsuarioAtual` e `Clock`.

## Alternativas consideradas

- **Microsserviços:** descartados pelo custo operacional, sem benefício para um
  desenvolvedor e um usuário.
- **Monólito em camadas** (`controller/`, `service/`, `repository/` globais):
  descartado porque espalha cada funcionalidade por pastas distantes e não impede
  acoplamento entre áreas.
- **Módulos Maven separados:** fronteiras mais rígidas, mas com build mais lento e
  mais configuração. O Modulith dá fronteiras verificadas sem esse custo.

## Consequências

- As fronteiras entre módulos são verificadas automaticamente, e não só combinadas.
- Se um dia fizer sentido extrair um módulo, os eventos e as APIs públicas já
  desenham o contrato.
- Exige disciplina de pacotes: classes que outros módulos usam precisam morar no
  pacote raiz do módulo.
- O banco é um só, com as tabelas de cada módulo. Consultas de relatório que
  atravessam módulos ficam no módulo `relatorios`, só de leitura.
