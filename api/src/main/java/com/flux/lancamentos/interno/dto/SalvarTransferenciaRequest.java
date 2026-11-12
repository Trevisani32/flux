package com.flux.lancamentos.interno.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.flux.compartilhado.texto.TextoComparavel;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Corpo de POST /api/transferencias e PUT /api/transferencias/{id}. O valor chega positivo.
 * A descrição é opcional: vazia, a transferência grava "Transferência" (decisão 25). Na convertida
 * (Fatia 04c, decisão 31), o PUT repete o valor, as contas e a data da saída, e só a descrição e a
 * observação mudam, nos dois lados.
 */
public record SalvarTransferenciaRequest(
        @NotNull(message = "Informe o valor.")
        @Digits(integer = 100, fraction = 2, message = "Use no máximo 2 casas decimais.")
        @DecimalMin(value = "0.01", message = "O valor precisa ser maior que zero.")
        @DecimalMax(value = "9999999999999.99", message = "Use no máximo 13 dígitos antes da vírgula.")
        BigDecimal valor,
        @NotNull(message = "Informe a data.") LocalDate data,
        @NotNull(message = "Escolha a conta de origem.") Long contaOrigemId,
        @NotNull(message = "Escolha a conta de destino.") Long contaDestinoId,
        @Size(max = 255, message = "Use no máximo 255 caracteres.") String descricao,
        @Size(max = 1000, message = "Use no máximo 1.000 caracteres.") String observacao) {

    public SalvarTransferenciaRequest {
        descricao = descricao == null ? null : TextoComparavel.limparEspacos(descricao);
        observacao = observacao == null || observacao.isBlank() ? null : observacao.strip();
    }
}