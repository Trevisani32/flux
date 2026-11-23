package com.flux.lancamentos.interno;

/**
 * Receita e despesa têm categoria da mesma natureza; a transferência não tem categoria. Desde a Fatia 05b, o
 * pagamento da fatura (negativo, na conta) também não: não é despesa, porque a despesa é a compra (ADR 0018).
 */
public enum TipoLancamento {
    RECEITA,
    DESPESA,
    TRANSFERENCIA,
    PAGAMENTO_FATURA
}
