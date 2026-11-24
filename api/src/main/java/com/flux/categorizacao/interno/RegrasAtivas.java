package com.flux.categorizacao.interno;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.flux.categorias.CategoriaResumo;
import com.flux.cartoes.CartaoResumo;
import com.flux.contas.ContaResumo;

/**
 * As regras de um usuário que podem agir, na ordem da posição, para casar em memória (decisão 38):
 * a chave de cada regra já está gravada, e a do alvo é calculada uma vez por quem chama. Uma regra
 * com a categoria ou a conta arquivada está pausada e não casa (decisões 23 e 24).
 */
final class RegrasAtivas {

    private final List<Regra> ativas;

    RegrasAtivas(List<Regra> regrasEmOrdem, Map<Long, CategoriaResumo> categorias, Map<Long, ContaResumo> contas,
                  Map<Long, CartaoResumo> cartoes) {
        this.ativas = regrasEmOrdem.stream().filter(regra -> !pausada(regra, categorias, contas, cartoes)).toList();
    }

    RegrasAtivas(List<Regra> regrasEmOrdem, Map<Long, CategoriaResumo> categorias, Map<Long, ContaResumo> contas) {
        this(regrasEmOrdem, categorias, contas, Map.of());
    }

    /** Categoria arquivada (ou que sumiu), ou o filtro numa conta arquivada (ou que sumiu). */
    static boolean pausada(Regra regra, Map<Long, CategoriaResumo> categorias, Map<Long, ContaResumo> contas,
                           Map<Long, CartaoResumo> cartoes) {
        CategoriaResumo categoria = categorias.get(regra.getCategoriaId());
        if (categoria == null || categoria.arquivada()) {
            return true;
        }
        if (regra.getContaId() != null) {
            ContaResumo conta = contas.get(regra.getContaId());
            return conta == null || conta.arquivada();
        }
        if (regra.getCartaoId() == null) {
            return false;
        }
        CartaoResumo cartao = cartoes.get(regra.getCartaoId());
        return cartao == null || cartao.arquivado();
    }

    /** "RECEITA" para valor positivo, "DESPESA" para negativo (ADR 0002). */
    static String natureza(BigDecimal valor) {
        return valor.signum() > 0 ? "RECEITA" : "DESPESA";
    }

    /**
     * A primeira regra da natureza que casa com o alvo na conta, ou nula. {@code alvo} já é a
     * {@link Comparacao#chave(String)} da descrição do extrato (ou da descrição, no lançamento manual).
     */
    Regra primeira(Long contaId, Long cartaoId, String natureza, String alvo) {
        for (Regra regra : ativas) {
            if (regra.getNatureza().equals(natureza)
                    && (regra.getContaId() == null && regra.getCartaoId() == null
                    || regra.getContaId() != null && regra.getContaId().equals(contaId)
                    || regra.getCartaoId() != null && regra.getCartaoId().equals(cartaoId))
                    && regra.getCondicao().casa(alvo, regra.getTextoChave())) {
                return regra;
            }
        }
        return null;
    }

    static boolean pausada(Regra regra, Map<Long, CategoriaResumo> categorias, Map<Long, ContaResumo> contas) {
        return pausada(regra, categorias, contas, Map.of());
    }

    Regra primeira(Long contaId, String natureza, String alvo) {
        return primeira(contaId, null, natureza, alvo);
    }

    boolean vazia() {
        return ativas.isEmpty();
    }
}
