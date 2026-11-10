-- Fatia 03b: lançamentos e transferências (ADR 0013).
-- A FK composta (conta_id, usuario_id) precisa de uma chave única com as duas colunas.
ALTER TABLE contas ADD CONSTRAINT uq_contas_id_usuario UNIQUE (id, usuario_id);

-- A transferência é só o vínculo entre os dois lançamentos (saída e entrada): data, valor,
-- conta e descrição ficam em cada lado.
CREATE TABLE transferencias (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id  BIGINT      NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    criada_em   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_transferencias_id_usuario UNIQUE (id, usuario_id)
);

CREATE TABLE lancamentos (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id        BIGINT        NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    conta_id          BIGINT        NOT NULL,
    data              DATE          NOT NULL,
    descricao         VARCHAR(255)  NOT NULL,
    descricao_chave   VARCHAR(1020) NOT NULL,
    valor             NUMERIC(19,2) NOT NULL,
    tipo              VARCHAR(20)   NOT NULL,
    categoria_id      BIGINT,
    observacao        VARCHAR(1000),
    origem            VARCHAR(10)   NOT NULL,
    transferencia_id  BIGINT,
    criado_em         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    atualizado_em     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_lancamentos_tipo CHECK (tipo IN ('RECEITA', 'DESPESA', 'TRANSFERENCIA')),
    CONSTRAINT ck_lancamentos_origem CHECK (origem IN ('MANUAL', 'IMPORTADO')),
    -- ADR 0002: entrada positiva, saída negativa; a transferência tem um lado de cada sinal.
    CONSTRAINT ck_lancamentos_sinal CHECK (
        (tipo = 'RECEITA' AND valor > 0)
        OR (tipo = 'DESPESA' AND valor < 0)
        OR (tipo = 'TRANSFERENCIA' AND valor <> 0)),
    CONSTRAINT ck_lancamentos_transferencia CHECK ((tipo = 'TRANSFERENCIA') = (transferencia_id IS NOT NULL)),
    -- ADR 0012: o uso de conta e de categoria é RESTRICT, nunca CASCADE.
    CONSTRAINT fk_lancamentos_conta FOREIGN KEY (conta_id, usuario_id)
        REFERENCES contas (id, usuario_id) ON DELETE RESTRICT,
    -- O tipo entra na chave: o banco recusa categoria de outra natureza e de outro usuário. A
    -- transferência não tem categoria (com categoria_id nulo, a FK não é conferida).
    CONSTRAINT fk_lancamentos_categoria FOREIGN KEY (categoria_id, usuario_id, tipo)
        REFERENCES categorias (id, usuario_id, natureza) ON DELETE RESTRICT,
    CONSTRAINT fk_lancamentos_transferencia FOREIGN KEY (transferencia_id, usuario_id)
        REFERENCES transferencias (id, usuario_id) ON DELETE CASCADE
);

-- Lista do mês.
CREATE INDEX ix_lancamentos_usuario_data ON lancamentos (usuario_id, data);
-- Saldo por conta e filtro de conta (ADR 0003); serve também à FK de contas.
CREATE INDEX ix_lancamentos_usuario_conta_data ON lancamentos (usuario_id, conta_id, data);
-- Exclusão de categoria (FK RESTRICT) e uso da categoria.
CREATE INDEX ix_lancamentos_categoria ON lancamentos (categoria_id) WHERE categoria_id IS NOT NULL;
-- No máximo um lado de cada sinal por transferência; serve também à FK de transferências.
CREATE UNIQUE INDEX ux_lancamentos_lado_da_transferencia
    ON lancamentos (transferencia_id, (valor > 0)) WHERE transferencia_id IS NOT NULL;
