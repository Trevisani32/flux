package com.flux.categorias;

/**
 * O que outros módulos sabem de uma categoria. {@code natureza} é "RECEITA" ou "DESPESA";
 * {@code categoriaMaeId} e {@code nomeDaMae} são nulos na categoria principal.
 */
public record CategoriaResumo(
        Long id,
        String nome,
        String natureza,
        Long categoriaMaeId,
        String nomeDaMae,
        boolean arquivada) {
}
