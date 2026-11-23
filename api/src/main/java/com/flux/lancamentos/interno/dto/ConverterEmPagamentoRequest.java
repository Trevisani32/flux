package com.flux.lancamentos.interno.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Corpo de POST /api/lancamentos/{id}/converter-em-pagamento (Fatia 05b, decisão 37): o cartão e o mês da fatura
 * ("2026-11") que a despesa importada paga.
 */
public record ConverterEmPagamentoRequest(
        @NotNull(message = "Escolha o cartão.") Long cartaoId,
        @NotBlank(message = "Escolha a fatura.") String mes) {
}
