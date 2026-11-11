package com.flux.lancamentos.interno.dto;

/**
 * Corpo opcional de POST /api/lancamentos/{id}/restaurar: a categoria que o importado tinha
 * antes de ser excluído (o "Desfazer" do aviso curto, decisão 31). Sem ela, volta sem categoria.
 */
public record RestaurarRequest(Long categoriaId) {
}
