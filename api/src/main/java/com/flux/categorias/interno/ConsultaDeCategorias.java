package com.flux.categorias.interno;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.flux.categorias.CategoriaResumo;
import com.flux.categorias.CategoriasConsulta;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsultaDeCategorias implements CategoriasConsulta {

    private final CategoriaRepository repositorio;

    public ConsultaDeCategorias(CategoriaRepository repositorio) {
        this.repositorio = repositorio;
    }

    /** Sem transação aberta, a trava sairia junto com a consulta: MANDATORY recusa isso. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void travar(Long usuarioId) {
        repositorio.travarCategoriasDoUsuario(usuarioId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CategoriaResumo> buscar(Long usuarioId, Long categoriaId) {
        return listar(usuarioId).stream().filter(categoria -> categoria.id().equals(categoriaId)).findFirst();
    }

    /** No máximo 200 categorias por usuário (decisão 26 da 03a): lê todas de uma vez. */
    @Override
    @Transactional(readOnly = true)
    public List<CategoriaResumo> listar(Long usuarioId) {
        List<Categoria> todas = repositorio.findAllByUsuarioId(usuarioId);
        Map<Long, String> nomes = todas.stream().collect(Collectors.toMap(Categoria::getId, Categoria::getNome));
        Function<Categoria, CategoriaResumo> resumo = categoria -> new CategoriaResumo(categoria.getId(), categoria.getNome(),
                categoria.getNatureza().name(), categoria.getCategoriaMaeId(),
                categoria.principal() ? null : nomes.get(categoria.getCategoriaMaeId()), categoria.arquivada());
        return todas.stream().map(resumo).toList();
    }
}
