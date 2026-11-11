package com.flux.categorizacao.interno.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Quantos lançamentos (receitas e despesas não excluídas de contas ativas) a condição pega, sem olhar
 * as outras regras, quantos deles estão sem categoria e até 3 exemplos, os mais recentes primeiro.
 */
public record PreviaDaRegraResponse(int casam, int semCategoria, List<Exemplo> exemplos) {

    /** {@code descricao}: a que a regra olha (a do extrato, no importado). */
    public record Exemplo(LocalDate data, String descricao, BigDecimal valor) {
    }
}
