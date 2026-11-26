package com.flux.recorrencias.interno.dto;

import java.time.LocalDate;
import java.time.YearMonth;

public record ConfirmacaoResponse(Long recorrenciaId, LocalDate periodo, Long lancamentoId, Long compraId,
                                  LocalDate data, YearMonth faturaMes, String cartaoNome) {
}
