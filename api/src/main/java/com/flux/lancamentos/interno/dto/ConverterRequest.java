package com.flux.lancamentos.interno.dto;

import com.flux.compartilhado.texto.TextoComparavel;
import jakarta.validation.constraints.Size;

/**
 * Corpo de POST /api/lancamentos/{id}/converter (Fatia 04c). O outro lado é um par ({@code parId},
 * decisão 27) ou uma conta onde ele é lançado na hora ({@code contaId}, decisão 28): um dos dois, nunca
 * os dois. A descrição vale para os dois lados; vazia, fica "Transferência" (decisão 29).
 */
public record ConverterRequest(
        Long parId,
        Long contaId,
        @Size(max = 255, message = "Use no máximo 255 caracteres.") String descricao) {

    public ConverterRequest {
        descricao = descricao == null ? null : TextoComparavel.limparEspacos(descricao);
    }
}