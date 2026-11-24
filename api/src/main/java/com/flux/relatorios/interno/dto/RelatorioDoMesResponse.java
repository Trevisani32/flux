package com.flux.relatorios.interno.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.flux.relatorios.interno.Comparacao;

/**
 * Resposta de GET /api/relatorios/mes (Fatia 06a, ADR 0020). Os meses são "2026-11". Os valores vêm prontos, com escala 2
 * (ADR 0002): receitas, despesas e gastos sem sinal; o resultado e os saldos com sinal.
 *
 * <ul>
 *   <li>{@code mes} é o mês mostrado (o pedido, ou o atual quando o pedido está fora da faixa); {@code primeiroMes}, o do
 *       primeiro dado, é nulo só quando o usuário não tem nenhuma conta, e aí o resto vem zerado e vazio;</li>
 *   <li>{@code mesesDaMedia}: de 0 a 3 meses, do mais antigo ao mais novo; vazia, {@code media} e {@code comparacao} são
 *       nulas em toda a resposta;</li>
 *   <li>{@code categorias}: as despesas por categoria principal, já em ordem; {@code meses}: de 1 a 12, do mais antigo ao
 *       mês mostrado, para os dois gráficos.</li>
 * </ul>
 */
public record RelatorioDoMesResponse(
        String mes,
        String mesAtual,
        String primeiroMes,
        LocalDate hoje,
        List<String> mesesDaMedia,
        Saldo saldo,
        Total receitas,
        Total despesas,
        BigDecimal resultado,
        List<Categoria> categorias,
        List<Mes> meses) {

    /**
     * O saldo total em {@code data}: hoje no mês atual ({@code hoje} verdadeiro), o último dia num mês passado. Entram as
     * contas com o saldo inicial até a data e não arquivadas até ela. {@code total} e {@code investimentos} são nulos
     * quando a data é anterior ao primeiro saldo inicial do usuário.
     */
    public record Saldo(LocalDate data, boolean hoje, BigDecimal total, BigDecimal investimentos, int contas,
                        int contasDeInvestimento) {
    }

    /** As receitas ou as despesas do mês. {@code noCartao} conta as de fatura (0 nas receitas). */
    public record Total(BigDecimal valor, long quantidade, long noCartao, BigDecimal media, Comparacao comparacao) {
    }

    /**
     * Uma linha dos gastos por categoria. {@code id} nulo é "Sem categoria"; {@code direto} é o item "Direto em
     * Alimentação", com o id da principal. {@code subcategorias} vem vazia quando nenhuma subcategoria teve gasto.
     */
    public record Categoria(Long id, String nome, boolean arquivada, boolean direto, BigDecimal valor, BigDecimal media,
                            Comparacao comparacao, List<Categoria> subcategorias) {
    }

    /**
     * Um mês dos gráficos. {@code dataDoSaldo} é o último dia do mês (hoje, no mês atual); {@code saldo} é nulo antes do
     * primeiro saldo inicial.
     */
    public record Mes(String mes, BigDecimal receitas, BigDecimal despesas, BigDecimal resultado, LocalDate dataDoSaldo,
                      BigDecimal saldo) {
    }
}
