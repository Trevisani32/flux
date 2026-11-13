package com.flux.lancamentos.interno.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Resposta de GET /api/cartoes/{id}/faturas/{mes} (decisões 8 a 13). Os meses são "2026-12"; os valores vêm
 * prontos (ADR 0002).
 *
 * <ul>
 *   <li>{@code inicio}: o dia seguinte ao fechamento da anterior; {@code gravada} diz se a fatura existe (sem ela,
 *       as datas são as dos dias do cartão, decisão 9); {@code fechamentoNormal}, o fechamento pelos dias que
 *       valiam para ela (decisão 7), para "Voltar ao fechamento normal"; {@code ajusteDe}/{@code ajusteAte}, os
 *       limites do ajuste, e {@code ajusteAteOVencimento} diz se o fim é a véspera do vencimento (ou a do
 *       fechamento da seguinte);</li>
 *   <li>{@code situacao}: FUTURA, ABERTA ou FECHADA, calculada com hoje;</li>
 *   <li>{@code compras}: as compras e as parcelas, positivo; {@code estornos}: positivo; {@code total} = compras −
 *       estornos (negativo é crédito); {@code depois}: o total das faturas seguintes, e {@code depoisAte}, a última
 *       delas com lançamento (nulo sem nenhuma);</li>
 *   <li>{@code primeiroMes}/{@code ultimoMes}: até onde as setas andam (decisão 13);</li>
 *   <li>{@code itens}: os lançamentos, a compra mais recente primeiro.</li>
 * </ul>
 */
public record FaturaResponse(
        Long cartaoId,
        String cartaoNome,
        String cartaoCor,
        boolean cartaoArquivado,
        String mes,
        LocalDate inicio,
        LocalDate fechamento,
        LocalDate vencimento,
        boolean gravada,
        boolean fechamentoAjustado,
        LocalDate fechamentoNormal,
        LocalDate ajusteDe,
        LocalDate ajusteAte,
        boolean ajusteAteOVencimento,
        String situacao,
        BigDecimal compras,
        long quantidadeCompras,
        BigDecimal estornos,
        long quantidadeEstornos,
        BigDecimal total,
        BigDecimal depois,
        String depoisAte,
        String primeiroMes,
        String ultimoMes,
        List<Item> itens) {

    /**
     * Uma parcela ou um estorno. {@code tipo} é COMPRA ou ESTORNO; {@code valor} com sinal (a parcela negativa, o
     * estorno positivo); {@code valorTotal} é o da compra, positivo.
     */
    public record Item(Long lancamentoId, Long compraId, String tipo, LocalDate dataCompra, String descricao, BigDecimal valor,
                       int parcela, int parcelas, int parcelaInicial, BigDecimal valorTotal, Long categoriaId, String categoriaNome,
                       String categoriaMaeNome) {
    }
}
