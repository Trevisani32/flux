package com.flux.categorizacao.interno.dto;

/**
 * Uma regra como a tela mostra. {@code contaId} nulo = todas as contas. {@code pausada}: a categoria
 * ou a conta está arquivada, e a regra não age (decisões 23 e 24).
 */
public record RegraResponse(
        Long id,
        int posicao,
        String condicao,
        String texto,
        Long contaId,
        String contaNome,
        boolean contaArquivada,
        String natureza,
        Long categoriaId,
        String categoriaNome,
        String categoriaMaeNome,
        boolean categoriaArquivada,
        String descricao,
        boolean pausada) {
}
