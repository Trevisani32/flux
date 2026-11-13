package com.flux.cartoes;

import java.util.Optional;

/**
 * Uso de um cartão por outro módulo (as compras e os estornos, desde a Fatia 05a). Cartão usado não pode
 * ser excluído, só arquivado (decisão 5). Quem implementa grava um uso novo pegando antes a trava de
 * cartões ({@link CartoesConsulta#travar}), e a FK do uso é {@code ON DELETE RESTRICT}.
 */
public interface UsoDoCartao {

    /**
     * Descrição curta do uso, para a frase "Este cartão não pode ser excluído porque tem {descrição}."
     * (ex.: "12 compras e 1 estorno"), ou vazio se o cartão não é usado.
     */
    Optional<String> uso(Long usuarioId, Long cartaoId);
}
