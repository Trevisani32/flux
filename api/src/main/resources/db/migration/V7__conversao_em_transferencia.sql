-- Fatia 04c: conversão de importados em transferência (ADR 0016).
-- Um lado de transferência nunca fica excluído (ignorado): só um lançamento que não está excluído
-- vira lado, e excluir um lado pede desfazer a transferência antes (decisão 33). Desfazer solta os
-- lados antes de apagar o vínculo, porque o CASCADE de fk_lancamentos_transferencia apagaria os dois.
ALTER TABLE lancamentos
    ADD CONSTRAINT ck_lancamentos_lado_nao_ignorado CHECK (transferencia_id IS NULL OR ignorado_em IS NULL);