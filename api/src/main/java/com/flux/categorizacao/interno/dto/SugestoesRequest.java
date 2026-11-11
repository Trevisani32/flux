package com.flux.categorizacao.interno.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Corpo de POST /api/regras/sugestoes (decisões 20 a 22): importados que ganharam categoria à mão, na
 * revisão ou no diálogo do lançamento.
 */
public record SugestoesRequest(
        @NotNull(message = "Envie os itens.") @Size(max = 3000, message = "Envie no máximo 3.000 itens.")
        List<@Valid @NotNull Item> itens) {

    /** {@code descricao}: a que o usuário vê, quando é diferente da do extrato. */
    public record Item(
            @NotNull(message = "Informe a conta.") Long contaId,
            @NotBlank(message = "Informe a descrição do extrato.") @Size(max = 255, message = "A descrição pode ter até 255 caracteres.")
            String descricaoBanco,
            @Size(max = 255, message = "A descrição pode ter até 255 caracteres.") String descricao,
            @NotNull(message = "Informe a categoria.") Long categoriaId) {
    }
}
