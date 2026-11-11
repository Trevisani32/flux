package com.flux.importacao.interno.dto;

import java.math.BigDecimal;

/**
 * Resposta de POST /api/importacoes (decisão 27). {@code entraramPorOutra}: linhas marcadas que
 * outra importação gravou entre a revisão e a confirmação; {@code jaImportadas}: as outras linhas já
 * importadas, sem essas (o mockup diz "3 linhas já tinham entrado por outra importação" e "34 já
 * tinham sido importadas" sem repetir as 3). {@code mesDaUltimaLinha} ("2026-11") é o mês do "Ver
 * lançamentos". Sem nenhuma linha gravada, {@code importacaoId} é nulo e nada fica no histórico.
 */
public record ResultadoResponse(
        Long importacaoId,
        Long contaId,
        String contaNome,
        int importadas,
        int desmarcadas,
        int jaImportadas,
        int deFora,
        int entraramPorOutra,
        BigDecimal saldoDeHoje,
        String mesDaUltimaLinha) {
}
