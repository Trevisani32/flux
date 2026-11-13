package com.flux.lancamentos.interno;

import java.net.URI;

import com.flux.compartilhado.seguranca.UsuarioAtual;
import com.flux.lancamentos.interno.dto.CompraResponse;
import com.flux.lancamentos.interno.dto.SalvarEstornoRequest;
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

/** Fatia 05a: estornos no cartão (decisão 20), uma receita na fatura da data deles. */
@RestController
@RequestMapping("/api/estornos")
public class EstornoController {

    private final UsuarioAtual usuarioAtual;
    private final CompraService compraService;

    public EstornoController(UsuarioAtual usuarioAtual, CompraService compraService) {
        this.usuarioAtual = usuarioAtual;
        this.compraService = compraService;
    }

    @PostMapping
    public ResponseEntity<CompraResponse> criar(@Valid @RequestBody SalvarEstornoRequest pedido) {
        CompraResponse criado = compraService.criar(usuarioAtual.id(), dados(pedido));
        return ResponseEntity.created(URI.create("/api/estornos/" + criado.id())).body(criado);
    }

    @GetMapping("/{id}")
    public CompraResponse buscar(@PathVariable Long id) {
        return compraService.buscar(usuarioAtual.id(), id, TipoCompra.ESTORNO);
    }

    @PutMapping("/{id}")
    public CompraResponse atualizar(@PathVariable Long id, @Valid @RequestBody SalvarEstornoRequest pedido) {
        return compraService.atualizar(usuarioAtual.id(), id, dados(pedido));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        compraService.excluir(usuarioAtual.id(), id, TipoCompra.ESTORNO);
        return ResponseEntity.noContent().build();
    }

    private static CompraService.Dados dados(SalvarEstornoRequest pedido) {
        return new CompraService.Dados(TipoCompra.ESTORNO, pedido.cartaoId(), pedido.data(), pedido.descricao(), pedido.valor(), 1, 1,
                pedido.categoriaId(), pedido.observacao());
    }
}
