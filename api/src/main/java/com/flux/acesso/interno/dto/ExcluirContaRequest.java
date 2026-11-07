package com.flux.acesso.interno.dto;

import jakarta.validation.constraints.NotBlank;

public record ExcluirContaRequest(@NotBlank(message = "Informe a senha atual.") String senhaAtual) {
}
