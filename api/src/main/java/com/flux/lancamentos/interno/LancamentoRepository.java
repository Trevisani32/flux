package com.flux.lancamentos.interno;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Os importados ignorados (decisão 30) ficam de fora da lista, do saldo, do lançamento mais
 * antigo e do uso da conta, mas contam nos limites (decisão 24): os nomes dos métodos dizem.
 */
public interface LancamentoRepository extends JpaRepository<Lancamento, Long> {

    /**
     * Receita ou despesa de conta do usuário, não ignorada; um lado de transferência, um lançamento de fatura (a
     * parcela e o estorno, Fatia 05a, decisão 25) e um pagamento de fatura (Fatia 05b, que tem /api/pagamentos) ficam
     * de fora.
     */
    Optional<Lancamento> findByIdAndUsuarioIdAndTransferenciaIdIsNullAndFaturaIdIsNullAndFaturaPagaIdIsNullAndIgnoradoEmIsNull(Long id,
                                                                                                                            Long usuarioId);

    /** Fatia 05b: um pagamento de fatura do usuário. */
    Optional<Lancamento> findByIdAndUsuarioIdAndFaturaPagaIdIsNotNull(Long id, Long usuarioId);

    /** Fatia 05b: os pagamentos convertidos de despesas de uma importação (desfazer a importação). */
    List<Lancamento> findAllByUsuarioIdAndImportacaoIdAndFaturaPagaIdIsNotNull(Long usuarioId, Long importacaoId);

    /** Fatia 05b: os pagamentos convertidos de importados, de todas as importações do usuário (o histórico avisa). */
    List<Lancamento> findAllByUsuarioIdAndImportacaoIdIsNotNullAndFaturaPagaIdIsNotNullOrderByDataAscIdAsc(Long usuarioId);

    /** Fatia 05b: os pagamentos de uma fatura, o mais antigo primeiro. */
    List<Lancamento> findAllByUsuarioIdAndFaturaPagaIdOrderByDataAscIdAsc(Long usuarioId, Long faturaPagaId);

    /** Fatia 05b (decisões 34 e 38): os pagamentos de cada fatura do usuário, somados. */
    @Query("select new com.flux.lancamentos.interno.PagoDaFatura(l.faturaPagaId, count(l), sum(l.valor)) from Lancamento l "
            + "where l.usuarioId = :usuarioId and l.faturaPagaId is not null group by l.faturaPagaId")
    List<PagoDaFatura> pagosDasFaturas(@Param("usuarioId") Long usuarioId);

    /** Fatia 05b: os pagamentos de cada fatura de um cartão, somados. */
    @Query("select new com.flux.lancamentos.interno.PagoDaFatura(l.faturaPagaId, count(l), sum(l.valor)) from Lancamento l "
            + "join Fatura f on f.id = l.faturaPagaId where l.usuarioId = :usuarioId and f.cartaoId = :cartaoId group by l.faturaPagaId")
    List<PagoDaFatura> pagosDoCartao(@Param("usuarioId") Long usuarioId, @Param("cartaoId") Long cartaoId);

    /** Um importado ignorado, para restaurar. */
    Optional<Lancamento> findByIdAndUsuarioIdAndIgnoradoEmIsNotNull(Long id, Long usuarioId);

    /**
     * Conversão (Fatia 04c): o lançamento de conta não ignorado do usuário, inclusive um lado de transferência. O
     * lançamento de fatura fica de fora (Fatia 05a, decisão 27).
     */
    Optional<Lancamento> findByIdAndUsuarioIdAndFaturaIdIsNullAndIgnoradoEmIsNull(Long id, Long usuarioId);

    /** Conversão (Fatia 04c): os lados de transferência que vieram de uma importação. */
    List<Lancamento> findAllByUsuarioIdAndImportacaoIdAndTransferenciaIdIsNotNull(Long usuarioId, Long importacaoId);

    /** Conversão (Fatia 04c): todos os importados que estão numa transferência, para o histórico. */
    List<Lancamento> findAllByUsuarioIdAndImportacaoIdIsNotNullAndTransferenciaIdIsNotNull(Long usuarioId);

    /** Conversão (Fatia 04c, decisão 35): as receitas e despesas de uma importação que continuam na lista. */
    List<Lancamento> findAllByUsuarioIdAndImportacaoIdAndTransferenciaIdIsNullAndIgnoradoEmIsNull(Long usuarioId, Long importacaoId);

    List<Lancamento> findAllByUsuarioIdAndTransferenciaId(Long usuarioId, Long transferenciaId);

    List<Lancamento> findAllByUsuarioIdAndTransferenciaIdIn(Long usuarioId, Collection<Long> transferenciaIds);

    List<Lancamento> findAllByUsuarioIdAndDataBetweenAndIgnoradoEmIsNull(Long usuarioId, LocalDate inicio, LocalDate fim);

    /**
     * Os mais recentes de conta até a data, para "Últimos lançamentos" (10 linhas dão ao menos 5 itens). Os de
     * fatura ficam de fora (Fatia 05a, decisão 26).
     */
    List<Lancamento> findTop10ByUsuarioIdAndContaIdIsNotNullAndDataLessThanEqualAndIgnoradoEmIsNullOrderByDataDescIdDesc(Long usuarioId,
                                                                                                              LocalDate data);

    /** Limite total: conta os ignorados, que são linhas guardadas (decisão 24). */
    long countByUsuarioId(Long usuarioId);

    /** Limite do mês: conta os ignorados (decisão 24). */
    long countByUsuarioIdAndDataBetween(Long usuarioId, LocalDate inicio, LocalDate fim);

    long countByUsuarioIdAndContaIdAndTipoNotAndIgnoradoEmIsNull(Long usuarioId, Long contaId, TipoLancamento tipo);

    long countByUsuarioIdAndContaIdAndTipo(Long usuarioId, Long contaId, TipoLancamento tipo);

    long countByUsuarioIdAndCategoriaIdIn(Long usuarioId, Collection<Long> categoriaIds);

    /**
     * ADR 0003: soma, por conta, de todos os tipos com data até {@code data}, inclusive, sem os ignorados. Os de
     * fatura não têm conta e ficam de fora (Fatia 05a, decisão 24).
     */
    @Query("select new com.flux.lancamentos.interno.SomaDaConta(l.contaId, sum(l.valor)) from Lancamento l "
            + "where l.usuarioId = :usuarioId and l.contaId is not null and l.data <= :data and l.ignoradoEm is null group by l.contaId")
    List<SomaDaConta> somarPorContaAte(@Param("usuarioId") Long usuarioId, @Param("data") LocalDate data);

    /** Soma da conta antes de {@code data} (exclusive), sem os ignorados; nulo se não há lançamento. */
    @Query("select sum(l.valor) from Lancamento l where l.usuarioId = :usuarioId and l.contaId = :contaId "
            + "and l.data < :data and l.ignoradoEm is null")
    BigDecimal somarDaContaAntesDe(@Param("usuarioId") Long usuarioId, @Param("contaId") Long contaId,
                                   @Param("data") LocalDate data);

    /** O lançamento mais antigo da conta (limita a data do saldo inicial), sem os ignorados. */
    @Query("select min(l.data) from Lancamento l where l.usuarioId = :usuarioId and l.contaId = :contaId and l.ignoradoEm is null")
    Optional<LocalDate> primeiraDataDaConta(@Param("usuarioId") Long usuarioId, @Param("contaId") Long contaId);

    /** Para classificar a importação: a conta no período, mais as chaves (de qualquer data), com os ignorados. */
    @Query("select l from Lancamento l where l.usuarioId = :usuarioId and l.contaId = :contaId "
            + "and ((l.data between :inicio and :fim) or l.chaveImportacao in :chaves) order by l.data, l.id")
    List<Lancamento> existentesParaImportar(@Param("usuarioId") Long usuarioId, @Param("contaId") Long contaId,
                                            @Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim,
                                            @Param("chaves") Collection<String> chaves);

    @Query("select distinct l from Lancamento l join Compra c on c.id = l.compraId where l.usuarioId = :usuarioId "
            + "and l.cartaoId = :cartaoId and (l.faturaId = :faturaId or c.dataCompra between :inicio and :fim "
            + "or l.chaveImportacao in :chaves) order by l.data, l.id")
    List<Lancamento> existentesNoCartao(@Param("usuarioId") Long usuarioId, @Param("cartaoId") Long cartaoId,
                                        @Param("faturaId") Long faturaId, @Param("inicio") LocalDate inicio,
                                        @Param("fim") LocalDate fim, @Param("chaves") Collection<String> chaves);

    @Modifying
    @Query("delete from Lancamento l where l.usuarioId = :usuarioId and l.importacaoId = :importacaoId")
    int apagarDaImportacao(@Param("usuarioId") Long usuarioId, @Param("importacaoId") Long importacaoId);

    List<Lancamento> findAllByUsuarioIdAndImportacaoIdAndFaturaIdIsNotNull(Long usuarioId, Long importacaoId);

    @Modifying
    @Query("delete from Lancamento l where l.usuarioId = :usuarioId and l.contaId = :contaId and l.ignoradoEm is not null")
    int apagarIgnoradosDaConta(@Param("usuarioId") Long usuarioId, @Param("contaId") Long contaId);

    /** Editado: mudou depois de criado (ignorar e restaurar não mexem em atualizado_em). */
    @Query("select new com.flux.lancamentos.interno.QuantidadesDaImportacao(l.importacaoId, "
            + "sum(case when l.ignoradoEm is null and l.atualizadoEm > l.criadoEm then 1L else 0L end), "
            + "sum(case when l.ignoradoEm is not null then 1L else 0L end)) "
            + "from Lancamento l where l.usuarioId = :usuarioId and l.importacaoId is not null and l.compraId is null "
            + "group by l.importacaoId")
    List<QuantidadesDaImportacao> quantidadesDasImportacoes(@Param("usuarioId") Long usuarioId);

    @Query("select new com.flux.lancamentos.interno.CompraDaImportacao(l.importacaoId, l.compraId, "
            + "max(case when l.atualizadoEm > l.criadoEm then 1L else 0L end)) "
            + "from Lancamento l where l.usuarioId = :usuarioId and l.importacaoId is not null and l.compraId is not null "
            + "group by l.importacaoId, l.compraId")
    List<CompraDaImportacao> comprasDasImportacoes(@Param("usuarioId") Long usuarioId);

    List<Lancamento> findAllByUsuarioIdAndImportacaoIdAndIgnoradoEmIsNotNullOrderByIgnoradoEmDescIdDesc(Long usuarioId,
                                                                                                    Long importacaoId);

    /**
     * Regras: as receitas e despesas não ignoradas. Desde a 05c, traz uma compra de cartão só uma vez, pela primeira
     * parcela, com a data e o total da compra; pagamento de fatura nunca entra.
     */
    @Query("select new com.flux.lancamentos.interno.ParaAsRegras(l.id, l.contaId, l.cartaoId, l.compraId, "
            + "case when c.id is null then l.data else c.dataCompra end, "
            + "case when c.id is null then l.valor when l.valor < 0 then -c.valorTotal else c.valorTotal end, "
            + "l.descricao, l.descricaoBanco, l.categoriaId) from Lancamento l left join Compra c on c.id = l.compraId "
            + "where l.usuarioId = :usuarioId and l.tipo <> :transferencia and l.faturaPagaId is null and l.ignoradoEm is null "
            + "and (l.faturaId is null or not exists (select outra.id from Lancamento outra where outra.compraId = l.compraId "
            + "and outra.numeroParcela < l.numeroParcela)) "
            + "order by l.data desc, l.id desc")
    List<ParaAsRegras> paraAsRegras(@Param("usuarioId") Long usuarioId, @Param("transferencia") TipoLancamento transferencia);

    List<Lancamento> findAllByUsuarioIdAndIdIn(Long usuarioId, Collection<Long> ids);

    /** Fatia 05a: as parcelas de compras (e os estornos), a primeira de cada compra antes. */
    List<Lancamento> findAllByUsuarioIdAndCompraIdInOrderByCompraIdAscNumeroParcelaAsc(Long usuarioId, Collection<Long> compraIds);

    /** Fatia 05a: os lançamentos de uma fatura. */
    List<Lancamento> findAllByUsuarioIdAndFaturaId(Long usuarioId, Long faturaId);

    /** Fatia 05a (decisão 19): as parcelas saem para serem gravadas de novo, ou com a compra. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Lancamento l where l.usuarioId = :usuarioId and l.compraId = :compraId")
    int apagarDaCompra(@Param("usuarioId") Long usuarioId, @Param("compraId") Long compraId);

    /** Fatia 05a (decisão 11): as faturas do usuário somadas: quantos lançamentos, a soma, as compras e os estornos. */
    @Query("select new com.flux.lancamentos.interno.TotalDaFatura(l.faturaId, count(l), sum(l.valor), "
            + "sum(case when l.valor < 0 then 1L else 0L end), sum(case when l.valor > 0 then l.valor end)) "
            + "from Lancamento l where l.usuarioId = :usuarioId and l.faturaId is not null group by l.faturaId")
    List<TotalDaFatura> totaisDasFaturas(@Param("usuarioId") Long usuarioId);

    /** Fatia 05a: as faturas de um cartão somadas. */
    @Query("select new com.flux.lancamentos.interno.TotalDaFatura(l.faturaId, count(l), sum(l.valor), "
            + "sum(case when l.valor < 0 then 1L else 0L end), sum(case when l.valor > 0 then l.valor end)) "
            + "from Lancamento l where l.usuarioId = :usuarioId and l.cartaoId = :cartaoId group by l.faturaId")
    List<TotalDaFatura> totaisDoCartao(@Param("usuarioId") Long usuarioId, @Param("cartaoId") Long cartaoId);
}
