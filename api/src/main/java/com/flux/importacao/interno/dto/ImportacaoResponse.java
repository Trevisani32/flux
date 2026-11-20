package com.flux.importacao.interno.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Um item do histórico (decisão 33). {@code deFora}: antes do saldo inicial e fora da importação;
 * {@code editados} e {@code excluidos} contam os lançamentos dela hoje (os excluídos são os
 * ignorados). Desfeita, {@code desfeitaEm} vem preenchido. {@code transferencias} (Fatia 04c, decisão
 * 34): os lançamentos dela que estão numa transferência convertida, que o desfazer desfaz junto. {@code pagamentos}
 * (Fatia 05b): os lançamentos dela que viraram pagamento de fatura, que o desfazer apaga.
 */
public record ImportacaoResponse(
        Long id,
        String nomeArquivo,
        Long contaId,
        String contaNome,
        boolean contaArquivada,
        Instant criadaEm,
        LocalDate dataInicio,
        LocalDate dataFim,
        int importadas,
        int jaImportadas,
        int desmarcadas,
        int deFora,
        long editados,
        long excluidos,
        Instant desfeitaEm,
        List<Transferencia> transferencias,
        List<Pagamento> pagamentos) {

    /**
     * Um lançamento da importação numa transferência: a data dele, a conta do outro lado e o tipo que o
     * outro lado volta a ter (RECEITA ou DESPESA). Com {@code contaArquivada}, o desfazer é recusado.
     */
    public record Transferencia(LocalDate data, Long contaId, String contaNome, boolean contaArquivada, String tipo) {
    }

    /**
     * Fatia 05b: um lançamento da importação que virou pagamento de fatura: a data dele, o cartão e o mês da fatura
     * ("2026-11"), que volta a ter esse valor pendente. Com {@code cartaoArquivado}, o desfazer é recusado.
     */
    public record Pagamento(LocalDate data, Long cartaoId, String cartaoNome, boolean cartaoArquivado, String faturaMes) {
    }
}
