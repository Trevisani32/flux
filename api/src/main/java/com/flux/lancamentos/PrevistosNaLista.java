package com.flux.lancamentos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * As ocorrências previstas das recorrências, para a lista do mês (Fatia 07a, ADR 0022). O módulo de lançamentos declara
 * e o de recorrências implementa, como o {@code MovimentoDosCartoes}: a lista mostra os previstos sem depender de
 * recorrências. Sem implementação, a lista fica sem previstos e sem a marca "da recorrência". Quem usa pega a
 * implementação por {@code ObjectProvider}, porque ela depende de {@link LancamentosDasRecorrencias}.
 */
public interface PrevistosNaLista {

    /**
     * As ocorrências não tratadas com a data de {@code de} a {@code ate}, inclusive, das recorrências que não estão
     * pausadas, presas à janela de hoje − 60 dias a hoje + 12 meses (decisões 12 e 20). A data é a prevista: no cartão, a
     * da compra. Em ordem de data, descrição e id da recorrência. Só lê.
     */
    List<Previsto> previstos(Long usuarioId, LocalDate de, LocalDate ate);

    /** Dos lançamentos e das compras informados, os ligados a uma ocorrência efetivada: o id deles e o da recorrência. */
    Ligacoes ligacoes(Long usuarioId, Collection<Long> lancamentoIds, Collection<Long> compraIds);

    /**
     * Uma ocorrência prevista. {@code periodo} é a chave (o primeiro dia do mês, da semana ou do ano); {@code valor} tem o
     * sinal do tipo (ADR 0002) e escala 2; {@code tipo} é "RECEITA" ou "DESPESA"; conta ou cartão, nunca os dois;
     * {@code atrasada}: a data é antes de hoje.
     */
    record Previsto(Long recorrenciaId, LocalDate periodo, LocalDate data, String descricao, BigDecimal valor, String tipo,
                    boolean valorVaria, Long contaId, Long cartaoId, Long categoriaId, boolean atrasada) {
    }

    /** Por id de lançamento e por id de compra, o id da recorrência da ocorrência ligada. */
    record Ligacoes(Map<Long, Long> porLancamento, Map<Long, Long> porCompra) {
    }
}
