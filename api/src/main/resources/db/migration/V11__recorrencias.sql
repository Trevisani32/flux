-- Fatia 07a: recorrências (ADR 0022).
-- As ocorrências são calculadas quando alguém precisa delas; só as tratadas ficam gravadas: a efetivada (ligada ao
-- lançamento ou à compra) e a pulada, no máximo uma por período (o mês, a semana que começa na segunda ou o ano,
-- guardado como o primeiro dia dele). Nada entra em lancamentos antes da confirmação.

-- Chaves das FKs da ocorrência efetivada: o lançamento e a compra do mesmo usuário.
ALTER TABLE lancamentos ADD CONSTRAINT uq_lancamentos_id_usuario UNIQUE (id, usuario_id);
ALTER TABLE compras ADD CONSTRAINT uq_compras_id_usuario UNIQUE (id, usuario_id);

-- O valor é o digitado, positivo; o tipo dá o sinal (ADR 0002). A recorrência fica numa conta ou num cartão, nunca nos
-- dois, e o cartão só recebe despesa. dia_do_mes só na mensal; a semanal e a anual usam o dia de comeca_em.
-- ocorrencias_desde é o dia do cadastro, e "Retomar" o leva para o dia da retomada: só existem ocorrências a partir
-- do maior entre ele e comeca_em. termina_em antes de comeca_em só acontece ao encerrar uma que ainda não começou.
CREATE TABLE recorrencias (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id         BIGINT        NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    descricao          VARCHAR(255)  NOT NULL,
    valor              NUMERIC(19,2) NOT NULL,
    tipo               VARCHAR(10)   NOT NULL,
    categoria_id       BIGINT,
    conta_id           BIGINT,
    cartao_id          BIGINT,
    frequencia         VARCHAR(10)   NOT NULL,
    dia_do_mes         INTEGER,
    comeca_em          DATE          NOT NULL,
    termina_em         DATE,
    valor_varia        BOOLEAN       NOT NULL DEFAULT false,
    ocorrencias_desde  DATE          NOT NULL,
    criada_em          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    atualizada_em      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_recorrencias_tipo CHECK (tipo IN ('RECEITA', 'DESPESA')),
    CONSTRAINT ck_recorrencias_valor CHECK (valor > 0),
    CONSTRAINT ck_recorrencias_frequencia CHECK (frequencia IN ('MENSAL', 'SEMANAL', 'ANUAL')),
    CONSTRAINT ck_recorrencias_dia CHECK (
        (frequencia = 'MENSAL') = (dia_do_mes IS NOT NULL) AND (dia_do_mes IS NULL OR dia_do_mes BETWEEN 1 AND 31)),
    CONSTRAINT ck_recorrencias_conta_ou_cartao CHECK ((conta_id IS NULL) <> (cartao_id IS NULL)),
    CONSTRAINT ck_recorrencias_cartao_so_despesa CHECK (cartao_id IS NULL OR tipo = 'DESPESA'),
    CONSTRAINT uq_recorrencias_id_usuario UNIQUE (id, usuario_id),
    -- A recorrência é uso da categoria (ADR 0012): RESTRICT. O tipo entra na chave, como nos lançamentos.
    CONSTRAINT fk_recorrencias_categoria FOREIGN KEY (categoria_id, usuario_id, tipo)
        REFERENCES categorias (id, usuario_id, natureza) ON DELETE RESTRICT,
    -- Não é uso da conta nem do cartão: sai junto com eles, como as regras só da conta (V6).
    CONSTRAINT fk_recorrencias_conta FOREIGN KEY (conta_id, usuario_id)
        REFERENCES contas (id, usuario_id) ON DELETE CASCADE,
    CONSTRAINT fk_recorrencias_cartao FOREIGN KEY (cartao_id, usuario_id)
        REFERENCES cartoes (id, usuario_id) ON DELETE CASCADE
);

-- A lista do usuário e a exclusão dele.
CREATE INDEX ix_recorrencias_usuario ON recorrencias (usuario_id);
-- Exclusão de categoria (RESTRICT) e uso da categoria.
CREATE INDEX ix_recorrencias_categoria ON recorrencias (categoria_id) WHERE categoria_id IS NOT NULL;
-- Servem às FKs de contas e de cartões (CASCADE).
CREATE INDEX ix_recorrencias_conta ON recorrencias (conta_id, usuario_id) WHERE conta_id IS NOT NULL;
CREATE INDEX ix_recorrencias_cartao ON recorrencias (cartao_id, usuario_id) WHERE cartao_id IS NOT NULL;

-- A ocorrência tratada: a efetivada aponta para o lançamento (na conta) ou para a compra (no cartão, porque editar a
-- compra apaga e grava as parcelas de novo, ADR 0017); a pulada, para nada. Excluir o lançamento ou a compra apaga a
-- linha, e a ocorrência volta a ser prevista (ou atrasada).
CREATE TABLE ocorrencias_tratadas (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id      BIGINT       NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    recorrencia_id  BIGINT       NOT NULL,
    periodo         DATE         NOT NULL,
    data_prevista   DATE         NOT NULL,
    situacao        VARCHAR(10)  NOT NULL,
    lancamento_id   BIGINT,
    compra_id       BIGINT,
    tratada_em      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_ocorrencias_situacao CHECK (situacao IN ('EFETIVADA', 'PULADA')),
    CONSTRAINT ck_ocorrencias_ligacao CHECK (
        (situacao = 'PULADA' AND lancamento_id IS NULL AND compra_id IS NULL)
        OR (situacao = 'EFETIVADA' AND (lancamento_id IS NULL) <> (compra_id IS NULL))),
    -- No máximo uma tratada por período: mudar o dia não traz de volta a do mês já tratado.
    CONSTRAINT uq_ocorrencias_periodo UNIQUE (recorrencia_id, periodo),
    CONSTRAINT fk_ocorrencias_recorrencia FOREIGN KEY (recorrencia_id, usuario_id)
        REFERENCES recorrencias (id, usuario_id) ON DELETE CASCADE,
    CONSTRAINT fk_ocorrencias_lancamento FOREIGN KEY (lancamento_id, usuario_id)
        REFERENCES lancamentos (id, usuario_id) ON DELETE CASCADE,
    CONSTRAINT fk_ocorrencias_compra FOREIGN KEY (compra_id, usuario_id)
        REFERENCES compras (id, usuario_id) ON DELETE CASCADE
);

-- Um lançamento ou uma compra liga a uma ocorrência só; servem também às FKs (CASCADE).
CREATE UNIQUE INDEX ux_ocorrencias_lancamento ON ocorrencias_tratadas (lancamento_id) WHERE lancamento_id IS NOT NULL;
CREATE UNIQUE INDEX ux_ocorrencias_compra ON ocorrencias_tratadas (compra_id) WHERE compra_id IS NOT NULL;
-- As tratadas do usuário e a exclusão dele.
CREATE INDEX ix_ocorrencias_usuario ON ocorrencias_tratadas (usuario_id);
