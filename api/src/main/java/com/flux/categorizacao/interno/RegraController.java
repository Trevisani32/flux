package com.flux.categorizacao.interno;

import java.net.URI;
import java.util.List;

import com.flux.categorizacao.interno.dto.AplicacaoResponse;
import com.flux.categorizacao.interno.dto.AplicadosResponse;
import com.flux.categorizacao.interno.dto.AplicarRequest;
import com.flux.categorizacao.interno.dto.OrdemRequest;
import com.flux.categorizacao.interno.dto.PreviaDaRegraRequest;
import com.flux.categorizacao.interno.dto.PreviaDaRegraResponse;
import com.flux.categorizacao.interno.dto.RegraResponse;
import com.flux.categorizacao.interno.dto.SalvarRegraRequest;
import com.flux.categorizacao.interno.dto.SugestaoResponse;
import com.flux.categorizacao.interno.dto.SugestoesRequest;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** As regras de categorização (decisão 39). */
@RestController
@RequestMapping("/api/regras")
public class RegraController {

    private final UsuarioAtual usuarioAtual;
    private final RegraService regras;
    private final AplicacaoDeRegras aplicacao;

    public RegraController(UsuarioAtual usuarioAtual, RegraService regras, AplicacaoDeRegras aplicacao) {
        this.usuarioAtual = usuarioAtual;
        this.regras = regras;
        this.aplicacao = aplicacao;
    }

    @GetMapping
    public List<RegraResponse> listar() {
        return regras.listar(usuarioAtual.id());
    }

    @PostMapping
    public ResponseEntity<RegraResponse> criar(@Valid @RequestBody SalvarRegraRequest pedido) {
        RegraResponse criada = regras.criar(usuarioAtual.id(), pedido);
        return ResponseEntity.created(URI.create("/api/regras/" + criada.id())).body(criada);
    }

    @PutMapping("/{id}")
    public RegraResponse atualizar(@PathVariable Long id, @Valid @RequestBody SalvarRegraRequest pedido) {
        return regras.atualizar(usuarioAtual.id(), id, pedido);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        regras.excluir(usuarioAtual.id(), id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/ordem")
    public List<RegraResponse> reordenar(@Valid @RequestBody OrdemRequest pedido) {
        return regras.reordenar(usuarioAtual.id(), pedido);
    }

    @PostMapping("/previa")
    public PreviaDaRegraResponse previa(@Valid @RequestBody PreviaDaRegraRequest pedido) {
        return regras.previa(usuarioAtual.id(), pedido);
    }

    @GetMapping("/aplicacao")
    public AplicacaoResponse previaDaAplicacao(@RequestParam(name = "regra", required = false) Long regraId) {
        return aplicacao.previa(usuarioAtual.id(), regraId);
    }

    @PostMapping("/aplicacao")
    public AplicadosResponse aplicar(@Valid @RequestBody AplicarRequest pedido) {
        return aplicacao.aplicar(usuarioAtual.id(), pedido);
    }

    @PostMapping("/sugestoes")
    public List<SugestaoResponse> sugestoes(@Valid @RequestBody SugestoesRequest pedido) {
        return regras.sugestoes(usuarioAtual.id(), pedido);
    }
}
