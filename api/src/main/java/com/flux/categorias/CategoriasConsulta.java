package com.flux.categorias;

import java.util.List;
import java.util.Optional;

/**
 * Leitura de categorias para outros módulos (lançamentos, desde a Fatia 03b). Categoria de
 * outro usuário é tratada como inexistente (ADR 0007).
 */
public interface CategoriasConsulta {

    /**
     * Trava as alterações de categorias do usuário até o fim da transação: é a mesma trava
     * que a exclusão de categoria pega (ADR 0012). Com as duas travas, pegue antes a de
     * contas. Exige uma transação aberta.
     */
    void travar(Long usuarioId);

    /** A categoria do usuário, ativa ou arquivada; vazio se não existe ou é de outro usuário. */
    Optional<CategoriaResumo> buscar(Long usuarioId, Long categoriaId);

    /** Todas as categorias do usuário, das duas naturezas, ativas e arquivadas, sem ordem definida. */
    List<CategoriaResumo> listar(Long usuarioId);
}
