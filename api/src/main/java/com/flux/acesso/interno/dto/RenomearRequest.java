package com.flux.acesso.interno.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenomearRequest(
        @NotBlank(message = "Informe o nome.")
        @Size(max = 120, message = "O nome pode ter no máximo 120 caracteres.") String nome) {

    public RenomearRequest {
        nome = nome == null ? null : nome.strip();
    }
}
