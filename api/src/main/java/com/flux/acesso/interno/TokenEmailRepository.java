package com.flux.acesso.interno;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TokenEmailRepository extends JpaRepository<TokenEmail, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TokenEmail t where t.tokenHash = :hash")
    Optional<TokenEmail> buscarComTravaPorHash(@Param("hash") String hash);

    /** Dono do link, sem travar nada: serve para travar o usuário antes do token. */
    @Query("select t.usuarioId from TokenEmail t where t.tokenHash = :hash")
    Optional<Long> usuarioDoToken(@Param("hash") String hash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TokenEmail t where t.id = :id")
    Optional<TokenEmail> buscarComTravaPorId(@Param("id") Long id);

    @Query("select t from TokenEmail t where t.usuarioId = :usuarioId and t.finalidade = :finalidade "
            + "and t.usadoEm is null and t.invalidadoEm is null and t.expiraEm > :agora "
            + "order by t.criadoEm desc, t.id desc")
    List<TokenEmail> pendentes(@Param("usuarioId") Long usuarioId, @Param("finalidade") FinalidadeToken finalidade,
                               @Param("agora") Instant agora);

    List<TokenEmail> findByUsuarioIdAndFinalidadeOrderByIdAsc(Long usuarioId, FinalidadeToken finalidade);

    @Modifying
    @Query("update TokenEmail t set t.invalidadoEm = :agora where t.usuarioId = :usuarioId "
            + "and t.finalidade = :finalidade and t.usadoEm is null and t.invalidadoEm is null")
    int invalidarPendentes(@Param("usuarioId") Long usuarioId, @Param("finalidade") FinalidadeToken finalidade,
                           @Param("agora") Instant agora);

    @Modifying
    @Query("update TokenEmail t set t.invalidadoEm = :agora where t.usuarioId = :usuarioId "
            + "and t.finalidade = :finalidade and t.id <> :exceto and t.usadoEm is null and t.invalidadoEm is null")
    int invalidarOutrosPendentes(@Param("usuarioId") Long usuarioId,
                                 @Param("finalidade") FinalidadeToken finalidade, @Param("exceto") Long exceto,
                                 @Param("agora") Instant agora);

    @Modifying
    @Query("delete from TokenEmail t where t.expiraEm < :agora")
    int apagarVencidos(@Param("agora") Instant agora);
}
