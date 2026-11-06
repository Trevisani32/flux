package com.flux.acesso.interno.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EntrarRequest(
        @NotBlank(message = "Informe o e-mail.")
        @Email(message = "Informe um e-mail válido.")
        @Size(max = 255, message = "O e-mail pode ter no máximo 255 caracteres.") String email,
        @NotBlank(message = "Informe a senha.") String senha) {

    public EntrarRequest {
        email = email == null ? null : email.strip();
    }
}
