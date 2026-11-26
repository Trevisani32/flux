package com.flux.recorrencias.interno.dto;

import java.util.List;

public record RecorrenciasResponse(List<RecorrenciaResponse> ativas, List<RecorrenciaResponse> pausadas,
                                   List<RecorrenciaResponse> encerradas, AConfirmarResponse aConfirmar) {
}
