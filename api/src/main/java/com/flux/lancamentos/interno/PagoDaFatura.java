package com.flux.lancamentos.interno;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Os pagamentos de uma fatura somados (projeção da consulta, Fatia 05b). {@code soma} é a soma com sinal, negativa.
 * Um record de primeiro nível: o {@code new} do JPQL não resolve record aninhado.
 */
public record PagoDaFatura(Long faturaId, long pagamentos, BigDecimal soma) {

    /** O que saiu das contas para esta fatura, positivo. */
    public BigDecimal pago() {
        return soma.negate().setScale(2, RoundingMode.UNNECESSARY);
    }
}
