package com.flux.recorrencias.interno;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecorrenciaRepository extends JpaRepository<Recorrencia, Long> {

    @Query(value = "select pg_advisory_xact_lock(hashtextextended('recorrencias:' || cast(:usuarioId as text), 0))",
            nativeQuery = true)
    void travarRecorrenciasDoUsuario(@Param("usuarioId") Long usuarioId);

    Optional<Recorrencia> findByIdAndUsuarioId(Long id, Long usuarioId);

    List<Recorrencia> findAllByUsuarioId(Long usuarioId);

    long countByUsuarioId(Long usuarioId);

    long countByUsuarioIdAndCategoriaIdIn(Long usuarioId, Collection<Long> categoriaIds);
}
