package com.flux.lancamentos.interno.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Corpo de POST /api/compras/previa (decisão 18): o que define as parcelas, com as mesmas regras da compra. */
public record PreviaDaCompraRequest(
        @NotNull(message = "Escolha o cartão.") Long cartaoId,
        @NotNull(message = "Informe a data.") LocalDate data,
        @NotNull(message = "Informe o valor.")
        @Digits(integer = 100, fraction = 2, message = "Use no máximo 2 casas decimais.")
        @DecimalMin(value = "0.01", message = "O valor precisa ser maior que zero.")
        @DecimalMax(value = "9999999999999.99", message = "Use no máximo 13 dígitos antes da vírgula.")
        BigDecimal valorTotal,
        @NotNull(message = "Use de 1 a 48 parcelas.")
        @Min(value = 1, message = "Use de 1 a 48 parcelas.")
        @Max(value = 48, message = "Use de 1 a 48 parcelas.") Integer parcelas,
        Integer parcelaInicial) {

    public PreviaDaCompraRequest {
        parcelaInicial = parcelaInicial == null ? Integer.valueOf(1) : parcelaInicial;
    }
}
