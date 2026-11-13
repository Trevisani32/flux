package com.flux.lancamentos.interno;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FaturaRepository extends JpaRepository<Fatura, Long> {

    List<Fatura> findAllByUsuarioIdAndCartaoId(Long usuarioId, Long cartaoId);

    List<Fatura> findAllByUsuarioId(Long usuarioId);
}
