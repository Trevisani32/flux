package com.flux.lancamentos.interno.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Uma ocorrência calculada que aparece separada dos lançamentos confirmados do mês. */
public record PrevistoDaLista(
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
