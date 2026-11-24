package com.flux.categorizacao.interno;

import java.util.List;

import com.flux.cartoes.LimpezaDoCartao;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Remove as regras só do cartão antes de ele sair e fecha as posições restantes. */
@Component
public class LimpezaDoCartaoPorRegras implements LimpezaDoCartao {

    private final RegraRepository repositorio;

    public LimpezaDoCartaoPorRegras(RegraRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void antesDeExcluir(Long usuarioId, Long cartaoId) {
        List<Regra> regras = repositorio.findAllByUsuarioIdOrderByPosicao(usuarioId);
        List<Regra> doCartao = regras.stream().filter(regra -> cartaoId.equals(regra.getCartaoId())).toList();
        if (doCartao.isEmpty()) {
            return;
        }
        repositorio.deleteAll(doCartao);
        int posicao = 1;
        for (Regra regra : regras) {
            if (!doCartao.contains(regra)) {
                regra.mudarPosicao(posicao++);
            }
        }
    }
}
