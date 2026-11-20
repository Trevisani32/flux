package com.flux.lancamentos.interno;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Os lançamentos de uma fatura somados (projeção da consulta). {@code soma} é a soma com sinal;
 * {@code estornos}, a soma dos positivos (nula sem estorno). Um record de primeiro nível: o {@code new}
 * do JPQL não resolve record aninhado.
 */
public record TotalDaFatura(Long faturaId, long lancamentos, BigDecimal soma, long compras, BigDecimal estornos) {

    /** Decisão 11: total = compras − estornos = −Σ dos lançamentos. Negativo é crédito. */
    public BigDecimal total() {
        return soma.negate().setScale(2, RoundingMode.UNNECESSARY);
    }

    public BigDecimal somaDosEstornos() {
        return (estornos == null ? BigDecimal.ZERO : estornos).setScale(2, RoundingMode.UNNECESSARY);
    }

    /** As compras e as parcelas, positivo. */
    public BigDecimal somaDasCompras() {
        return somaDosEstornos().subtract(soma).setScale(2, RoundingMode.UNNECESSARY);
    }

    public long quantidadeDeEstornos() {
        return lancamentos - compras;
    }
}
