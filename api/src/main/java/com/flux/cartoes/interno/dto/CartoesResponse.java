package com.flux.cartoes.interno.dto;

import java.util.List;

/** Resposta de GET /api/cartoes: as duas listas em ordem alfabética (decisão 6). */
public record CartoesResponse(List<CartaoResponse> ativos, List<CartaoResponse> arquivados) {
}
