package com.flux.categorias.interno.dto;

import com.flux.categorias.interno.Natureza;
import com.flux.compartilhado.texto.TextoComparavel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Corpo de POST /api/categorias. Sem {@code categoriaMaeId}, a categoria é principal. */
public record CriarCategoriaRequest(
        @NotBlank(message = "Informe o nome.")
        @Size(max = 60, message = "Use no máximo 60 caracteres.") String nome,
        @NotNull(message = "Escolha a natureza.") Natureza natureza,
        Long categoriaMaeId) {

    public CriarCategoriaRequest {
        nome = nome == null ? null : TextoComparavel.limparEspacos(nome);
    }
}
