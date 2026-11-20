package com.flux.lancamentos.interno.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Uma parcela: o número, o mês da fatura ("2026-12"), o vencimento dela e o valor positivo. */
public record ParcelaDaCompra(int numero, String mes, LocalDate vencimento, BigDecimal valor, boolean noFlux) {
}
