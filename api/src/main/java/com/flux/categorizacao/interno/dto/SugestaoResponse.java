package com.flux.categorizacao.interno.dto;

/**
 * Uma regra pronta para o diálogo "Nova regra" (decisão 21): "contém", o texto sugerido, a categoria
 * escolhida e a descrição legível, se o usuário mudou a descrição. {@code deDescricao}: a do extrato
 * de onde a sugestão saiu.
 */
public record SugestaoResponse(
        String condicao,
        String texto,
        Long categoriaId,
        String categoriaNome,
        String categoriaMaeNome,
        String natureza,
        String descricao,
        String deDescricao) {
}
