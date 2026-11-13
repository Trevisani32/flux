package com.flux.lancamentos.interno;

import java.net.URI;

import com.flux.compartilhado.seguranca.UsuarioAtual;
import com.flux.lancamentos.interno.dto.CompraResponse;
import com.flux.lancamentos.interno.dto.PreviaDaCompraRequest;
import com.flux.lancamentos.interno.dto.PreviaDaCompraResponse;
import com.flux.lancamentos.interno.dto.SalvarCompraRequest;
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

/** Fatia 05a: compras no cartão (decisões 14 a 19). */
@RestController
@RequestMapping("/api/compras")
public class CompraController {

    private final UsuarioAtual usuarioAtual;
    private final CompraService compraService;

    public CompraController(UsuarioAtual usuarioAtual, CompraService compraService) {
        this.usuarioAtual = usuarioAtual;
        this.compraService = compraService;
    }

    /** Decisão 18: a prévia das parcelas, sem gravar. */
    @PostMapping("/previa")
    public PreviaDaCompraResponse previa(@Valid @RequestBody PreviaDaCompraRequest pedido) {
        return compraService.previa(usuarioAtual.id(), pedido);
    }

    @PostMapping
    public ResponseEntity<CompraResponse> criar(@Valid @RequestBody SalvarCompraRequest pedido) {
        CompraResponse criada = compraService.criar(usuarioAtual.id(), dados(pedido));
        return ResponseEntity.created(URI.create("/api/compras/" + criada.id())).body(criada);
    }

    @GetMapping("/{id}")
    public CompraResponse buscar(@PathVariable Long id) {
        return compraService.buscar(usuarioAtual.id(), id, TipoCompra.COMPRA);
    }

    @PutMapping("/{id}")
    public CompraResponse atualizar(@PathVariable Long id, @Valid @RequestBody SalvarCompraRequest pedido) {
        return compraService.atualizar(usuarioAtual.id(), id, dados(pedido));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        compraService.excluir(usuarioAtual.id(), id, TipoCompra.COMPRA);
        return ResponseEntity.noContent().build();
    }

    private static CompraService.Dados dados(SalvarCompraRequest pedido) {
        return new CompraService.Dados(TipoCompra.COMPRA, pedido.cartaoId(), pedido.data(), pedido.descricao(), pedido.valorTotal(),
                pedido.parcelas(), pedido.parcelaInicial(), pedido.categoriaId(), pedido.observacao());
    }
}
