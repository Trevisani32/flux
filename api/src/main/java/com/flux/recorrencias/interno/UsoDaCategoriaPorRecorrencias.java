package com.flux.recorrencias.interno;

import java.util.Collection;
import java.util.Optional;

import com.flux.categorias.UsoDaCategoria;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class UsoDaCategoriaPorRecorrencias implements UsoDaCategoria, Ordered {

    private final RecorrenciaRepository recorrencias;

    public UsoDaCategoriaPorRecorrencias(RecorrenciaRepository recorrencias) {
        this.recorrencias = recorrencias;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> uso(Long usuarioId, Collection<Long> categoriaIds) {
        long quantidade = recorrencias.countByUsuarioIdAndCategoriaIdIn(usuarioId, categoriaIds);
        return quantidade == 0 ? Optional.empty()
                : Optional.of(quantidade == 1 ? "1 recorrência" : quantidade + " recorrências");
    }

    @Override
    public int getOrder() {
        return 30;
    }
}
