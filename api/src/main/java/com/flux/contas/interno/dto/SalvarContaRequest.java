package com.flux.contas.interno.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.flux.compartilhado.texto.TextoComparavel;
import com.flux.contas.interno.CorConta;
import com.flux.contas.interno.TipoConta;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Corpo de POST /api/contas e PUT /api/contas/{id}. A cor é opcional. */
public record SalvarContaRequest(
        @NotBlank(message = "Informe o nome.")
        @Size(max = 60, message = "Use no máximo 60 caracteres.") String nome,
        @NotNull(message = "Escolha o tipo.") TipoConta tipo,
        @NotNull(message = "Informe o saldo inicial.")
        // integer = 100: aqui só as casas decimais contam; o tamanho fica no DecimalMin/DecimalMax.
        @Digits(integer = 100, fraction = 2, message = "Use no máximo 2 casas decimais.")
        @DecimalMin(value = "-9999999999999.99", message = "Use no máximo 13 dígitos antes da vírgula.")
        @DecimalMax(value = "9999999999999.99", message = "Use no máximo 13 dígitos antes da vírgula.")
        BigDecimal saldoInicial,
        @NotNull(message = "Informe a data do saldo inicial.") LocalDate dataSaldoInicial,
        CorConta cor) {

    public SalvarContaRequest {
        nome = nome == null ? null : TextoComparavel.limparEspacos(nome);
    }
}
