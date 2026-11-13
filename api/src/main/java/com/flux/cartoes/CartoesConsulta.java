package com.flux.cartoes;

import java.util.List;
import java.util.Optional;

/**
 * Leitura de cartões para outros módulos (lançamentos, desde a Fatia 05a). Cartão de outro usuário é
 * tratado como inexistente (ADR 0007).
 */
public interface CartoesConsulta {

    /**
     * Trava as alterações de cartões do usuário até o fim da transação: é a trava que arquivar, excluir,
     * editar os dias e ajustar um fechamento pegam (decisão 49, chave {@code 'cartoes:' || usuarioId}).
     * Quem grava uma compra ou um estorno chama isto antes de ler o cartão. Com mais de uma trava, a
     * ordem é contas, cartões e categorias. Exige uma transação aberta.
     */
    void travar(Long usuarioId);

    /** O cartão do usuário, ativo ou arquivado; vazio se não existe ou é de outro usuário. */
    Optional<CartaoResumo> buscar(Long usuarioId, Long cartaoId);

    /** Todos os cartões do usuário, ativos e arquivados, sem ordem definida. */
    List<CartaoResumo> listar(Long usuarioId);
}
