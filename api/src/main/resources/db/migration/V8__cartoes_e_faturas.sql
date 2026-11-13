-- Fatia 05a: cartões, faturas, compras e estornos (ADR 0017).
-- O cartão é do módulo cartoes; as faturas, as compras e as parcelas são de lancamentos (decisão 45).
-- Cada parcela e cada estorno é um lançamento sem conta, numa fatura (decisões 20 e 22).

CREATE TABLE cartoes (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id          BIGINT        NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    nome                VARCHAR(60)   NOT NULL,
    nome_chave          VARCHAR(240)  NOT NULL,
    limite              NUMERIC(19,2) NOT NULL,
    dia_fechamento      INTEGER       NOT NULL,
    dia_vencimento      INTEGER       NOT NULL,
    conta_pagamento_id  BIGINT        NOT NULL,
    cor                 VARCHAR(20),
    arquivado_em        TIMESTAMPTZ,
    criado_em           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    atualizado_em       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_cartoes_limite CHECK (limite > 0),
    -- Decisão 3: dias de 1 a 31; com o vencimento no mesmo mês do fechamento (depois dele), o
    -- fechamento vai até o dia 27, senão empataria com o vencimento em fevereiro.
    CONSTRAINT ck_cartoes_dias CHECK (
        dia_fechamento BETWEEN 1 AND 31 AND dia_vencimento BETWEEN 1 AND 31
        AND (dia_vencimento <= dia_fechamento OR dia_fechamento <= 27)),
    CONSTRAINT ck_cartoes_cor
        CHECK (cor IS NULL OR cor IN ('OURO', 'LARANJA', 'ROSA', 'ROXO', 'INDIGO', 'AZUL', 'CIANO', 'CINZA')),
    CONSTRAINT uq_cartoes_id_usuario UNIQUE (id, usuario_id),
    -- Decisão 4: a conta de pagamento é uso da conta (ADR 0012), RESTRICT.
    CONSTRAINT fk_cartoes_conta FOREIGN KEY (conta_pagamento_id, usuario_id)
        REFERENCES contas (id, usuario_id) ON DELETE RESTRICT
);

CREATE INDEX ix_cartoes_usuario ON cartoes (usuario_id);
-- Serve à FK de contas e ao uso da conta.
CREATE INDEX ix_cartoes_conta ON cartoes (conta_pagamento_id, usuario_id);
CREATE UNIQUE INDEX ux_cartoes_nome_ativo ON cartoes (usuario_id, nome_chave) WHERE arquivado_em IS NULL;

-- Uma fatura por cartão e mês de referência (o mês do vencimento), criada sob demanda, com as datas
-- do momento gravadas (decisão 9). fechamento_ajustado marca o ajuste só daquela fatura (decisão 12), e
-- fechamento_normal guarda o fechamento pelos dias que valiam para ela: o "normal" do ajuste, que uma troca
-- de dias não muda na aberta nem nas anteriores (decisão 7).
CREATE TABLE faturas (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id           BIGINT      NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    cartao_id            BIGINT      NOT NULL,
    mes_referencia       DATE        NOT NULL,
    data_fechamento      DATE        NOT NULL,
    data_vencimento      DATE        NOT NULL,
    fechamento_ajustado  BOOLEAN     NOT NULL DEFAULT false,
    fechamento_normal    DATE        NOT NULL,
    criada_em            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_faturas_mes CHECK (EXTRACT(DAY FROM mes_referencia) = 1),
    CONSTRAINT ck_faturas_vencimento CHECK (date_trunc('month', data_vencimento)::date = mes_referencia),
    CONSTRAINT ck_faturas_fechamento CHECK (data_fechamento < data_vencimento),
    CONSTRAINT uq_faturas_cartao_mes UNIQUE (cartao_id, mes_referencia),
    -- Chave da FK das parcelas, com a data do vencimento (decisão 47).
    CONSTRAINT uq_faturas_parcela UNIQUE (id, cartao_id, usuario_id, data_vencimento),
    -- A fatura vazia sai com o cartão; a que tem lançamento segura o cartão pelas FKs RESTRICT deles.
    CONSTRAINT fk_faturas_cartao FOREIGN KEY (cartao_id, usuario_id)
        REFERENCES cartoes (id, usuario_id) ON DELETE CASCADE
);

-- As faturas de um usuário: a lista dos cartões lê todas de uma vez, e a exclusão do usuário apaga por ele.
CREATE INDEX ix_faturas_usuario ON faturas (usuario_id);

-- A compra (ou o estorno) junta as parcelas. A descrição, a categoria e a observação ficam em cada
-- parcela; aqui ficam o que define as parcelas.
CREATE TABLE compras (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id       BIGINT        NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    cartao_id        BIGINT        NOT NULL,
    tipo             VARCHAR(10)   NOT NULL,
    data_compra      DATE          NOT NULL,
    valor_total      NUMERIC(19,2) NOT NULL,
    parcelas         INTEGER       NOT NULL,
    parcela_inicial  INTEGER       NOT NULL,
    criada_em        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    atualizada_em    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_compras_tipo CHECK (tipo IN ('COMPRA', 'ESTORNO')),
    -- Decisões 14 e 17: de 1 a 48 parcelas; a primeira no Flux vai da 1ª à última.
    CONSTRAINT ck_compras_parcelas CHECK (parcelas BETWEEN 1 AND 48 AND parcela_inicial BETWEEN 1 AND parcelas),
    -- Decisão 15: ao menos 1 centavo por parcela.
    CONSTRAINT ck_compras_valor CHECK (valor_total >= parcelas * 0.01),
    -- Decisão 20: o estorno é um valor só, solto na fatura da data dele.
    CONSTRAINT ck_compras_estorno CHECK (tipo = 'COMPRA' OR parcelas = 1),
    CONSTRAINT uq_compras_id_cartao_usuario UNIQUE (id, cartao_id, usuario_id),
    -- A compra é uso do cartão (decisão 28): RESTRICT.
    CONSTRAINT fk_compras_cartao FOREIGN KEY (cartao_id, usuario_id)
        REFERENCES cartoes (id, usuario_id) ON DELETE RESTRICT
);

CREATE INDEX ix_compras_usuario ON compras (usuario_id);
-- Serve à FK de cartões, ao uso do cartão e às compras que um ajuste ou a troca de dias recoloca.
CREATE INDEX ix_compras_cartao_data ON compras (cartao_id, data_compra);

-- A parcela e o estorno: sem conta, numa fatura, com o cartão, a compra e o número da parcela.
ALTER TABLE lancamentos
    ALTER COLUMN conta_id DROP NOT NULL,
    ADD COLUMN cartao_id       BIGINT,
    ADD COLUMN fatura_id       BIGINT,
    ADD COLUMN compra_id       BIGINT,
    ADD COLUMN numero_parcela  INTEGER,
    -- ADR 0017: o lançamento está numa conta ou numa fatura, nunca nas duas nem em nenhuma.
    ADD CONSTRAINT ck_lancamentos_conta_ou_fatura CHECK ((conta_id IS NULL) <> (fatura_id IS NULL)),
    -- Na fatura, cartão, fatura, compra e número vêm juntos.
    ADD CONSTRAINT ck_lancamentos_parcela CHECK (
        (fatura_id IS NULL) = (cartao_id IS NULL)
        AND (fatura_id IS NULL) = (compra_id IS NULL)
        AND (fatura_id IS NULL) = (numero_parcela IS NULL)),
    ADD CONSTRAINT ck_lancamentos_numero_parcela CHECK (numero_parcela IS NULL OR numero_parcela BETWEEN 1 AND 48),
    -- Na fatura só há compra (despesa) e estorno (receita); transferência, nunca.
    ADD CONSTRAINT ck_lancamentos_tipo_na_fatura CHECK (fatura_id IS NULL OR tipo IN ('RECEITA', 'DESPESA')),
    -- A FK da importação leva conta_id e, com ele nulo, não confere nada (MATCH SIMPLE): até a 05c,
    -- o importado fica só em conta (decisão 47).
    ADD CONSTRAINT ck_lancamentos_importado_em_conta CHECK (importacao_id IS NULL OR conta_id IS NOT NULL),
    ADD CONSTRAINT fk_lancamentos_cartao FOREIGN KEY (cartao_id, usuario_id)
        REFERENCES cartoes (id, usuario_id) ON DELETE RESTRICT,
    -- Decisão 47: a parcela está no cartão da fatura e na data do vencimento dela; mudar o vencimento
    -- da fatura (a troca de dias) leva a data das parcelas junto.
    ADD CONSTRAINT fk_lancamentos_fatura FOREIGN KEY (fatura_id, cartao_id, usuario_id, data)
        REFERENCES faturas (id, cartao_id, usuario_id, data_vencimento) ON UPDATE CASCADE ON DELETE RESTRICT,
    ADD CONSTRAINT fk_lancamentos_compra FOREIGN KEY (compra_id, cartao_id, usuario_id)
        REFERENCES compras (id, cartao_id, usuario_id) ON DELETE RESTRICT;

-- Uma parcela de cada número por compra; serve também à FK de compras.
CREATE UNIQUE INDEX ux_lancamentos_parcela ON lancamentos (compra_id, numero_parcela) WHERE compra_id IS NOT NULL;
-- Os lançamentos de uma fatura; serve à FK de faturas.
CREATE INDEX ix_lancamentos_fatura ON lancamentos (fatura_id) WHERE fatura_id IS NOT NULL;
-- Serve à FK de cartões.
CREATE INDEX ix_lancamentos_cartao ON lancamentos (cartao_id, usuario_id) WHERE cartao_id IS NOT NULL;
