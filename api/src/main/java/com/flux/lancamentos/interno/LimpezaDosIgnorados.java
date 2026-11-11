package com.flux.lancamentos.interno;

import com.flux.contas.LimpezaDaConta;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Os importados ignorados não são uso da conta (decisão 30), mas a FK RESTRICT da V4 os prende
 * a ela: saem antes da conta, na transação da exclusão (decisão 34).
 */
@Component
public class LimpezaDosIgnorados implements LimpezaDaConta {

    private final LancamentoRepository repositorio;

    public LimpezaDosIgnorados(LancamentoRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void antesDeExcluir(Long usuarioId, Long contaId) {
        repositorio.apagarIgnoradosDaConta(usuarioId, contaId);
    }
}
