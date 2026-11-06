# Flux — finanças pessoais

[![CI](https://github.com/Trevisani32/flux/actions/workflows/ci.yml/badge.svg)](https://github.com/Trevisani32/flux/actions/workflows/ci.yml)

Sistema web de finanças pessoais para contas, cartões, importação de extratos e orçamento, de uso pessoal e portfólio, com dados fictícios.

Em construção.

## Stack

- Java 25, Spring Boot 4.1 e Spring Modulith
- PostgreSQL 17
- Angular 21 com Angular Material
- Docker

## Como rodar localmente

Pré-requisitos: JDK 25, Node 22.12+ ou 24+ e Docker.

```bash
docker compose -f docker-compose.dev.yml up -d --wait
cd api && ./mvnw spring-boot:run
cd web && npm ci && npm start
```

No Windows, use `mvnw.cmd spring-boot:run` e defina `JAVA_HOME` para um JDK 25. Abra `http://localhost:4200` e entre com o usuário de desenvolvimento definido em `api/src/main/resources/application-dev.yml`.

## Testes

`cd api && ./mvnw verify` exige Docker. Para a web: `cd web && npm run lint && npm test && npm run build`.

## Documentação

- [Produto](produto.md)
- [Decisões de arquitetura](decisoes/)

## Licença

MIT.
