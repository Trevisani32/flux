package com.flux.acesso.interno.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TrocarSenhaRequest(
        @NotBlank(message = "Informe a senha atual.") String senhaAtual,
        @NotBlank(message = "Informe a nova senha.")
        @Size(min = 10, max = 72, message = "A nova senha precisa ter entre 10 e 72 caracteres.")
        String novaSenha) {
}
