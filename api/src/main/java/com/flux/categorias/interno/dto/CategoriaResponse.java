package com.flux.categorias.interno.dto;

import java.time.Instant;
import java.util.List;

import com.flux.categorias.interno.Natureza;

/**
 * Uma categoria. Numa principal, {@code subcategorias} traz as que estão no mesmo estado
 * dela (ativas numa ativa, arquivadas numa arquivada), em ordem alfabética; numa
 * subcategoria, vem vazia. {@code nomeDaMae} só existe nas subcategorias.
 */
public record CategoriaResponse(
        Long id,
        String nome,
        Natureza natureza,
        Long categoriaMaeId,
        String nomeDaMae,
        Instant arquivadaEm,
        List<CategoriaResponse> subcategorias) {
}
