package com.flux.recorrencias.interno.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OcorrenciaResponse(
        Long recorrenciaId,
        LocalDate periodo,
        LocalDate data,
        LocalDate dataNaLista,
        String descricao,
        String tipo,
        BigDecimal valor,
        BigDecimal valorPrevisto,
        boolean valorVaria,
        Long contaId,
        String contaNome,
        Long cartaoId,
        String cartaoNome,
        String cartaoCor,
        Long categoriaId,
        String categoriaNome,
        String categoriaMaeNome,
        boolean atrasada,
        LocalDate visivelAte) {
}
