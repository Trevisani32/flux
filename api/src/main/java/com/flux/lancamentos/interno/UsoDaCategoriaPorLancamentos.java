package com.flux.lancamentos.interno;

import java.util.Collection;
import java.util.Optional;

import com.flux.categorias.UsoDaCategoria;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Lançamentos nas categorias informadas (a principal e as subcategorias): "42 lançamentos". */
@Component
public class UsoDaCategoriaPorLancamentos implements UsoDaCategoria, Ordered {

    private final LancamentoRepository repositorio;

    public UsoDaCategoriaPorLancamentos(LancamentoRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> uso(Long usuarioId, Collection<Long> categoriaIds) {
        long quantidade = repositorio.countByUsuarioIdAndCategoriaIdIn(usuarioId, categoriaIds);
        return quantidade == 0 ? Optional.empty() : Optional.of(Quantidades.lancamentos(quantidade));
    }

    @Override
    public int getOrder() {
        return 10;
    }
}
