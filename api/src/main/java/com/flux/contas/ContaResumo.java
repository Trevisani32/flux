package com.flux.contas;

import java.math.BigDecimal;
import java.time.LocalDate;

/** O que outros módulos sabem de uma conta. */
public record ContaResumo(
        Long id,
        String nome,
        BigDecimal saldoInicial,
        LocalDate dataSaldoInicial,
        boolean arquivada) {
}
