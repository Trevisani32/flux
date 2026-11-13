package com.flux.cartoes.interno;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartaoRepository extends JpaRepository<Cartao, Long> {

    Optional<Cartao> findByIdAndUsuarioId(Long id, Long usuarioId);

    List<Cartao> findAllByUsuarioId(Long usuarioId);

    long countByUsuarioId(Long usuarioId);

    boolean existsByUsuarioIdAndNomeChaveAndArquivadoEmIsNull(Long usuarioId, String nomeChave);

    boolean existsByUsuarioIdAndNomeChaveAndArquivadoEmIsNullAndIdNot(Long usuarioId, String nomeChave, Long id);

    /** Uso da conta (decisão 4): os cartões, ativos e arquivados, que pagam com ela. */
    long countByUsuarioIdAndContaPagamentoId(Long usuarioId, Long contaPagamentoId);

    /**
     * Trava, até o fim da transação, as alterações de cartões do usuário (decisão 49): o nome, o limite de
     * cartões, arquivar e excluir, os dias e o ajuste de fechamento não correm com uma compra.
     */
    @Query(value = "select 1 from pg_advisory_xact_lock(hashtextextended('cartoes:' || :usuarioId, 0))", nativeQuery = true)
    Integer travarCartoesDoUsuario(@Param("usuarioId") Long usuarioId);
}
