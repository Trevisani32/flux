CREATE TABLE usuarios (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nome           VARCHAR(120) NOT NULL,
    email          VARCHAR(255) NOT NULL UNIQUE,
    senha_hash     VARCHAR(100) NOT NULL,
    criado_em      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizado_em  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE tokens_renovacao (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id       BIGINT      NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    token_hash       VARCHAR(64) NOT NULL UNIQUE,
    expira_em        TIMESTAMPTZ NOT NULL,
    criado_em        TIMESTAMPTZ NOT NULL DEFAULT now(),
    revogado_em      TIMESTAMPTZ,
    substituido_por  BIGINT      REFERENCES tokens_renovacao (id)
);

CREATE INDEX ix_tokens_renovacao_usuario ON tokens_renovacao (usuario_id);
