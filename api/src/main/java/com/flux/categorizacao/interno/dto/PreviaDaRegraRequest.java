package com.flux.categorizacao.interno.dto;

import com.flux.categorizacao.interno.Condicao;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Corpo de POST /api/regras/previa (decisão 12): a regra que está sendo escrita. Sem categoria, a
 * prévia conta receitas e despesas; com ela, só a natureza dela.
 */
public record PreviaDaRegraRequest(
        @NotNull(message = "Escolha a condição.") Condicao condicao,
        @NotBlank(message = "Escreva o texto da regra.") @Size(max = 255, message = "O texto pode ter até 255 caracteres.") String texto,
        Long contaId,
        Long categoriaId) {
}
