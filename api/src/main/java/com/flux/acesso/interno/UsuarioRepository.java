package com.flux.acesso.interno;

import java.time.Instant;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Usuario u where u.id = :id")
    Optional<Usuario> buscarComTravaPorId(@Param("id") Long id);

    /**
     * Trava (até o fim da transação) o e-mail informado, para dois cadastros ou trocas
     * simultâneos com o mesmo endereço não colidirem na restrição UNIQUE.
     */
    @Query(value = "select 1 from pg_advisory_xact_lock(hashtext(:email))", nativeQuery = true)
    Integer travarEmail(@Param("email") String email);

    @Modifying
    @Query("delete from Usuario u where u.emailConfirmadoEm is null and u.criadoEm < :limite")
    int apagarNaoConfirmadosCriadosAntesDe(@Param("limite") Instant limite);
}
