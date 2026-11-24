package com.flux.cartoes;

/** Consulta usada pela troca dos dias para saber se o cartão já recebeu uma importação de fatura. */
public interface ImportacoesDoCartao {

    boolean existe(Long usuarioId, Long cartaoId);
}
