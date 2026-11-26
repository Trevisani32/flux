package com.flux.recorrencias.interno;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OcorrenciaTratadaRepository extends JpaRepository<OcorrenciaTratada, Long> {

    List<OcorrenciaTratada> findAllByUsuarioIdAndPeriodoBetween(Long usuarioId, LocalDate de, LocalDate ate);

    List<OcorrenciaTratada> findAllByUsuarioIdAndRecorrenciaId(Long usuarioId, Long recorrenciaId);

    Optional<OcorrenciaTratada> findByUsuarioIdAndRecorrenciaIdAndPeriodo(Long usuarioId, Long recorrenciaId, LocalDate periodo);

    Optional<OcorrenciaTratada> findByUsuarioIdAndLancamentoId(Long usuarioId, Long lancamentoId);

    Optional<OcorrenciaTratada> findByUsuarioIdAndCompraId(Long usuarioId, Long compraId);

    List<OcorrenciaTratada> findAllByUsuarioIdAndLancamentoIdIn(Long usuarioId, Collection<Long> lancamentoIds);

    List<OcorrenciaTratada> findAllByUsuarioIdAndCompraIdIn(Long usuarioId, Collection<Long> compraIds);
}
