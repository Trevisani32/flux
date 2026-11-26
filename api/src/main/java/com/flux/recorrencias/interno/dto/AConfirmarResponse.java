package com.flux.recorrencias.interno.dto;

import java.time.LocalDate;
import java.util.List;

public record AConfirmarResponse(LocalDate ate, long recorrencias, List<OcorrenciaResponse> ocorrencias) {
}
