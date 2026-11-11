package com.flux.importacao.interno.dto;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Um item do histórico (decisão 33). {@code deFora}: antes do saldo inicial e fora da importação;
 * {@code editados} e {@code excluidos} contam os lançamentos dela hoje (os excluídos são os
 * ignorados). Desfeita, {@code desfeitaEm} vem preenchido.
 */
public record ImportacaoResponse(
        Long id,
        String nomeArquivo,
        Long contaId,
        String contaNome,
        boolean contaArquivada,
        Instant criadaEm,
        LocalDate dataInicio,
        LocalDate dataFim,
        int importadas,
        int jaImportadas,
        int desmarcadas,
        int deFora,
        long editados,
        long excluidos,
        Instant desfeitaEm) {
}
