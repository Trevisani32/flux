package com.flux.recorrencias.interno.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.flux.recorrencias.interno.Frequencia;
import com.flux.recorrencias.interno.TipoDaRecorrencia;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SalvarRecorrenciaRequest(
        @NotNull(message = "Escolha o tipo.") TipoDaRecorrencia tipo,
        @NotBlank(message = "Escreva a descrição.")
        @Size(max = 255, message = "A descrição pode ter até 255 caracteres.")
        String descricao,
        @NotNull(message = "Informe o valor.") @DecimalMin(value = "0.01", message = "O valor precisa ser maior que zero.")
        @Digits(integer = 13, fraction = 2, message = "Use até 2 casas decimais e até 13 dígitos antes da vírgula.") BigDecimal valor,
        Boolean valorVaria,
        Long categoriaId,
        Long contaId,
        Long cartaoId,
        @NotNull(message = "Escolha a frequência.") Frequencia frequencia,
        Integer diaDoMes,
        @NotNull(message = "Informe o início.") LocalDate comecaEm,
        LocalDate terminaEm) {
}
