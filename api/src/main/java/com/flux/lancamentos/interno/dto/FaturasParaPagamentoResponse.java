package com.flux.lancamentos.interno.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Resposta de GET /api/lancamentos/{id}/faturas-para-pagamento (Fatia 05b, decisão 37): a despesa importada e, para
 * cada cartão ativo, as faturas que ela pode pagar, as sugeridas primeiro.
 */
public record FaturasParaPagamentoResponse(ItemDaLista lancamento, List<Cartao> cartoes) {

    /** Um cartão ativo e as faturas dele, já em ordem. */
    public record Cartao(Long id, String nome, String cor, List<Fatura> faturas) {
    }

    /**
     * Uma fatura que já fechou ou está aberta, dos meses perto da data da despesa. {@code situacao} é ABERTA, FECHADA ou
     * PAGA; {@code sugerida}: fechada, com o pendente igual ao valor e o vencimento a até 5 dias da data.
     */
    public record Fatura(String mes, String situacao, LocalDate vencimento, BigDecimal total, BigDecimal pendente, boolean sugerida) {
    }
}
