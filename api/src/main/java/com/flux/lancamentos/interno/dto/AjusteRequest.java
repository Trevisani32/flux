package com.flux.lancamentos.interno.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

/** Corpo de PUT /api/cartoes/{id}/faturas/{mes}/fechamento (decisão 12). */
public record AjusteRequest(@NotNull(message = "Informe a data do fechamento.") LocalDate data) {
}
