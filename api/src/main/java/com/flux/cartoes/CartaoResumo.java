package com.flux.cartoes;

import java.math.BigDecimal;

/**
 * O que outros módulos sabem de um cartão. {@code cor} é o nome da cor da paleta ("AZUL") ou nulo;
 * {@code limite} tem escala 2.
 */
public record CartaoResumo(
        Long id,
        String nome,
        String cor,
        BigDecimal limite,
        int diaFechamento,
        int diaVencimento,
        Long contaPagamentoId,
        boolean arquivado) {

    public CalendarioDoCartao calendario() {
        return new CalendarioDoCartao(diaFechamento, diaVencimento);
    }
}
