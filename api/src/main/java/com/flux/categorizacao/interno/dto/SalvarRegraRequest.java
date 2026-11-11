package com.flux.categorizacao.interno.dto;

import com.flux.categorizacao.interno.Condicao;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Corpo de POST e PUT /api/regras. A natureza não vem: é a da categoria (decisão 6). {@code contaId}
 * nulo = todas as contas; {@code descricao} vazia = a regra não troca a descrição.
 */
public record SalvarRegraRequest(
        @NotNull(message = "Escolha a condição.") Condicao condicao,
        @NotBlank(message = "Escreva o texto da regra.") @Size(max = 255, message = "O texto pode ter até 255 caracteres.") String texto,
        Long contaId,
        @NotNull(message = "Escolha a categoria.") Long categoriaId,
        @Size(max = 255, message = "A descrição pode ter até 255 caracteres.") String descricao) {
}
