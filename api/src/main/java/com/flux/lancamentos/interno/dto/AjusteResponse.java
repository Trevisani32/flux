package com.flux.lancamentos.interno.dto;

/**
 * Resposta do ajuste e da volta ao normal (decisão 12): quantas compras e quantos estornos mudaram de fatura, e a
 * fatura de novo.
 */
public record AjusteResponse(int comprasMovidas, int estornosMovidos, FaturaResponse fatura) {
}
