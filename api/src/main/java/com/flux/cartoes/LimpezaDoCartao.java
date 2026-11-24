package com.flux.cartoes;

/** Limpezas de módulos que precisam acontecer antes de excluir um cartão sem uso. */
public interface LimpezaDoCartao {

    void antesDeExcluir(Long usuarioId, Long cartaoId);
}
