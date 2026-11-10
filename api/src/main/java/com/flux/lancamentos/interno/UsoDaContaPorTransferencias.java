package com.flux.lancamentos.interno;

import java.util.Optional;

import com.flux.contas.UsoDaConta;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transferências da conta: "3 transferências". Cada transferência tem um lado só em cada
 * conta (origem e destino são diferentes), então contar os lados da conta é contar as
 * transferências.
 */
@Component
public class UsoDaContaPorTransferencias implements UsoDaConta, Ordered {

    private final LancamentoRepository repositorio;

    public UsoDaContaPorTransferencias(LancamentoRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> uso(Long usuarioId, Long contaId) {
        long quantidade = repositorio.countByUsuarioIdAndContaIdAndTipo(usuarioId, contaId, TipoLancamento.TRANSFERENCIA);
        return quantidade == 0 ? Optional.empty() : Optional.of(Quantidades.transferencias(quantidade));
    }

    @Override
    public int getOrder() {
        return 20;
    }
}
