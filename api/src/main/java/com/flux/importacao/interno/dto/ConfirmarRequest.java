package com.flux.importacao.interno.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A parte JSON "pedido" de POST /api/importacoes, que vai junto com o arquivo de novo (decisão
 * 13): a conta, o hash da revisão e as linhas marcadas, cada uma com a categoria escolhida.
 */
public record ConfirmarRequest(
        @NotNull(message = "Escolha a conta.") Long contaId,
        @NotBlank(message = "Escolha o arquivo de novo.") String hash,
        @NotEmpty(message = "Marque ao menos uma linha.") @Size(max = 3000, message = "Marque no máximo 3.000 linhas.")
        List<@Valid @NotNull Escolha> linhas) {

    public record Escolha(@NotNull(message = "Informe a linha.") Integer linha, Long categoriaId) {
    }
}
