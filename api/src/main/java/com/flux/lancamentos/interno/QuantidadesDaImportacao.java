package com.flux.lancamentos.interno;

/** Editados e ignorados de uma importação, somados no banco. */
public record QuantidadesDaImportacao(Long importacaoId, Long editados, Long excluidos) {
}
