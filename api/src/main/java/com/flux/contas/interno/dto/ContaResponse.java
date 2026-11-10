package com.flux.contas.interno.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.flux.contas.interno.CorConta;
import com.flux.contas.interno.TipoConta;

/** Uma conta. {@code saldoAtual} já vem calculado (ADR 0003); {@code arquivadaEm} é nulo nas ativas. */
public record ContaResponse(
        Long id,
        String nome,
        TipoConta tipo,
        BigDecimal saldoInicial,
        LocalDate dataSaldoInicial,
        CorConta cor,
        BigDecimal saldoAtual,
        Instant arquivadaEm) {
}
