package com.flux.cartoes.interno;

import java.util.List;
import java.util.Optional;

import com.flux.cartoes.CartaoResumo;
import com.flux.cartoes.CartoesConsulta;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsultaDeCartoes implements CartoesConsulta {

    private final CartaoRepository repositorio;

    public ConsultaDeCartoes(CartaoRepository repositorio) {
        this.repositorio = repositorio;
    }

    /** Sem transação aberta, a trava sairia junto com a consulta: MANDATORY recusa isso. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void travar(Long usuarioId) {
        repositorio.travarCartoesDoUsuario(usuarioId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CartaoResumo> buscar(Long usuarioId, Long cartaoId) {
        return repositorio.findByIdAndUsuarioId(cartaoId, usuarioId).map(ConsultaDeCartoes::resumo);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CartaoResumo> listar(Long usuarioId) {
        return repositorio.findAllByUsuarioId(usuarioId).stream().map(ConsultaDeCartoes::resumo).toList();
    }

    static CartaoResumo resumo(Cartao cartao) {
        return new CartaoResumo(cartao.getId(), cartao.getNome(), cartao.getCor() == null ? null : cartao.getCor().name(),
                cartao.getLimite(), cartao.getDiaFechamento(), cartao.getDiaVencimento(), cartao.getContaPagamentoId(),
                cartao.arquivado());
    }
}
