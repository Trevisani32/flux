package com.flux.categorizacao.interno.dto;

/** Quantos lançamentos ganharam categoria, contados dentro da trava (pode diferir da prévia). */
public record AplicadosResponse(int categorizados) {
}
