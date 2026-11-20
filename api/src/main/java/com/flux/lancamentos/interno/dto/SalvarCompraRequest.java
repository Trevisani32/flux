package com.flux.lancamentos.interno.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.flux.compartilhado.texto.TextoComparavel;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Corpo de POST /api/compras e PUT /api/compras/{id} (decisões 14 a 17). O valor total chega positivo.
 * {@code parcelaInicial} nula é a 1ª; a categoria e a observação são opcionais.
 */
public record SalvarCompraRequest(
        @NotNull(message = "Escolha o cartão.") Long cartaoId,
        @NotNull(message = "Informe a data.") LocalDate data,
        @NotBlank(message = "Escreva a descrição.")
        @Size(max = 255, message = "Use no máximo 255 caracteres.") String descricao,
        @NotNull(message = "Informe o valor.")
        // integer = 100: aqui só as casas decimais contam; o tamanho fica no DecimalMin/DecimalMax.
        @Digits(integer = 100, fraction = 2, message = "Use no máximo 2 casas decimais.")
        @DecimalMin(value = "0.01", message = "O valor precisa ser maior que zero.")
        @DecimalMax(value = "9999999999999.99", message = "Use no máximo 13 dígitos antes da vírgula.")
        BigDecimal valorTotal,
        @NotNull(message = "Use de 1 a 48 parcelas.")
        @Min(value = 1, message = "Use de 1 a 48 parcelas.")
        @Max(value = 48, message = "Use de 1 a 48 parcelas.") Integer parcelas,
        Integer parcelaInicial,
        Long categoriaId,
        @Size(max = 1000, message = "Use no máximo 1.000 caracteres.") String observacao) {

    public SalvarCompraRequest {
        descricao = descricao == null ? null : TextoComparavel.limparEspacos(descricao);
        observacao = observacao == null || observacao.isBlank() ? null : observacao.strip();
        parcelaInicial = parcelaInicial == null ? Integer.valueOf(1) : parcelaInicial;
    }
}
