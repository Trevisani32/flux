package com.flux.lancamentos.interno;

import com.flux.compartilhado.seguranca.UsuarioAtual;
import com.flux.lancamentos.interno.dto.AjusteRequest;
import com.flux.lancamentos.interno.dto.AjusteResponse;
import com.flux.lancamentos.interno.dto.FaturaResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Fatia 05a: a fatura de um mês (decisão 13) e o ajuste do fechamento dela (decisão 12). {@code mes} é "2026-12". */
@RestController
@RequestMapping("/api/cartoes/{cartaoId}/faturas/{mes}")
public class FaturaController {

    private final UsuarioAtual usuarioAtual;
    private final FaturaService faturaService;

    public FaturaController(UsuarioAtual usuarioAtual, FaturaService faturaService) {
        this.usuarioAtual = usuarioAtual;
        this.faturaService = faturaService;
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
}
