package com.flux.recorrencias.interno.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CandidatosResponse(LocalDate de, LocalDate ate, Candidato sugerido, List<Candidato> candidatos) {

    public record Candidato(Long id, String alvo, LocalDate data, String descricao, BigDecimal valor, String origem,
                            String categoriaNome, String categoriaMaeNome) {
    }
}
