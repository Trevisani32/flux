package com.flux.contas.interno.dto;

import java.math.BigDecimal;
import java.util.List;

/** Resposta de GET /api/contas: as duas listas em ordem alfabética e os totais das ativas. */
public record ContasResponse(List<ContaResponse> ativas, List<ContaResponse> arquivadas, Totais totais) {

    /** Somas dos saldos atuais das contas ativas. Disponível = corrente, poupança e carteira. */
    public record Totais(BigDecimal geral, BigDecimal disponivel, BigDecimal investimentos) {
    }
}
