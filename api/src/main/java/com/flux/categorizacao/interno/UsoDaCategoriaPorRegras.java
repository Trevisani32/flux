package com.flux.categorizacao.interno;

import java.util.Collection;
import java.util.Optional;

import com.flux.categorias.UsoDaCategoria;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras nas categorias informadas (a principal e as subcategorias): "2 regras" (decisão 23). Vem
 * depois dos lançamentos na frase: "42 lançamentos e 2 regras".
 */
@Component
public class UsoDaCategoriaPorRegras implements UsoDaCategoria, Ordered {

    private final RegraRepository repositorio;

    public UsoDaCategoriaPorRegras(RegraRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> uso(Long usuarioId, Collection<Long> categoriaIds) {
        long quantidade = repositorio.countByUsuarioIdAndCategoriaIdIn(usuarioId, categoriaIds);
        if (quantidade == 0) {
            return Optional.empty();
        }
        return Optional.of(quantidade == 1 ? "1 regra" : quantidade + " regras");
    }

    @Override
    public int getOrder() {
        return 20;
    }
}
