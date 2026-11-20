package com.flux.lancamentos.interno;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CompraRepository extends JpaRepository<Compra, Long> {

    Optional<Compra> findByIdAndUsuarioIdAndTipo(Long id, Long usuarioId, TipoCompra tipo);

    List<Compra> findAllByUsuarioIdAndIdIn(Long usuarioId, Collection<Long> ids);

    /** Ajuste de fechamento (decisão 12): as compras com data no trecho que mudou de fatura. */
    List<Compra> findAllByUsuarioIdAndCartaoIdAndDataCompraBetween(Long usuarioId, Long cartaoId, LocalDate inicio, LocalDate fim);

    /** Troca de dias (decisão 7): as compras com data depois do fechamento da aberta. */
    List<Compra> findAllByUsuarioIdAndCartaoIdAndDataCompraAfter(Long usuarioId, Long cartaoId, LocalDate data);

    /** Uso do cartão (decisão 28). */
    long countByUsuarioIdAndCartaoIdAndTipo(Long usuarioId, Long cartaoId, TipoCompra tipo);
}
