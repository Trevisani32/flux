package com.flux.lancamentos.interno.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Corpo de POST /api/cartoes/{id}/faturas/{mes}/pagamentos e PUT /api/pagamentos/{id} (Fatia 05b, decisões 30 e 33).
 * O valor chega positivo; o pagamento é gravado negativo, porque sai da conta (ADR 0002). A observação é opcional.
 */
public record SalvarPagamentoRequest(
        @NotNull(message = "Escolha a conta.") Long contaId,
        @NotNull(message = "Informe a data.") LocalDate data,
        @NotNull(message = "Informe o valor.")
        // integer = 100: aqui só as casas decimais contam; o tamanho fica no DecimalMin/DecimalMax.
        @Digits(integer = 100, fraction = 2, message = "Use no máximo 2 casas decimais.")
        @DecimalMin(value = "0.01", message = "O valor precisa ser maior que zero.")
        @DecimalMax(value = "9999999999999.99", message = "Use no máximo 13 dígitos antes da vírgula.")
        BigDecimal valor,
        @Size(max = 1000, message = "Use no máximo 1.000 caracteres.") String observacao) {

    public SalvarPagamentoRequest {
        observacao = observacao == null || observacao.isBlank() ? null : observacao.strip();
    }
}
