package com.flux.importacao.interno;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PerfilCsvRepository extends JpaRepository<PerfilCsv, Long> {

    Optional<PerfilCsv> findByContaIdAndUsuarioId(Long contaId, Long usuarioId);
}
