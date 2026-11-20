package com.flux.lancamentos.interno.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Uma compra ou um estorno (decisões 14 a 20), resposta de criar, buscar e editar. {@code tipo} é COMPRA ou
 * ESTORNO; {@code valorTotal} é positivo; {@code somenteLeitura}, o cartão arquivado (decisão 5);
 * {@code parcelasNoFlux}, as parcelas gravadas, a primeira antes (o estorno tem uma).
 */
public record CompraResponse(
        Long id,
        String tipo,
        Long cartaoId,
        String cartaoNome,
        boolean somenteLeitura,
        LocalDate data,
        String descricao,
        BigDecimal valorTotal,
        int parcelas,
        int parcelaInicial,
        Long categoriaId,
        String categoriaNome,
        String categoriaMaeNome,
        boolean categoriaArquivada,
        String observacao,
        List<ParcelaDaCompra> parcelasNoFlux) {
}
