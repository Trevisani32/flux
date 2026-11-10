package com.flux.categorias.interno.dto;

import com.flux.compartilhado.texto.TextoComparavel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpo de PUT /api/categorias/{id}. A natureza não muda; {@code categoriaMaeId} nulo = principal. */
public record EditarCategoriaRequest(
        @NotBlank(message = "Informe o nome.")
        @Size(max = 60, message = "Use no máximo 60 caracteres.") String nome,
        Long categoriaMaeId) {

    public EditarCategoriaRequest {
        nome = nome == null ? null : TextoComparavel.limparEspacos(nome);
    }
}
