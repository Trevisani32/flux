package com.flux.recorrencias.interno.dto;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public record PreviaResponse(LocalDate desde, boolean comecaAntes, List<Item> ocorrencias) {
    public record Item(LocalDate data, boolean ajustada, YearMonth faturaMes, LocalDate faturaVencimento) {
    }
}
