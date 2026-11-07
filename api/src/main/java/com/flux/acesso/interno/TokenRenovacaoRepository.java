package com.flux.acesso.interno;

import java.time.Instant;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TokenRenovacaoRepository extends JpaRepository<TokenRenovacao, Long> {

    Optional<TokenRenovacao> findByTokenHash(String hash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TokenRenovacao t where t.tokenHash = :hash")
    Optional<TokenRenovacao> buscarComTravaPorHash(@Param("hash") String hash);

    @Modifying
    @Query("update TokenRenovacao t set t.revogadoEm = :agora "
            + "where t.usuarioId = :usuarioId and t.revogadoEm is null")
    int revogarTodosDoUsuario(@Param("usuarioId") Long usuarioId, @Param("agora") Instant agora);

    @Modifying
    @Query("delete from TokenRenovacao t where t.expiraEm < :agora")
    int apagarVencidos(@Param("agora") Instant agora);
}
