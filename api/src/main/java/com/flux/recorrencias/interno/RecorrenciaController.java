package com.flux.recorrencias.interno;

import java.net.URI;

import com.flux.compartilhado.seguranca.UsuarioAtual;
import com.flux.recorrencias.interno.dto.AConfirmarResponse;
import com.flux.recorrencias.interno.dto.ConfirmacaoResponse;
import com.flux.recorrencias.interno.dto.ConfirmarRequest;
import com.flux.recorrencias.interno.dto.CandidatosResponse;
import com.flux.recorrencias.interno.dto.HistoricoResponse;
import com.flux.recorrencias.interno.dto.LigarRequest;
import com.flux.recorrencias.interno.dto.OcorrenciaResponse;
import com.flux.recorrencias.interno.dto.PreviaRequest;
import com.flux.recorrencias.interno.dto.PreviaResponse;
import com.flux.recorrencias.interno.dto.RecorrenciaResponse;
import com.flux.recorrencias.interno.dto.RecorrenciasResponse;
import com.flux.recorrencias.interno.dto.SalvarRecorrenciaRequest;
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
@RequestMapping("/api/recorrencias")
public class RecorrenciaController {

    private final UsuarioAtual usuarioAtual;
    private final RecorrenciaService recorrencias;

    public RecorrenciaController(UsuarioAtual usuarioAtual, RecorrenciaService recorrencias) {
        this.usuarioAtual = usuarioAtual;
        this.recorrencias = recorrencias;
    }

    @GetMapping
    public RecorrenciasResponse listar() {
        return recorrencias.listar(usuarioAtual.id());
    }

    @PostMapping
    public ResponseEntity<RecorrenciaResponse> criar(@Valid @RequestBody SalvarRecorrenciaRequest pedido) {
        RecorrenciaResponse criada = recorrencias.criar(usuarioAtual.id(), pedido);
        return ResponseEntity.created(URI.create("/api/recorrencias/" + criada.id())).body(criada);
    }

    @GetMapping("/a-confirmar")
    public AConfirmarResponse aConfirmar() {
        return recorrencias.aConfirmar(usuarioAtual.id());
    }

    @PostMapping("/previa")
    public PreviaResponse previa(@Valid @RequestBody PreviaRequest pedido) {
        return recorrencias.previa(usuarioAtual.id(), pedido);
    }

    @GetMapping("/{id}")
    public RecorrenciaResponse buscar(@PathVariable Long id) {
        return recorrencias.buscar(usuarioAtual.id(), id);
    }

    @PutMapping("/{id}")
    public RecorrenciaResponse editar(@PathVariable Long id, @Valid @RequestBody SalvarRecorrenciaRequest pedido) {
        return recorrencias.editar(usuarioAtual.id(), id, pedido);
    }

    @PostMapping("/{id}/encerrar")
    public RecorrenciaResponse encerrar(@PathVariable Long id) {
        return recorrencias.encerrar(usuarioAtual.id(), id);
    }

    @PostMapping("/{id}/retomar")
    public RecorrenciaResponse retomar(@PathVariable Long id) {
        return recorrencias.retomar(usuarioAtual.id(), id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        recorrencias.excluir(usuarioAtual.id(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/ocorrencias/{periodo}/confirmar")
    public ConfirmacaoResponse confirmar(@PathVariable Long id, @PathVariable String periodo,
                                         @Valid @RequestBody ConfirmarRequest pedido) {
        return recorrencias.confirmar(usuarioAtual.id(), id, periodo, pedido);
    }

    @GetMapping("/{id}/historico")
    public HistoricoResponse historico(@PathVariable Long id) {
        return recorrencias.historico(usuarioAtual.id(), id);
    }

    @GetMapping("/{id}/ocorrencias/{periodo}/candidatos")
    public CandidatosResponse candidatos(@PathVariable Long id, @PathVariable String periodo) {
        return recorrencias.candidatos(usuarioAtual.id(), id, periodo);
    }

    @PostMapping("/{id}/ocorrencias/{periodo}/ligar")
    public ConfirmacaoResponse ligar(@PathVariable Long id, @PathVariable String periodo,
                                     @RequestBody LigarRequest pedido) {
        return recorrencias.ligar(usuarioAtual.id(), id, periodo, pedido);
    }

    @PostMapping("/{id}/ocorrencias/{periodo}/pular")
    public OcorrenciaResponse pular(@PathVariable Long id, @PathVariable String periodo) {
        return recorrencias.pular(usuarioAtual.id(), id, periodo);
    }

    @PostMapping("/{id}/ocorrencias/{periodo}/voltar-a-prever")
    public ResponseEntity<Void> voltarAPrever(@PathVariable Long id, @PathVariable String periodo) {
        recorrencias.voltarAPrever(usuarioAtual.id(), id, periodo);
        return ResponseEntity.noContent().build();
    }
}
