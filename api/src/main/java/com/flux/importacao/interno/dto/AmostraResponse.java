package com.flux.importacao.interno.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.flux.importacao.interno.leitura.FormatoCsv;

/**
 * Resposta de POST /api/importacoes/amostra (decisão 12): as primeiras linhas do arquivo,
 * separadas pelo formato, o modelo reconhecido ("NUBANK_CONTA" ou nulo), o formato usado (o
 * enviado ou o sugerido) e a primeira linha depois do cabeçalho, já lida com ele.
 */
public record AmostraResponse(
        List<List<String>> linhas,
        String modelo,
        FormatoCsv formato,
        PrimeiraLinha primeiraLinha,
        String erroDaPrimeiraLinha) {

    public record PrimeiraLinha(LocalDate data, String descricao, BigDecimal valor) {
    }
}
