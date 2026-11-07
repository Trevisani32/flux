package com.flux.acesso.interno.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpo de "reenviar confirmação" e de "esqueci a senha". */
public record EmailRequest(
        @NotBlank(message = "Informe o e-mail.")
        @Email(message = "Informe um e-mail válido.")
        @Size(max = 255, message = "O e-mail pode ter no máximo 255 caracteres.") String email) {

    public EmailRequest {
        email = email == null ? null : email.strip();
    }
}
