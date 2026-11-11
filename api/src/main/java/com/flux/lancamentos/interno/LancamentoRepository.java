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

    /** Receita ou despesa do usuário, não ignorada; um lado de transferência fica de fora. */
    Optional<Lancamento> findByIdAndUsuarioIdAndTransferenciaIdIsNullAndIgnoradoEmIsNull(Long id, Long usuarioId);

    /** Um importado ignorado, para restaurar. */
    Optional<Lancamento> findByIdAndUsuarioIdAndIgnoradoEmIsNotNull(Long id, Long usuarioId);

    /** Conversão (Fatia 04c): o lançamento não ignorado do usuário, inclusive um lado de transferência. */
    Optional<Lancamento> findByIdAndUsuarioIdAndIgnoradoEmIsNull(Long id, Long usuarioId);

    /** Conversão (Fatia 04c): os lados de transferência que vieram de uma importação. */
    List<Lancamento> findAllByUsuarioIdAndImportacaoIdAndTransferenciaIdIsNotNull(Long usuarioId, Long importacaoId);

    /** Conversão (Fatia 04c): todos os importados que estão numa transferência, para o histórico. */
    List<Lancamento> findAllByUsuarioIdAndImportacaoIdIsNotNullAndTransferenciaIdIsNotNull(Long usuarioId);

    /** Conversão (Fatia 04c, decisão 35): as receitas e despesas de uma importação que continuam na lista. */
    List<Lancamento> findAllByUsuarioIdAndImportacaoIdAndTransferenciaIdIsNullAndIgnoradoEmIsNull(Long usuarioId, Long importacaoId);

    List<Lancamento> findAllByUsuarioIdAndTransferenciaId(Long usuarioId, Long transferenciaId);

    List<Lancamento> findAllByUsuarioIdAndTransferenciaIdIn(Long usuarioId, Collection<Long> transferenciaIds);

    List<Lancamento> findAllByUsuarioIdAndDataBetweenAndIgnoradoEmIsNull(Long usuarioId, LocalDate inicio, LocalDate fim);

    /** Os mais recentes até a data, para "Últimos lançamentos" (10 linhas dão ao menos 5 itens). */
    List<Lancamento> findTop10ByUsuarioIdAndDataLessThanEqualAndIgnoradoEmIsNullOrderByDataDescIdDesc(Long usuarioId, LocalDate data);

    /** Limite total: conta os ignorados, que são linhas guardadas (decisão 24). */
    long countByUsuarioId(Long usuarioId);

    /** Limite do mês: conta os ignorados (decisão 24). */
    long countByUsuarioIdAndDataBetween(Long usuarioId, LocalDate inicio, LocalDate fim);

    long countByUsuarioIdAndContaIdAndTipoNotAndIgnoradoEmIsNull(Long usuarioId, Long contaId, TipoLancamento tipo);

    long countByUsuarioIdAndContaIdAndTipo(Long usuarioId, Long contaId, TipoLancamento tipo);

    long countByUsuarioIdAndCategoriaIdIn(Long usuarioId, Collection<Long> categoriaIds);

    /** ADR 0003: soma, por conta, de todos os tipos com data até {@code data}, inclusive, sem os ignorados. */
    @Query("select new com.flux.lancamentos.interno.SomaDaConta(l.contaId, sum(l.valor)) from Lancamento l "
            + "where l.usuarioId = :usuarioId and l.data <= :data and l.ignoradoEm is null group by l.contaId")
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

    @Modifying
    @Query("delete from Lancamento l where l.usuarioId = :usuarioId and l.importacaoId = :importacaoId")
    int apagarDaImportacao(@Param("usuarioId") Long usuarioId, @Param("importacaoId") Long importacaoId);

    @Modifying
    @Query("delete from Lancamento l where l.usuarioId = :usuarioId and l.contaId = :contaId and l.ignoradoEm is not null")
    int apagarIgnoradosDaConta(@Param("usuarioId") Long usuarioId, @Param("contaId") Long contaId);

    /** Editado: mudou depois de criado (ignorar e restaurar não mexem em atualizado_em). */
    @Query("select new com.flux.lancamentos.interno.QuantidadesDaImportacao(l.importacaoId, "
            + "sum(case when l.ignoradoEm is null and l.atualizadoEm > l.criadoEm then 1L else 0L end), "
            + "sum(case when l.ignoradoEm is not null then 1L else 0L end)) "
            + "from Lancamento l where l.usuarioId = :usuarioId and l.importacaoId is not null group by l.importacaoId")
    List<QuantidadesDaImportacao> quantidadesDasImportacoes(@Param("usuarioId") Long usuarioId);

    List<Lancamento> findAllByUsuarioIdAndImportacaoIdAndIgnoradoEmIsNotNullOrderByIgnoradoEmDescIdDesc(Long usuarioId,
                                                                                                    Long importacaoId);

    /** Regras (Fatia 04b): as receitas e despesas não ignoradas, só com as colunas que as regras usam. */
    @Query("select new com.flux.lancamentos.interno.ParaAsRegras(l.id, l.contaId, l.data, l.valor, l.descricao, l.descricaoBanco, "
            + "l.categoriaId) from Lancamento l where l.usuarioId = :usuarioId and l.tipo <> :transferencia and l.ignoradoEm is null "
            + "order by l.data desc, l.id desc")
    List<ParaAsRegras> paraAsRegras(@Param("usuarioId") Long usuarioId, @Param("transferencia") TipoLancamento transferencia);

    List<Lancamento> findAllByUsuarioIdAndIdIn(Long usuarioId, Collection<Long> ids);
}