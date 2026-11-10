package com.flux.lancamentos.interno.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Resposta de GET /api/lancamentos: um mês, já filtrado. {@code mes} é "2026-11";
 * {@code hoje} é a data do Clock, para a tela marcar "hoje" e "amanhã".
 */
public record ListaDoMesResponse(
        String mes,
        LocalDate hoje,
        Totais totais,
        int quantidadeLancamentos,
        int quantidadeTransferencias,
        List<Dia> dias) {

    /**
     * Só receitas e despesas que passaram pelos filtros, sem transferências (decisão 9).
     * {@code saidas} vem negativo, e {@code resultado} = entradas + saídas.
     */
    public record Totais(BigDecimal entradas, int quantidadeEntradas, BigDecimal saidas, int quantidadeSaidas,
                         BigDecimal resultado) {
    }

    /**
     * Os itens de um dia, na ordem da lista. {@code saldoNoFimDoDia} só vem com o filtro de
     * uma conta: o saldo dela no fim do dia, contando todos os lançamentos dela (decisão 7).
     */
    public record Dia(LocalDate data, BigDecimal saldoNoFimDoDia, List<ItemDaLista> itens) {
    }
}
