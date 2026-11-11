-- Fatia 04a: importação de extratos (ADR 0006 e ADR 0014).
-- Registro de cada importação confirmada. O arquivo não é guardado: só o hash, o nome e as
-- quantidades. Do OFX, só o hash de BANKID e ACCTID e os 4 últimos dígitos do ACCTID.
CREATE TABLE importacoes (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id      BIGINT       NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    conta_id        BIGINT       NOT NULL,
    formato         VARCHAR(3)   NOT NULL,
    nome_arquivo    VARCHAR(255) NOT NULL,
    hash_arquivo    VARCHAR(64)  NOT NULL,
    extrato_hash    VARCHAR(64),
    extrato_final   VARCHAR(4),
    data_inicio     DATE         NOT NULL,
    data_fim        DATE         NOT NULL,
    lidas           INTEGER      NOT NULL,
    importadas      INTEGER      NOT NULL,
    duplicadas      INTEGER      NOT NULL,
    anteriores      INTEGER      NOT NULL,
    fora            INTEGER      NOT NULL,
    desmarcadas     INTEGER      NOT NULL,
    criada_em       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    desfeita_em     TIMESTAMPTZ,
    CONSTRAINT ck_importacoes_formato CHECK (formato IN ('OFX', 'CSV')),
    CONSTRAINT ck_importacoes_periodo CHECK (data_inicio <= data_fim),
    CONSTRAINT ck_importacoes_quantidades CHECK (
        importadas > 0 AND duplicadas >= 0 AND anteriores >= 0 AND fora >= 0 AND desmarcadas >= 0
        AND lidas = importadas + duplicadas + anteriores + fora + desmarcadas),
    CONSTRAINT ck_importacoes_extrato CHECK ((extrato_hash IS NULL) = (extrato_final IS NULL)),
    -- Chave da FK dos lançamentos, que leva a conta junto: o importado não muda de conta.
    CONSTRAINT uq_importacoes_id_conta_usuario UNIQUE (id, conta_id, usuario_id),
    -- O registro não é uso da conta: sai com ela (a conta só se exclui sem lançamentos).
    CONSTRAINT fk_importacoes_conta FOREIGN KEY (conta_id, usuario_id)
        REFERENCES contas (id, usuario_id) ON DELETE CASCADE
);

-- Histórico, o mais recente primeiro.
CREATE INDEX ix_importacoes_usuario ON importacoes (usuario_id, criada_em);
-- Serve à FK de contas.
CREATE INDEX ix_importacoes_conta ON importacoes (conta_id, usuario_id);
-- "Este arquivo já foi importado": só as importações que não foram desfeitas.
CREATE INDEX ix_importacoes_hash ON importacoes (usuario_id, hash_arquivo) WHERE desfeita_em IS NULL;

-- Perfil de CSV: um por conta, criado na primeira importação de CSV dela.
CREATE TABLE perfis_csv (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id            BIGINT      NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    conta_id              BIGINT      NOT NULL,
    separador             VARCHAR(20) NOT NULL,
    linhas_de_cabecalho   INTEGER     NOT NULL,
    coluna_data           INTEGER     NOT NULL,
    formato_data          VARCHAR(10) NOT NULL,
    coluna_descricao      INTEGER     NOT NULL,
    coluna_valor          INTEGER     NOT NULL,
    coluna_saidas         INTEGER,
    coluna_identificador  INTEGER,
    separador_decimal     VARCHAR(10) NOT NULL,
    atualizado_em         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_perfis_csv_conta UNIQUE (conta_id),
    CONSTRAINT ck_perfis_csv_separador CHECK (separador IN ('VIRGULA', 'PONTO_E_VIRGULA', 'TAB')),
    CONSTRAINT ck_perfis_csv_decimal CHECK (separador_decimal IN ('VIRGULA', 'PONTO')),
    CONSTRAINT ck_perfis_csv_formato_data CHECK (formato_data IN ('DD_MM_AAAA', 'DD_MM_AA', 'AAAA_MM_DD')),
    CONSTRAINT ck_perfis_csv_cabecalho CHECK (linhas_de_cabecalho BETWEEN 0 AND 20),
    CONSTRAINT ck_perfis_csv_colunas CHECK (
        coluna_data BETWEEN 1 AND 50 AND coluna_descricao BETWEEN 1 AND 50 AND coluna_valor BETWEEN 1 AND 50
        AND (coluna_saidas IS NULL OR coluna_saidas BETWEEN 1 AND 50)
        AND (coluna_identificador IS NULL OR coluna_identificador BETWEEN 1 AND 50)),
    CONSTRAINT fk_perfis_csv_conta FOREIGN KEY (conta_id, usuario_id)
        REFERENCES contas (id, usuario_id) ON DELETE CASCADE
);

-- Lançamentos importados (ADR 0006 e ADR 0014): a importação de onde vieram, a chave de
-- idempotência, a descrição original do banco e a marca de excluído (ignorado).
ALTER TABLE lancamentos
    ADD COLUMN importacao_id     BIGINT,
    ADD COLUMN chave_importacao  VARCHAR(300),
    ADD COLUMN descricao_banco   VARCHAR(255),
    ADD COLUMN ignorado_em       TIMESTAMPTZ,
    ADD CONSTRAINT ck_lancamentos_importado CHECK (
        (origem = 'IMPORTADO') = (importacao_id IS NOT NULL)
        AND (importacao_id IS NULL) = (chave_importacao IS NULL)
        AND (importacao_id IS NULL) = (descricao_banco IS NULL)),
    -- Só o importado é ignorado, e ele perde a categoria (não segura a exclusão dela).
    ADD CONSTRAINT ck_lancamentos_ignorado CHECK (
        ignorado_em IS NULL OR (importacao_id IS NOT NULL AND categoria_id IS NULL)),
    ADD CONSTRAINT fk_lancamentos_importacao FOREIGN KEY (importacao_id, conta_id, usuario_id)
        REFERENCES importacoes (id, conta_id, usuario_id) ON DELETE RESTRICT;

-- ADR 0006: a mesma chave não entra duas vezes para o mesmo usuário.
CREATE UNIQUE INDEX ux_lancamentos_chave_importacao
    ON lancamentos (usuario_id, chave_importacao) WHERE chave_importacao IS NOT NULL;
-- Desfazer, contar e listar os excluídos de uma importação; serve à FK de importações.
CREATE INDEX ix_lancamentos_importacao ON lancamentos (importacao_id) WHERE importacao_id IS NOT NULL;
