package com.flux.categorias;

import java.util.Collection;
import java.util.Optional;

/**
 * Uso de categorias por outro módulo (lançamentos na Fatia 03b; regras na 04; orçamentos
 * na 07). Cada módulo que referencia categorias implementa. Categoria usada não pode ser
 * excluída, só arquivada.
 *
 * <p>Sem corrida entre conferir e excluir: o {@code CategoriaService} confere o uso e exclui
 * dentro da trava do usuário, {@code pg_advisory_xact_lock(hashtextextended('categorias:' ||
 * usuarioId, 0))}. Quem implementa grava um uso novo pegando antes essa mesma trava, na
 * mesma transação (com as duas travas, primeiro a de contas). A FK do uso para
 * {@code categorias} é {@code ON DELETE RESTRICT}, nunca {@code CASCADE}: um uso gravado
 * fora da trava faz a exclusão falhar, sem apagar nada.
 */
public interface UsoDaCategoria {

    /**
     * Descrição curta do uso somado das categorias informadas (a principal e as
     * subcategorias dela), para entrar na frase "… porque tem {descrição}." (ex.:
     * "42 lançamentos"), ou vazio se nenhuma é usada.
     */
    Optional<String> uso(Long usuarioId, Collection<Long> categoriaIds);
}
