package com.flux.lancamentos.interno.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Resposta de POST /api/compras/previa (decisão 18): todas as parcelas da divisão, com {@code noFlux} falso nas
 * que ficam fora (a compra em andamento, decisão 17), e a fatura da 1ª parcela, mesmo fora do Flux. Fatia 05b (decisão
 * 38): {@code disponivelDepois}, o limite disponível do cartão com as parcelas no Flux desta compra somadas às faturas
 * delas (negativo: acima do limite). {@code avisoDaFaturaPaga} (decisão 35, dono em 2026-11-20): a frase que avisa, antes de
 * salvar, que uma parcela fica numa fatura paga (a que já estava paga e continua, ou a que passa a ser paga com ela) e que
 * o lançamento fica travado; nulo quando nenhuma fica.
 */
public record PreviaDaCompraResponse(List<ParcelaDaCompra> parcelas, Fatura primeiraFatura, BigDecimal disponivelDepois,
                                     String avisoDaFaturaPaga) {

    /** {@code mes} é "2026-12"; {@code situacao}, FUTURA, ABERTA ou FECHADA. */
    public record Fatura(String mes, LocalDate fechamento, LocalDate vencimento, String situacao) {
    }
}
