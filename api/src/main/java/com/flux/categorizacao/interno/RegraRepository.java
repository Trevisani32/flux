package com.flux.categorizacao.interno;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Toda consulta recebe o usuário (ADR 0007). */
public interface RegraRepository extends JpaRepository<Regra, Long> {

    List<Regra> findAllByUsuarioIdOrderByPosicao(Long usuarioId);

    Optional<Regra> findByIdAndUsuarioId(Long id, Long usuarioId);

    long countByUsuarioIdAndCategoriaIdIn(Long usuarioId, Collection<Long> categoriaIds);
}
