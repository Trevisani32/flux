package com.flux.categorias.interno;

import java.net.URI;

import com.flux.categorias.interno.dto.CategoriaResponse;
import com.flux.categorias.interno.dto.CategoriasResponse;
import com.flux.categorias.interno.dto.CriarCategoriaRequest;
import com.flux.categorias.interno.dto.EditarCategoriaRequest;
import com.flux.compartilhado.seguranca.UsuarioAtual;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/categorias")
public class CategoriaController {

    private final UsuarioAtual usuarioAtual;
    private final CategoriaService categoriaService;

    public CategoriaController(UsuarioAtual usuarioAtual, CategoriaService categoriaService) {
        this.usuarioAtual = usuarioAtual;
        this.categoriaService = categoriaService;
    }

    @GetMapping
    public CategoriasResponse listar() {
        return categoriaService.listar(usuarioAtual.id());
    }

    @GetMapping("/{id}")
    public CategoriaResponse buscar(@PathVariable Long id) {
        return categoriaService.buscar(usuarioAtual.id(), id);
    }

    @PostMapping
    public ResponseEntity<CategoriaResponse> criar(@Valid @RequestBody CriarCategoriaRequest pedido) {
        CategoriaResponse criada = categoriaService.criar(usuarioAtual.id(), pedido);
        return ResponseEntity.created(URI.create("/api/categorias/" + criada.id())).body(criada);
    }

    @PutMapping("/{id}")
    public CategoriaResponse atualizar(@PathVariable Long id, @Valid @RequestBody EditarCategoriaRequest pedido) {
        return categoriaService.atualizar(usuarioAtual.id(), id, pedido);
    }

    @PostMapping("/{id}/arquivar")
    public CategoriaResponse arquivar(@PathVariable Long id) {
        return categoriaService.arquivar(usuarioAtual.id(), id);
    }

    @PostMapping("/{id}/reativar")
    public CategoriaResponse reativar(@PathVariable Long id) {
        return categoriaService.reativar(usuarioAtual.id(), id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        categoriaService.excluir(usuarioAtual.id(), id);
        return ResponseEntity.noContent().build();
    }
}
