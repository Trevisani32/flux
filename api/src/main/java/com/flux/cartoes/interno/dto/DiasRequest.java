package com.flux.cartoes.interno.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Corpo de POST /api/cartoes/{id}/previa-dos-dias: os dias que o diálogo de edição tem agora. */
public record DiasRequest(
        @NotNull(message = "Escolha um dia de 1 a 31.")
        @Min(value = 1, message = "Escolha um dia de 1 a 31.")
        @Max(value = 31, message = "Escolha um dia de 1 a 31.") Integer diaFechamento,
        @NotNull(message = "Escolha um dia de 1 a 31.")
        @Min(value = 1, message = "Escolha um dia de 1 a 31.")
        @Max(value = 31, message = "Escolha um dia de 1 a 31.") Integer diaVencimento) {
}
