package com.flux.acesso.interno.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CadastrarRequest(
        @NotBlank(message = "Informe o nome.")
        @Size(max = 120, message = "O nome pode ter no máximo 120 caracteres.") String nome,
        @NotBlank(message = "Informe o e-mail.")
        @Email(message = "Informe um e-mail válido.")
        @Size(max = 255, message = "O e-mail pode ter no máximo 255 caracteres.") String email,
        @NotBlank(message = "Informe a senha.")
        @Size(min = 10, max = 72, message = "A senha precisa ter entre 10 e 72 caracteres.") String senha,
        @NotNull(message = "Aceite os termos de uso e a política de privacidade para continuar.")
        @AssertTrue(message = "Aceite os termos de uso e a política de privacidade para continuar.")
        Boolean aceiteDosTermos) {

    public CadastrarRequest {
        nome = nome == null ? null : nome.strip();
        email = email == null ? null : email.strip();
    }
}
