package com.flux.cartoes.interno.dto;

import java.math.BigDecimal;

import com.flux.cartoes.interno.CorCartao;
import com.flux.compartilhado.texto.TextoComparavel;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Corpo de POST /api/cartoes e PUT /api/cartoes/{id} (decisões 1 a 4). A cor é opcional. */
public record SalvarCartaoRequest(
        @NotBlank(message = "Escreva o nome do cartão.")
        @Size(max = 60, message = "O nome pode ter até 60 caracteres.") String nome,
        @NotNull(message = "Informe o limite.")
        // integer = 100: aqui só as casas decimais contam; o tamanho fica no DecimalMax.
        @Digits(integer = 100, fraction = 2, message = "Use até 2 casas decimais e até 13 dígitos antes da vírgula.")
        @DecimalMin(value = "0.01", message = "O limite precisa ser maior que zero.")
        @DecimalMax(value = "9999999999999.99", message = "Use até 2 casas decimais e até 13 dígitos antes da vírgula.")
        BigDecimal limite,
        @NotNull(message = "Escolha um dia de 1 a 31.")
        @Min(value = 1, message = "Escolha um dia de 1 a 31.")
        @Max(value = 31, message = "Escolha um dia de 1 a 31.") Integer diaFechamento,
        @NotNull(message = "Escolha um dia de 1 a 31.")
        @Min(value = 1, message = "Escolha um dia de 1 a 31.")
        @Max(value = 31, message = "Escolha um dia de 1 a 31.") Integer diaVencimento,
        @NotNull(message = "Escolha a conta de pagamento.") Long contaPagamentoId,
        CorCartao cor) {

    public SalvarCartaoRequest {
        nome = nome == null ? null : TextoComparavel.limparEspacos(nome);
    }
}
