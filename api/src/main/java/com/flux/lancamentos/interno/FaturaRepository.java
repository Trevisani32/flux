package com.flux.lancamentos.interno;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FaturaRepository extends JpaRepository<Fatura, Long> {

    List<Fatura> findAllByUsuarioIdAndCartaoId(Long usuarioId, Long cartaoId);

    List<Fatura> findAllByUsuarioId(Long usuarioId);

    /** Fatia 05b: as faturas dos pagamentos da lista. */
    List<Fatura> findAllByUsuarioIdAndIdIn(Long usuarioId, Collection<Long> ids);

    /** Fatia 05b: a fatura de um pagamento. */
    Optional<Fatura> findByIdAndUsuarioId(Long id, Long usuarioId);
}
