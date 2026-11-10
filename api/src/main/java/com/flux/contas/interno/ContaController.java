package com.flux.contas.interno;

import java.net.URI;

import com.flux.compartilhado.seguranca.UsuarioAtual;
import com.flux.contas.interno.dto.ContaResponse;
import com.flux.contas.interno.dto.ContasResponse;
import com.flux.contas.interno.dto.SalvarContaRequest;
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
@RequestMapping("/api/contas")
public class ContaController {

    private final UsuarioAtual usuarioAtual;
    private final ContaService contaService;

    public ContaController(UsuarioAtual usuarioAtual, ContaService contaService) {
        this.usuarioAtual = usuarioAtual;
        this.contaService = contaService;
    }

    @GetMapping
    public ContasResponse listar() {
        return contaService.listar(usuarioAtual.id());
    }

    @GetMapping("/{id}")
    public ContaResponse buscar(@PathVariable Long id) {
        return contaService.buscar(usuarioAtual.id(), id);
    }

    @PostMapping
    public ResponseEntity<ContaResponse> criar(@Valid @RequestBody SalvarContaRequest pedido) {
        ContaResponse criada = contaService.criar(usuarioAtual.id(), pedido);
        return ResponseEntity.created(URI.create("/api/contas/" + criada.id())).body(criada);
    }

    @PutMapping("/{id}")
    public ContaResponse atualizar(@PathVariable Long id, @Valid @RequestBody SalvarContaRequest pedido) {
        return contaService.atualizar(usuarioAtual.id(), id, pedido);
    }

    @PostMapping("/{id}/arquivar")
    public ContaResponse arquivar(@PathVariable Long id) {
        return contaService.arquivar(usuarioAtual.id(), id);
    }

    @PostMapping("/{id}/reativar")
    public ContaResponse reativar(@PathVariable Long id) {
        return contaService.reativar(usuarioAtual.id(), id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        contaService.excluir(usuarioAtual.id(), id);
        return ResponseEntity.noContent().build();
    }
}
