package com.flux.acesso.interno.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfirmarEmailRequest(
        @NotBlank(message = "Informe o link.")
        @Size(max = 100, message = "Link inválido.") String token) {
}
