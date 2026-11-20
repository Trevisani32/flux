package com.flux.cartoes.interno;

import java.net.URI;

import com.flux.cartoes.interno.dto.CartaoResponse;
import com.flux.cartoes.interno.dto.CartoesResponse;
import com.flux.cartoes.interno.dto.DiasRequest;
import com.flux.cartoes.interno.dto.PreviaDosDiasResponse;
import com.flux.cartoes.interno.dto.SalvarCartaoRequest;
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
@RequestMapping("/api/cartoes")
public class CartaoController {

    private final UsuarioAtual usuarioAtual;
    private final CartaoService cartaoService;

    public CartaoController(UsuarioAtual usuarioAtual, CartaoService cartaoService) {
        this.usuarioAtual = usuarioAtual;
        this.cartaoService = cartaoService;
    }

    @GetMapping
    public CartoesResponse listar() {
        return cartaoService.listar(usuarioAtual.id());
    }

    @GetMapping("/{id}")
    public CartaoResponse buscar(@PathVariable Long id) {
        return cartaoService.buscar(usuarioAtual.id(), id);
    }

    @PostMapping
    public ResponseEntity<CartaoResponse> criar(@Valid @RequestBody SalvarCartaoRequest pedido) {
        CartaoResponse criado = cartaoService.criar(usuarioAtual.id(), pedido);
        return ResponseEntity.created(URI.create("/api/cartoes/" + criado.id())).body(criado);
    }

    @PutMapping("/{id}")
    public CartaoResponse atualizar(@PathVariable Long id, @Valid @RequestBody SalvarCartaoRequest pedido) {
        return cartaoService.atualizar(usuarioAtual.id(), id, pedido);
    }

    /** Decisão 7: o aviso do diálogo de edição, sem gravar. */
    @PostMapping("/{id}/previa-dos-dias")
    public PreviaDosDiasResponse previaDosDias(@PathVariable Long id, @Valid @RequestBody DiasRequest pedido) {
        return cartaoService.previaDosDias(usuarioAtual.id(), id, pedido);
    }

    @PostMapping("/{id}/arquivar")
    public CartaoResponse arquivar(@PathVariable Long id) {
        return cartaoService.arquivar(usuarioAtual.id(), id);
    }

    @PostMapping("/{id}/reativar")
    public CartaoResponse reativar(@PathVariable Long id) {
        return cartaoService.reativar(usuarioAtual.id(), id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        cartaoService.excluir(usuarioAtual.id(), id);
        return ResponseEntity.noContent().build();
    }
}
