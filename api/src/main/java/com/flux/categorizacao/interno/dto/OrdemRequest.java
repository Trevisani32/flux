package com.flux.categorizacao.interno.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Corpo de PUT /api/regras/ordem: todos os ids do usuário, na ordem nova (decisão 9); no máximo 200, o limite de regras. */
public record OrdemRequest(
        @NotNull(message = "Envie a ordem das regras.") @Size(max = 200, message = "Envie no máximo 200 regras.")
        List<@NotNull Long> ids) {
}
