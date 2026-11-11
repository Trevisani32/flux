package com.flux.lancamentos.interno;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferenciaRepository extends JpaRepository<Transferencia, Long> {

    Optional<Transferencia> findByIdAndUsuarioId(Long id, Long usuarioId);

    /** Desfazer a importação de um lado (Fatia 04c, decisão 34): os vínculos que saem junto. */
    List<Transferencia> findAllByIdInAndUsuarioId(Collection<Long> ids, Long usuarioId);
}