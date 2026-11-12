package com.flux.lancamentos.interno;

import java.net.URI;
import java.util.List;

import com.flux.compartilhado.seguranca.UsuarioAtual;
import com.flux.lancamentos.interno.dto.ItemDaLista;
import com.flux.lancamentos.interno.dto.SalvarTransferenciaRequest;
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
@RequestMapping("/api/transferencias")
public class TransferenciaController {

    private final UsuarioAtual usuarioAtual;
    private final TransferenciaService transferenciaService;
    private final ConversaoService conversaoService;

    public TransferenciaController(UsuarioAtual usuarioAtual, TransferenciaService transferenciaService, ConversaoService conversaoService) {
        this.usuarioAtual = usuarioAtual;
        this.transferenciaService = transferenciaService;
        this.conversaoService = conversaoService;
    }

    @GetMapping("/{id}")
    public ItemDaLista buscar(@PathVariable Long id) {
        return transferenciaService.buscar(usuarioAtual.id(), id);
    }

    @PostMapping
    public ResponseEntity<ItemDaLista> criar(@Valid @RequestBody SalvarTransferenciaRequest pedido) {
        ItemDaLista criada = transferenciaService.criar(usuarioAtual.id(), pedido);
        return ResponseEntity.created(URI.create("/api/transferencias/" + criada.id())).body(criada);
    }

    @PutMapping("/{id}")
    public ItemDaLista atualizar(@PathVariable Long id, @Valid @RequestBody SalvarTransferenciaRequest pedido) {
        return transferenciaService.atualizar(usuarioAtual.id(), id, pedido);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        transferenciaService.excluir(usuarioAtual.id(), id);
        return ResponseEntity.noContent().build();
    }

    /** Fatia 04c: desfaz a convertida; responde os dois lançamentos, a saída primeiro (decisão 32). */
    @PostMapping("/{id}/desfazer")
    public List<ItemDaLista> desfazer(@PathVariable Long id) {
        return conversaoService.desfazer(usuarioAtual.id(), id);
    }
}