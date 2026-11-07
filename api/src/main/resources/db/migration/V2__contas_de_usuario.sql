ALTER TABLE usuarios
    ADD COLUMN email_confirmado_em TIMESTAMPTZ,
    ADD COLUMN termos_versao       VARCHAR(10),
    ADD COLUMN termos_aceitos_em   TIMESTAMPTZ;

UPDATE usuarios SET email_confirmado_em = criado_em;

CREATE INDEX ix_usuarios_nao_confirmados ON usuarios (criado_em) WHERE email_confirmado_em IS NULL;

ALTER TABLE tokens_renovacao
    DROP CONSTRAINT tokens_renovacao_substituido_por_fkey,
    ADD CONSTRAINT tokens_renovacao_substituido_por_fkey
        FOREIGN KEY (substituido_por) REFERENCES tokens_renovacao (id) ON DELETE SET NULL;

CREATE INDEX ix_tokens_renovacao_expira_em ON tokens_renovacao (expira_em);

CREATE TABLE tokens_email (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id     BIGINT       NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    finalidade     VARCHAR(20)  NOT NULL,
    token_hash     VARCHAR(64)  UNIQUE,
    email_novo     VARCHAR(255),
    expira_em      TIMESTAMPTZ  NOT NULL,
    criado_em      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    usado_em       TIMESTAMPTZ,
    invalidado_em  TIMESTAMPTZ,
    CONSTRAINT ck_tokens_email_finalidade
        CHECK (finalidade IN ('CONFIRMACAO', 'REDEFINICAO', 'TROCA_DE_EMAIL')),
    CONSTRAINT ck_tokens_email_email_novo
        CHECK ((finalidade = 'TROCA_DE_EMAIL') = (email_novo IS NOT NULL))
);

CREATE INDEX ix_tokens_email_usuario ON tokens_email (usuario_id, finalidade);
CREATE INDEX ix_tokens_email_expira_em ON tokens_email (expira_em);

CREATE TABLE event_publication (
    id                     UUID                     NOT NULL,
    listener_id            TEXT                     NOT NULL,
    event_type             TEXT                     NOT NULL,
    serialized_event       TEXT                     NOT NULL,
    publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date        TIMESTAMP WITH TIME ZONE,
    status                 TEXT,
    completion_attempts    INT,
    last_resubmission_date TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id)
);

CREATE INDEX event_publication_serialized_event_hash_idx ON event_publication USING hash (serialized_event);
CREATE INDEX event_publication_by_completion_date_idx ON event_publication (completion_date);
