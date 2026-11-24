-- Fatia 05c: importação de fatura de cartão e regras com cartão (ADR 0017 e ADR 0019).
-- A importação passa a ser numa conta ou numa fatura de cartão (decisões 40 e 43): o registro guarda o cartão e a
-- fatura escolhida no lugar da conta. A compra importada aponta para a importação (compras.importacao_id), e as
-- parcelas importadas ficam na fatura, ligadas à importação pela FK com o cartão. As regras ganham o cartão como
-- filtro (decisão 44), nunca junto com a conta. O perfil de CSV passa a ser de uma conta ou de um cartão (decisão 39).

-- Uma importação é de uma conta ou de um cartão, e a de cartão tem a fatura escolhida.
ALTER TABLE importacoes
    ALTER COLUMN conta_id DROP NOT NULL,
    ADD COLUMN cartao_id BIGINT,
    ADD COLUMN fatura_id BIGINT,
    ADD CONSTRAINT ck_importacoes_conta_ou_cartao CHECK ((conta_id IS NULL) <> (cartao_id IS NULL)),
    ADD CONSTRAINT ck_importacoes_fatura CHECK ((cartao_id IS NULL) = (fatura_id IS NULL)),
    -- Chave das FKs da compra e da parcela importadas, que levam o cartão junto.
    ADD CONSTRAINT uq_importacoes_id_cartao_usuario UNIQUE (id, cartao_id, usuario_id),
    -- O registro não é uso do cartão: sai com ele, como sai com a conta (V5).
    ADD CONSTRAINT fk_importacoes_cartao FOREIGN KEY (cartao_id, usuario_id)
        REFERENCES cartoes (id, usuario_id) ON DELETE CASCADE;

-- Chave da FK da fatura escolhida: a fatura do mesmo cartão e do mesmo usuário.
ALTER TABLE faturas
    ADD CONSTRAINT uq_faturas_id_cartao_usuario UNIQUE (id, cartao_id, usuario_id);

-- NO ACTION: conferida no fim do comando. Excluir o cartão (ou o usuário) apaga as faturas e as importações dele no
-- mesmo comando; uma fatura com importação nunca sai sozinha, porque o cartão com importação não é vazio na troca de
-- dias (ADR 0019).
ALTER TABLE importacoes
    ADD CONSTRAINT fk_importacoes_fatura FOREIGN KEY (fatura_id, cartao_id, usuario_id)
        REFERENCES faturas (id, cartao_id, usuario_id);

-- Serve à FK de cartões e ao critério do cartão vazio.
CREATE INDEX ix_importacoes_cartao ON importacoes (cartao_id, usuario_id) WHERE cartao_id IS NOT NULL;
-- Serve à FK de faturas.
CREATE INDEX ix_importacoes_fatura ON importacoes (fatura_id) WHERE fatura_id IS NOT NULL;

-- A parcela e o estorno importados (decisão 47): a FK da V5 leva conta_id e não confere nada sem ele (MATCH SIMPLE);
-- a nova leva o cartão. O importado de conta fica preso à importação da conta dele, e o de fatura, à do cartão dele.
-- O CHECK que deixava o importado só em conta (V8) sai. O lançamento de fatura nunca fica excluído (ignorado): a
-- compra importada se exclui de verdade, com as parcelas.
ALTER TABLE lancamentos
    DROP CONSTRAINT ck_lancamentos_importado_em_conta,
    ADD CONSTRAINT ck_lancamentos_ignorado_em_conta CHECK (ignorado_em IS NULL OR fatura_id IS NULL),
    ADD CONSTRAINT fk_lancamentos_importacao_do_cartao FOREIGN KEY (importacao_id, cartao_id, usuario_id)
        REFERENCES importacoes (id, cartao_id, usuario_id) ON DELETE RESTRICT;

-- A compra importada (decisão 41): a importação de onde veio. Desfazer a importação apaga as parcelas e a compra.
ALTER TABLE compras
    ADD COLUMN importacao_id BIGINT,
    ADD CONSTRAINT fk_compras_importacao FOREIGN KEY (importacao_id, cartao_id, usuario_id)
        REFERENCES importacoes (id, cartao_id, usuario_id) ON DELETE RESTRICT;

-- Desfazer e contar as compras de uma importação; serve à FK de importações.
CREATE INDEX ix_compras_importacao ON compras (importacao_id) WHERE importacao_id IS NOT NULL;

-- Decisão 39: o perfil de CSV de um cartão, com a opção "compras vêm positivas" (só no cartão).
ALTER TABLE perfis_csv
    ALTER COLUMN conta_id DROP NOT NULL,
    ADD COLUMN cartao_id BIGINT,
    ADD COLUMN compras_positivas BOOLEAN NOT NULL DEFAULT false,
    ADD CONSTRAINT ck_perfis_csv_conta_ou_cartao CHECK ((conta_id IS NULL) <> (cartao_id IS NULL)),
    ADD CONSTRAINT ck_perfis_csv_compras_positivas CHECK (cartao_id IS NOT NULL OR NOT compras_positivas),
    ADD CONSTRAINT uq_perfis_csv_cartao UNIQUE (cartao_id),
    ADD CONSTRAINT fk_perfis_csv_cartao FOREIGN KEY (cartao_id, usuario_id)
        REFERENCES cartoes (id, usuario_id) ON DELETE CASCADE;

-- Decisão 44: o filtro da regra é uma conta ou um cartão, nunca os dois. Como o da conta (V6), o do cartão não é uso
-- dele: a FK é CASCADE, de reserva, e a exclusão do cartão tira as regras dele antes, renumerando as outras.
ALTER TABLE regras_categorizacao
    ADD COLUMN cartao_id BIGINT,
    ADD CONSTRAINT ck_regras_conta_ou_cartao CHECK (conta_id IS NULL OR cartao_id IS NULL),
    ADD CONSTRAINT fk_regras_cartao FOREIGN KEY (cartao_id, usuario_id)
        REFERENCES cartoes (id, usuario_id) ON DELETE CASCADE;

-- A mesma condição não se repete, agora por conta e por cartão ("todas as contas e cartões" conta como uma).
DROP INDEX ux_regras_condicao;
CREATE UNIQUE INDEX ux_regras_condicao
    ON regras_categorizacao (usuario_id, natureza, condicao, texto_chave, conta_id, cartao_id) NULLS NOT DISTINCT;
-- Serve à FK de cartões (CASCADE).
CREATE INDEX ix_regras_cartao ON regras_categorizacao (cartao_id, usuario_id) WHERE cartao_id IS NOT NULL;
