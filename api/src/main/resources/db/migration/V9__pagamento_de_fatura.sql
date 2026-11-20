-- Fatia 05b: pagamento da fatura, "paga fora do Flux" e o importado que vira pagamento (ADR 0017 e ADR 0018).
-- O pagamento é um lançamento da conta, do tipo PAGAMENTO_FATURA e negativo, ligado à fatura que ele paga
-- (fatura_paga_id), no lugar da tabela pagamentos_fatura do ADR 0005 (decisão 46). Não tem categoria: a FK pela
-- natureza (fk_lancamentos_categoria) recusa, porque nenhuma categoria tem a natureza PAGAMENTO_FATURA.

ALTER TABLE lancamentos
    DROP CONSTRAINT ck_lancamentos_tipo,
    DROP CONSTRAINT ck_lancamentos_sinal;

ALTER TABLE lancamentos
    ADD CONSTRAINT ck_lancamentos_tipo CHECK (tipo IN ('RECEITA', 'DESPESA', 'TRANSFERENCIA', 'PAGAMENTO_FATURA')),
    -- ADR 0002: o pagamento é dinheiro que sai da conta.
    ADD CONSTRAINT ck_lancamentos_sinal CHECK (
        (tipo = 'RECEITA' AND valor > 0)
        OR (tipo = 'DESPESA' AND valor < 0)
        OR (tipo = 'TRANSFERENCIA' AND valor <> 0)
        OR (tipo = 'PAGAMENTO_FATURA' AND valor < 0)),
    ADD COLUMN fatura_paga_id BIGINT,
    -- O pagamento, e só ele, aponta para a fatura. Ele fica numa conta: o ck_lancamentos_conta_ou_fatura e o
    -- ck_lancamentos_tipo_na_fatura da V8 já recusam o pagamento dentro de uma fatura.
    ADD CONSTRAINT ck_lancamentos_pagamento CHECK ((tipo = 'PAGAMENTO_FATURA') = (fatura_paga_id IS NOT NULL)),
    -- Como o lado de transferência (V7), o pagamento nunca fica excluído (ignorado): o importado convertido se desfaz antes.
    ADD CONSTRAINT ck_lancamentos_pagamento_nao_ignorado CHECK (fatura_paga_id IS NULL OR ignorado_em IS NULL);

-- Chave da FK do pagamento: a fatura do mesmo usuário.
ALTER TABLE faturas
    ADD CONSTRAINT uq_faturas_id_usuario UNIQUE (id, usuario_id),
    -- Decisão 36: a fatura fechada marcada como paga fora do Flux, sem lançamento.
    ADD COLUMN paga_fora_em TIMESTAMPTZ;

-- A fatura com pagamento não se apaga (RESTRICT): segura o cartão, como as parcelas.
ALTER TABLE lancamentos
    ADD CONSTRAINT fk_lancamentos_fatura_paga FOREIGN KEY (fatura_paga_id, usuario_id)
        REFERENCES faturas (id, usuario_id) ON DELETE RESTRICT;

-- Os pagamentos de uma fatura (o pago, a situação e o limite disponível); serve também à FK de faturas.
CREATE INDEX ix_lancamentos_fatura_paga ON lancamentos (fatura_paga_id) WHERE fatura_paga_id IS NOT NULL;
