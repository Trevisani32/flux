package com.flux.exportacao.interno.dto;

import com.flux.exportacao.interno.Formato;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ExportarRequest(
        @NotNull(message = "Informe o formato.") Formato formato,
        @NotBlank(message = "Informe a senha atual.") String senhaAtual) {
}
