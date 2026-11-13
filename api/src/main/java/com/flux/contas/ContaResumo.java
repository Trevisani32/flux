package com.flux.contas;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * O que outros módulos sabem de uma conta. {@code tipo} é "CORRENTE", "POUPANCA", "CARTEIRA" ou
 * "INVESTIMENTO" (Fatia 05a: só as três primeiras pagam fatura de cartão).
 */
public record ContaResumo(
        Long id,
        String nome,
        BigDecimal saldoInicial,
        LocalDate dataSaldoInicial,
        boolean arquivada,
        String tipo) {
}
