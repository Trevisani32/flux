package com.flux.cartoes.interno.dto;

import java.time.LocalDate;

/**
 * Decisão 7: o que mudar os dias faria. {@code troca} vem quando o cartão tem lançamento ou ajuste de fechamento e os
 * dias mudam; {@code valemJa}, quando ele não tem nenhum dos dois e os dias mudam: aí os dias novos valem
 * já, inclusive na fatura aberta. Com os dias iguais, nenhum dos dois. Os meses são "2026-12".
 */
public record PreviaDosDiasResponse(Troca troca, boolean valemJa) {

    /** A aberta, que continua como está, e a primeira fatura com os dias novos. */
    public record Troca(String abertaMes, LocalDate abertaFechamento, String primeiraMes, LocalDate primeiraInicio,
                        LocalDate primeiraFechamento, LocalDate primeiraVencimento) {
    }
}
