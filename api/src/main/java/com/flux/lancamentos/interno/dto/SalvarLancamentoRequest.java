package com.flux.lancamentos.interno.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.flux.compartilhado.texto.TextoComparavel;
import com.flux.lancamentos.interno.TipoLancamento;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Corpo de POST /api/lancamentos e PUT /api/lancamentos/{id}. O valor chega positivo, e o
 * tipo dá o sinal (ADR 0002). A categoria e a observação são opcionais.
 */
public record SalvarLancamentoRequest(
        @NotNull(message = "Escolha o tipo.") TipoLancamento tipo,
        @NotNull(message = "Informe o valor.")
        // integer = 100: aqui só as casas decimais contam; o tamanho fica no DecimalMin/DecimalMax.
        @Digits(integer = 100, fraction = 2, message = "Use no máximo 2 casas decimais.")
        @DecimalMin(value = "0.01", message = "O valor precisa ser maior que zero.")
        @DecimalMax(value = "9999999999999.99", message = "Use no máximo 13 dígitos antes da vírgula.")
        BigDecimal valor,
        @NotNull(message = "Informe a data.") LocalDate data,
        @NotBlank(message = "Informe a descrição.")
        @Size(max = 255, message = "Use no máximo 255 caracteres.") String descricao,
        @NotNull(message = "Escolha a conta.") Long contaId,
        Long categoriaId,
        @Size(max = 1000, message = "Use no máximo 1.000 caracteres.") String observacao) {

    public SalvarLancamentoRequest {
        descricao = descricao == null ? null : TextoComparavel.limparEspacos(descricao);
        observacao = observacao == null || observacao.isBlank() ? null : observacao.strip();
    }
}
