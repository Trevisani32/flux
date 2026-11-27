package com.flux.lancamentos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.List;

import com.flux.cartoes.CartaoResumo;

/**
 * O que a projeção de saldo (Fatia 07b, ADR 0023) pede aos lançamentos: o que já está gravado com data futura nas contas e o
 * que cada fatura ainda vai tirar da conta de pagamento. Só lê, com um número fixo de consultas por chamada, quantos forem
 * as contas, os cartões, as faturas e os dias.
 */
public interface LancamentosDaProjecao {

    /**
     * Os lançamentos das contas com a data de {@code de} a {@code ate}, inclusive: receitas, despesas e cada lado de
     * transferência, sem os ignorados, em ordem de data e id. O pagamento de fatura fica de fora: a data dele vai só até
     * hoje, e o pendente da fatura já o desconta.
     */
    List<LancamentoFuturo> futuros(Long usuarioId, Collection<Long> contaIds, LocalDate de, LocalDate ate);

    /**
     * Das faturas dos {@code cartoes}, as que vencem até {@code ate} e ainda vão tirar dinheiro da conta: as gravadas e as
     * que só recebem compras previstas. Cada prevista cai na fatura da data dela, pelas faturas gravadas (com os ajustes de
     * fechamento), como uma compra de verdade. A saída é o pendente com as previstas somadas ao total: total + previsto −
     * pago, nunca negativo, e zero na fatura paga fora do Flux (ADR 0018). Só as de saída maior que zero, em ordem de
     * vencimento, id do cartão e mês.
     */
    List<FaturaProjetada> faturas(Long usuarioId, List<CartaoResumo> cartoes, LocalDate ate, List<CompraPrevista> previstas);

    /**
     * Um lançamento já gravado. {@code tipo}: "RECEITA", "DESPESA" ou "TRANSFERENCIA"; {@code valor} com sinal e escala 2;
     * na transferência, {@code outraContaId} é a conta do outro lado (nulo nos outros tipos).
     */
    record LancamentoFuturo(Long id, Long contaId, LocalDate data, String tipo, BigDecimal valor, String descricao,
                            Long outraContaId) {
    }

    /** Uma compra prevista de uma recorrência no cartão: a data da compra e o valor, positivo. */
    record CompraPrevista(Long recorrenciaId, LocalDate periodo, Long cartaoId, LocalDate data, BigDecimal valor) {
    }

    /**
     * Uma fatura que vai sair da conta de pagamento. {@code pendente}: total − pago, nunca negativo (zero na paga fora do
     * Flux), sem as previstas; {@code previsto}: a soma das previstas dela; {@code saida}: o pendente com as previstas no
     * total, que nem sempre é a soma dos dois (o pago a mais e a paga fora do Flux absorvem a prevista). Positivos, escala
     * 2; as previstas na ordem em que vieram.
     */
    record FaturaProjetada(Long cartaoId, YearMonth mes, LocalDate vencimento, BigDecimal pendente, BigDecimal previsto,
                           BigDecimal saida, List<CompraPrevista> previstas) {
    }
}
