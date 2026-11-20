package com.flux.lancamentos.interno.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Resposta de POST /api/compras/previa (decisão 18): todas as parcelas da divisão, com {@code noFlux} falso nas
 * que ficam fora (a compra em andamento, decisão 17), e a fatura da 1ª parcela, mesmo fora do Flux.
 */
public record PreviaDaCompraResponse(List<ParcelaDaCompra> parcelas, Fatura primeiraFatura) {

    /** {@code mes} é "2026-12"; {@code situacao}, FUTURA, ABERTA ou FECHADA. */
    public record Fatura(String mes, LocalDate fechamento, LocalDate vencimento, String situacao) {
    }
}
