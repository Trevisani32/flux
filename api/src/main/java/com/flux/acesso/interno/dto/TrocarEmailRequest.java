package com.flux.acesso.interno.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TrocarEmailRequest(
        @NotBlank(message = "Informe o novo e-mail.")
        @Email(message = "Informe um e-mail válido.")
        @Size(max = 255, message = "O e-mail pode ter no máximo 255 caracteres.") String novoEmail,
        @NotBlank(message = "Informe a senha atual.") String senhaAtual) {

    public TrocarEmailRequest {
        novoEmail = novoEmail == null ? null : novoEmail.strip();
    }
}
