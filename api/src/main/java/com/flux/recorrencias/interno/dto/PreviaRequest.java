package com.flux.recorrencias.interno.dto;

import java.time.LocalDate;

import com.flux.recorrencias.interno.Frequencia;
import jakarta.validation.constraints.NotNull;

public record PreviaRequest(
        @NotNull(message = "Escolha a frequência.") Frequencia frequencia,
        Integer diaDoMes,
        @NotNull(message = "Informe o início.") LocalDate comecaEm,
        LocalDate terminaEm,
        Long cartaoId,
        Long recorrenciaId) {
}
