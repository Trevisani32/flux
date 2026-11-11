-- Fatia 04b: regras de categorização (ADR 0015).
-- Cada regra tem uma condição sobre a descrição (contém, começa com ou igual a), um filtro de conta
-- opcional e a ação: a categoria e, opcionalmente, uma descrição legível. A natureza vem da
-- categoria: uma regra de despesa só pega despesa. Vale a primeira, pela posição.
CREATE TABLE regras_categorizacao (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id     BIGINT        NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    posicao        INTEGER       NOT NULL,
    condicao       VARCHAR(12)   NOT NULL,
    texto          VARCHAR(255)  NOT NULL,
    texto_chave    VARCHAR(1020) NOT NULL,
    conta_id       BIGINT,
    natureza       VARCHAR(10)   NOT NULL,
    categoria_id   BIGINT        NOT NULL,
    descricao      VARCHAR(255),
    criada_em      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    atualizada_em  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_regras_condicao CHECK (condicao IN ('CONTEM', 'COMECA_COM', 'IGUAL_A')),
    CONSTRAINT ck_regras_natureza CHECK (natureza IN ('RECEITA', 'DESPESA')),
    CONSTRAINT ck_regras_posicao CHECK (posicao >= 1),
    CONSTRAINT ck_regras_texto CHECK (char_length(texto_chave) >= 2),
    -- A posição é única por usuário, conferida no fim da transação: reordenar troca posições.
    CONSTRAINT uq_regras_posicao UNIQUE (usuario_id, posicao) DEFERRABLE INITIALLY DEFERRED,
    -- A natureza entra na chave: a regra só aponta para categoria do usuário e da natureza dela.
    -- ADR 0012: o uso de categoria é RESTRICT, nunca CASCADE.
    CONSTRAINT fk_regras_categoria FOREIGN KEY (categoria_id, usuario_id, natureza)
        REFERENCES categorias (id, usuario_id, natureza) ON DELETE RESTRICT,
    -- O filtro de conta não é uso dela: a regra só daquela conta sai junto com a conta.
    CONSTRAINT fk_regras_conta FOREIGN KEY (conta_id, usuario_id)
        REFERENCES contas (id, usuario_id) ON DELETE CASCADE
);

-- A mesma condição não se repete (por natureza e conta; "todas as contas" conta como uma).
CREATE UNIQUE INDEX ux_regras_condicao
    ON regras_categorizacao (usuario_id, natureza, condicao, texto_chave, conta_id) NULLS NOT DISTINCT;
-- Exclusão de categoria (RESTRICT) e uso da categoria.
CREATE INDEX ix_regras_categoria ON regras_categorizacao (categoria_id);
-- Serve à FK de contas (CASCADE).
CREATE INDEX ix_regras_conta ON regras_categorizacao (conta_id, usuario_id) WHERE conta_id IS NOT NULL;
