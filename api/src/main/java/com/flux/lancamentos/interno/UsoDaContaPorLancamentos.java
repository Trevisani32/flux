package com.flux.lancamentos.interno;

import java.util.Optional;

import com.flux.contas.UsoDaConta;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Receitas e despesas da conta, sem os ignorados: "34 lançamentos". Vem antes das transferências na frase. */
@Component
public class UsoDaContaPorLancamentos implements UsoDaConta, Ordered {

    private final LancamentoRepository repositorio;

    public UsoDaContaPorLancamentos(LancamentoRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> uso(Long usuarioId, Long contaId) {
        long quantidade = repositorio.countByUsuarioIdAndContaIdAndTipoNotAndIgnoradoEmIsNull(usuarioId, contaId,
                TipoLancamento.TRANSFERENCIA);
        return quantidade == 0 ? Optional.empty() : Optional.of(Quantidades.lancamentos(quantidade));
    }

    @Override
    public int getOrder() {
        return 10;
    }
}
