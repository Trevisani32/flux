package com.flux.categorizacao.interno;

import java.util.List;

import com.flux.contas.LimpezaDaConta;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Decisão 24: o filtro de conta não é uso dela. Antes de a conta sair, as regras só dela saem e as
 * outras são renumeradas (1, 2, 3…, sem buraco). A FK {@code CASCADE} da V6 fica de reserva. Roda
 * dentro da trava de contas, que toda escrita de regra também pega.
 */
@Component
public class LimpezaDaContaPorRegras implements LimpezaDaConta {

    private final RegraRepository repositorio;

    public LimpezaDaContaPorRegras(RegraRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void antesDeExcluir(Long usuarioId, Long contaId) {
        List<Regra> regras = repositorio.findAllByUsuarioIdOrderByPosicao(usuarioId);
        List<Regra> daConta = regras.stream().filter(regra -> contaId.equals(regra.getContaId())).toList();
        if (daConta.isEmpty()) {
            return;
        }
        repositorio.deleteAll(daConta);
        int posicao = 1;
        for (Regra regra : regras) {
            if (!daConta.contains(regra)) {
                regra.mudarPosicao(posicao++);
            }
        }
    }
}
