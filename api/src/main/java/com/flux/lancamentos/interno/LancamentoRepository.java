package com.flux.lancamentos.interno;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LancamentoRepository extends JpaRepository<Lancamento, Long> {

    /** Receita ou despesa do usuário; um lado de transferência fica de fora. */
    Optional<Lancamento> findByIdAndUsuarioIdAndTransferenciaIdIsNull(Long id, Long usuarioId);

    List<Lancamento> findAllByUsuarioIdAndTransferenciaId(Long usuarioId, Long transferenciaId);

    List<Lancamento> findAllByUsuarioIdAndTransferenciaIdIn(Long usuarioId, Collection<Long> transferenciaIds);

    List<Lancamento> findAllByUsuarioIdAndDataBetween(Long usuarioId, LocalDate inicio, LocalDate fim);

    /** Os mais recentes até a data, para "Últimos lançamentos" (10 linhas dão ao menos 5 itens). */
    List<Lancamento> findTop10ByUsuarioIdAndDataLessThanEqualOrderByDataDescIdDesc(Long usuarioId, LocalDate data);

    long countByUsuarioId(Long usuarioId);

    long countByUsuarioIdAndDataBetween(Long usuarioId, LocalDate inicio, LocalDate fim);

    long countByUsuarioIdAndContaIdAndTipoNot(Long usuarioId, Long contaId, TipoLancamento tipo);

    long countByUsuarioIdAndContaIdAndTipo(Long usuarioId, Long contaId, TipoLancamento tipo);

    long countByUsuarioIdAndCategoriaIdIn(Long usuarioId, Collection<Long> categoriaIds);

    /** ADR 0003: soma, por conta, de todos os tipos com data até {@code data}, inclusive. */
    @Query("select new com.flux.lancamentos.interno.SomaDaConta(l.contaId, sum(l.valor)) from Lancamento l "
            + "where l.usuarioId = :usuarioId and l.data <= :data group by l.contaId")
    List<SomaDaConta> somarPorContaAte(@Param("usuarioId") Long usuarioId, @Param("data") LocalDate data);

    /** Soma da conta antes de {@code data} (exclusive); nulo se não há lançamento. */
    @Query("select sum(l.valor) from Lancamento l where l.usuarioId = :usuarioId and l.contaId = :contaId and l.data < :data")
    BigDecimal somarDaContaAntesDe(@Param("usuarioId") Long usuarioId, @Param("contaId") Long contaId,
                                   @Param("data") LocalDate data);

    @Query("select min(l.data) from Lancamento l where l.usuarioId = :usuarioId and l.contaId = :contaId")
    Optional<LocalDate> primeiraDataDaConta(@Param("usuarioId") Long usuarioId, @Param("contaId") Long contaId);
}
