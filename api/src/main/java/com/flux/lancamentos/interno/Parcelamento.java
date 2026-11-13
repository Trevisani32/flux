package com.flux.lancamentos.interno;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Decisão 15 (ADR 0017): cada parcela é o total ÷ N truncado no centavo, e o resto vai para a 1ª. R$ 100,00
 * em 3x dá 33,34 + 33,33 + 33,33. Nenhuma parcela é zero: o total precisa ter ao menos 1 centavo por parcela.
 */
final class Parcelamento {

    static final int MAXIMO_DE_PARCELAS = 48;

    private Parcelamento() {
    }

    /** O menor total para {@code parcelas}: 1 centavo por parcela (R$ 0,48 em 48x). */
    static BigDecimal minimo(int parcelas) {
        return BigDecimal.valueOf(parcelas, 2);
    }

    /** As {@code parcelas} parcelas de {@code total} (escala 2, ao menos o mínimo), a 1ª primeiro. */
    static List<BigDecimal> dividir(BigDecimal total, int parcelas) {
        if (parcelas < 1 || parcelas > MAXIMO_DE_PARCELAS || total.compareTo(minimo(parcelas)) < 0) {
            throw new IllegalArgumentException("Divisão inválida: " + total + " em " + parcelas + "x.");
        }
        BigDecimal parcela = total.divide(BigDecimal.valueOf(parcelas), 2, RoundingMode.DOWN);
        BigDecimal primeira = total.subtract(parcela.multiply(BigDecimal.valueOf(parcelas - 1L))).setScale(2, RoundingMode.UNNECESSARY);
        List<BigDecimal> valores = new ArrayList<>(parcelas);
        valores.add(primeira);
        for (int numero = 2; numero <= parcelas; numero++) {
            valores.add(parcela);
        }
        return valores;
    }
}
