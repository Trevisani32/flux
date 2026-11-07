package com.flux.acesso.interno.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RedefinirSenhaRequest(
        @NotBlank(message = "Informe o link.")
        @Size(max = 100, message = "Link inválido.") String token,
        @NotBlank(message = "Informe a nova senha.")
        @Size(min = 10, max = 72, message = "A nova senha precisa ter entre 10 e 72 caracteres.")
        String novaSenha) {
}
