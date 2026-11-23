package com.flux.lancamentos.interno;

import java.net.URI;

import com.flux.compartilhado.seguranca.UsuarioAtual;
import com.flux.lancamentos.interno.dto.AjusteRequest;
import com.flux.lancamentos.interno.dto.AjusteResponse;
import com.flux.lancamentos.interno.dto.FaturaResponse;
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
 * Fatia 05a: a fatura de um mês (decisão 13) e o ajuste do fechamento dela (decisão 12); Fatia 05b: o pagamento
 * (decisão 30) e a marca de paga fora do Flux (decisão 36). {@code mes} é "2026-12".
 */
@RestController
@RequestMapping("/api/cartoes/{cartaoId}/faturas/{mes}")
public class FaturaController {

    private final UsuarioAtual usuarioAtual;
    private final FaturaService faturaService;
    private final PagamentoService pagamentoService;

    public FaturaController(UsuarioAtual usuarioAtual, FaturaService faturaService, PagamentoService pagamentoService) {
        this.usuarioAtual = usuarioAtual;
        this.faturaService = faturaService;
        this.pagamentoService = pagamentoService;
    }

    @GetMapping
    public FaturaResponse buscar(@PathVariable Long cartaoId, @PathVariable String mes) {
        return faturaService.buscar(usuarioAtual.id(), cartaoId, mes);
    }

    @PutMapping("/fechamento")
    public AjusteResponse ajustar(@PathVariable Long cartaoId, @PathVariable String mes, @Valid @RequestBody AjusteRequest pedido) {
        return faturaService.ajustar(usuarioAtual.id(), cartaoId, mes, pedido.data());
    }

    @DeleteMapping("/fechamento")
    public AjusteResponse voltarAoNormal(@PathVariable Long cartaoId, @PathVariable String mes) {
        return faturaService.voltarAoNormal(usuarioAtual.id(), cartaoId, mes);
    }

    /** Decisão 30: "Pagar fatura"; responde o pagamento criado. */
    @PostMapping("/pagamentos")
    public ResponseEntity<PagamentoResponse> pagar(@PathVariable Long cartaoId, @PathVariable String mes,
                                                   @Valid @RequestBody SalvarPagamentoRequest pedido) {
        PagamentoResponse criado = pagamentoService.criar(usuarioAtual.id(), cartaoId, mes, pedido);
        return ResponseEntity.created(URI.create("/api/pagamentos/" + criado.id())).body(criado);
    }

    /** Decisão 36: "Marcar como paga fora do Flux"; responde a fatura. */
    @PutMapping("/paga-fora")
    public FaturaResponse marcarPagaFora(@PathVariable Long cartaoId, @PathVariable String mes) {
        return faturaService.marcarPagaFora(usuarioAtual.id(), cartaoId, mes);
    }

    /** Decisão 36: "Desmarcar"; responde a fatura. */
    @DeleteMapping("/paga-fora")
    public FaturaResponse desmarcarPagaFora(@PathVariable Long cartaoId, @PathVariable String mes) {
        return faturaService.desmarcarPagaFora(usuarioAtual.id(), cartaoId, mes);
    }
}
