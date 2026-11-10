package com.flux.lancamentos.interno;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferenciaRepository extends JpaRepository<Transferencia, Long> {

    Optional<Transferencia> findByIdAndUsuarioId(Long id, Long usuarioId);
}
