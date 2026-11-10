package com.flux.contas.interno;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContaRepository extends JpaRepository<Conta, Long> {

    Optional<Conta> findByIdAndUsuarioId(Long id, Long usuarioId);

    List<Conta> findAllByUsuarioId(Long usuarioId);

    long countByUsuarioId(Long usuarioId);

    boolean existsByUsuarioIdAndNomeChaveAndArquivadaEmIsNull(Long usuarioId, String nomeChave);

    boolean existsByUsuarioIdAndNomeChaveAndArquivadaEmIsNullAndIdNot(Long usuarioId, String nomeChave, Long id);

    /**
     * Trava, até o fim da transação, as alterações de contas do usuário: duas requisições
     * simultâneas não passam juntas pela checagem do nome nem pelo limite de contas.
     */
    @Query(value = "select 1 from pg_advisory_xact_lock(hashtextextended('contas:' || :usuarioId, 0))",
            nativeQuery = true)
    Integer travarContasDoUsuario(@Param("usuarioId") Long usuarioId);
}
