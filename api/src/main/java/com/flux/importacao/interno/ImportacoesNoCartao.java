package com.flux.importacao.interno;

import com.flux.cartoes.ImportacoesDoCartao;
import org.springframework.stereotype.Component;

/** A importação de fatura faz o cartão deixar de ser vazio mesmo depois de desfeita. */
@Component
class ImportacoesNoCartao implements ImportacoesDoCartao {

    private final ImportacaoRepository importacoes;

    ImportacoesNoCartao(ImportacaoRepository importacoes) {
        this.importacoes = importacoes;
    }

    @Override
    public boolean existe(Long usuarioId, Long cartaoId) {
        return importacoes.existsByUsuarioIdAndCartaoId(usuarioId, cartaoId);
    }
}
