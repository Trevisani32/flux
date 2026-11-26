package com.flux.recorrencias.interno.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RecorrenciaResponse(
        Long id,
        String descricao,
        String tipo,
        BigDecimal valor,
        BigDecimal valorComSinal,
        boolean valorVaria,
        Long categoriaId,
        String categoriaNome,
        String categoriaMaeNome,
        boolean categoriaArquivada,
        Long contaId,
        String contaNome,
        Long cartaoId,
        String cartaoNome,
        String cartaoCor,
        String frequencia,
        Integer diaDoMes,
        LocalDate comecaEm,
        LocalDate terminaEm,
        LocalDate ocorrenciasDesde,
        String situacao,
        String motivoDaPausa,
        LocalDate proxima,
        int atrasadas,
        int confirmadas) {
}
