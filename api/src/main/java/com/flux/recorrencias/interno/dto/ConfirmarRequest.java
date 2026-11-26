package com.flux.recorrencias.interno.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ConfirmarRequest(
        @NotNull(message = "Informe a data.") LocalDate data,
        @NotNull(message = "Informe o valor.") @DecimalMin(value = "0.01", message = "O valor precisa ser maior que zero.")
        @Digits(integer = 13, fraction = 2, message = "Use até 2 casas decimais e até 13 dígitos antes da vírgula.") BigDecimal valor,
        @Size(max = 1000, message = "Use no máximo 1.000 caracteres.") String observacao) {
}
