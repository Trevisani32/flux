package com.flux.cartoes.interno;

import java.util.Optional;

import com.flux.contas.UsoDaConta;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Decisão 4: a conta de pagamento é uso da conta. Entra na frase como papel: "Esta conta não pode ser
 * excluída porque é a conta de pagamento de 1 cartão." Conta os ativos e os arquivados (a FK é RESTRICT).
 */
@Component
public class UsoDaContaPorCartoes implements UsoDaConta, Ordered {

    private final CartaoRepository repositorio;

    public UsoDaContaPorCartoes(CartaoRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    public Optional<String> uso(Long usuarioId, Long contaId) {
        return Optional.empty();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> papel(Long usuarioId, Long contaId) {
        long quantidade = repositorio.countByUsuarioIdAndContaPagamentoId(usuarioId, contaId);
        if (quantidade == 0) {
            return Optional.empty();
        }
        return Optional.of("a conta de pagamento de " + (quantidade == 1 ? "1 cartão" : quantidade + " cartões"));
    }

    @Override
    public int getOrder() {
        return 30;
    }
}
