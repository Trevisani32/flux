package com.flux.lancamentos.interno;

import com.flux.compartilhado.seguranca.UsuarioAtual;
import com.flux.lancamentos.interno.dto.ItemDaLista;
import com.flux.lancamentos.interno.dto.PagamentoResponse;
import com.flux.lancamentos.interno.dto.SalvarPagamentoRequest;
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

/**
 * Fatia 05b: o pagamento de fatura que já existe (decisões 33 e 37). Lançar é em
 * POST /api/cartoes/{id}/faturas/{mes}/pagamentos, e converter a despesa importada, em
 * POST /api/lancamentos/{id}/converter-em-pagamento.
 */
@RestController
@RequestMapping("/api/pagamentos")
public class PagamentoController {

    private final UsuarioAtual usuarioAtual;
    private final PagamentoService pagamentoService;

    public PagamentoController(UsuarioAtual usuarioAtual, PagamentoService pagamentoService) {
        this.usuarioAtual = usuarioAtual;
        this.pagamentoService = pagamentoService;
    }

    @GetMapping("/{id}")
    public PagamentoResponse buscar(@PathVariable Long id) {
        return pagamentoService.buscar(usuarioAtual.id(), id);
    }

    @PutMapping("/{id}")
    public PagamentoResponse atualizar(@PathVariable Long id, @Valid @RequestBody SalvarPagamentoRequest pedido) {
        return pagamentoService.atualizar(usuarioAtual.id(), id, pedido);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        pagamentoService.excluir(usuarioAtual.id(), id);
        return ResponseEntity.noContent().build();
    }

    /** Decisão 37: o pagamento convertido volta a ser a despesa importada; responde a despesa. */
    @PostMapping("/{id}/desfazer")
    public ItemDaLista desfazer(@PathVariable Long id) {
        return pagamentoService.desfazer(usuarioAtual.id(), id);
    }
}
