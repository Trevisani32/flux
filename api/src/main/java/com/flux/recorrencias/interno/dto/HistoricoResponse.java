package com.flux.recorrencias.interno.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public record HistoricoResponse(Long recorrenciaId, String descricao, String frequencia, LocalDate desde, List<Item> itens) {

    public record Item(LocalDate periodo, LocalDate dataPrevista, String situacao, BigDecimal valor, LocalDate data,
                       Long lancamentoId, Long compraId, YearMonth mesNaLista, Long contaId, Long cartaoId) {
    }
}
