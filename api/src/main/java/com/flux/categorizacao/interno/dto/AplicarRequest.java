package com.flux.categorizacao.interno.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Corpo de POST /api/regras/aplicacao: as regras marcadas na prévia. Um lançamento só entra se a
 * primeira regra que casa com ele é uma delas; desmarcar uma regra não joga os lançamentos dela na de
 * baixo (decisão 17). No máximo 200, o limite de regras.
 */
public record AplicarRequest(
        @NotEmpty(message = "Escolha ao menos uma regra.") @Size(max = 200, message = "Envie no máximo 200 regras.")
        List<@NotNull Long> regras) {
}
