package com.flux.categorias.interno.dto;

import java.util.List;

/** Resposta de GET /api/categorias: as duas naturezas de uma vez. */
public record CategoriasResponse(Grupo despesas, Grupo receitas) {

    /**
     * {@code ativas}: as principais ativas, cada uma com as subcategorias ativas.
     * {@code arquivadas}: as principais arquivadas (com as subcategorias arquivadas dentro)
     * e as subcategorias arquivadas de uma principal ativa, todas na mesma ordem alfabética.
     */
    public record Grupo(List<CategoriaResponse> ativas, List<CategoriaResponse> arquivadas) {
    }
}
