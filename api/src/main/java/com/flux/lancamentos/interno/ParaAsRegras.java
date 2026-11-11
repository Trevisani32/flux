package com.flux.lancamentos.interno;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Uma receita ou despesa lida para as regras, só com as colunas que elas usam. Um record de primeiro
 * nível: o {@code new} do JPQL não resolve record aninhado.
 */
public record ParaAsRegras(Long id, Long contaId, LocalDate data, BigDecimal valor, String descricao, String descricaoBanco,
                           Long categoriaId) {
}
