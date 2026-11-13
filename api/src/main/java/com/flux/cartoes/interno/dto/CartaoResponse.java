package com.flux.cartoes.interno.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.flux.cartoes.interno.CorCartao;

/**
 * Um cartão (decisões 1 a 6, 26 e 29). {@code arquivadoEm} é nulo no ativo. {@code faturaAberta} é a
 * fatura que contém hoje, e {@code proximaFatura}, a seguinte; {@code emAberto} soma os totais da aberta e
 * das futuras, e {@code lancamentosEmAberto} conta os lançamentos delas (a confirmação de arquivar).
 * Os valores vêm prontos (ADR 0002): o total positivo é a pagar, o negativo é crédito.
 */
public record CartaoResponse(
        Long id,
        String nome,
        BigDecimal limite,
        int diaFechamento,
        int diaVencimento,
        Long contaPagamentoId,
        String contaPagamentoNome,
        boolean contaPagamentoArquivada,
        CorCartao cor,
        Instant arquivadoEm,
        Fatura faturaAberta,
        Fatura proximaFatura,
        BigDecimal emAberto,
        long lancamentosEmAberto) {

    /** {@code mes} é "2026-12". */
    public record Fatura(String mes, LocalDate inicio, LocalDate fechamento, LocalDate vencimento, BigDecimal total,
                         long lancamentos) {
    }
}
