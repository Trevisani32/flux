package com.flux.lancamentos.interno;

/** Uma compra ou estorno importado da fatura, com a indicação de que algum texto mudou depois da importação. */
public record CompraDaImportacao(Long importacaoId, Long compraId, Long editada) {
}
