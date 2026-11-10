package com.flux.lancamentos.interno;

import java.net.URI;
import java.time.YearMonth;
import java.util.List;

import com.flux.compartilhado.seguranca.UsuarioAtual;
import com.flux.lancamentos.interno.dto.ItemDaLista;
import com.flux.lancamentos.interno.dto.ListaDoMesResponse;
import com.flux.lancamentos.interno.dto.SalvarLancamentoRequest;
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

@RestController
@RequestMapping("/api/lancamentos")
public class LancamentoController {

    private final UsuarioAtual usuarioAtual;
    private final LancamentoService lancamentoService;
    private final ListaDosLancamentos lista;

    public LancamentoController(UsuarioAtual usuarioAtual, LancamentoService lancamentoService, ListaDosLancamentos lista) {
        this.usuarioAtual = usuarioAtual;
        this.lancamentoService = lancamentoService;
        this.lista = lista;
    }

    /** ?mes=2026-11&conta=3&categoria=12|sem&tipo=receita|despesa|transferencia&busca=texto, todos opcionais. */
    @GetMapping
    public ListaDoMesResponse listar(@RequestParam(required = false) YearMonth mes,
                                     @RequestParam(required = false) Long conta,
                                     @RequestParam(required = false) String categoria,
                                     @RequestParam(required = false) String tipo,
                                     @RequestParam(required = false) String busca) {
        return lista.listar(usuarioAtual.id(), FiltroDaLista.de(mes, conta, categoria, tipo, busca));
    }

    @GetMapping("/ultimos")
    public List<ItemDaLista> ultimos() {
        return lista.ultimos(usuarioAtual.id());
    }

    @GetMapping("/{id}")
    public ItemDaLista buscar(@PathVariable Long id) {
        return lancamentoService.buscar(usuarioAtual.id(), id);
    }

    @PostMapping
    public ResponseEntity<ItemDaLista> criar(@Valid @RequestBody SalvarLancamentoRequest pedido) {
        ItemDaLista criado = lancamentoService.criar(usuarioAtual.id(), pedido);
        return ResponseEntity.created(URI.create("/api/lancamentos/" + criado.id())).body(criado);
    }

    @PutMapping("/{id}")
    public ItemDaLista atualizar(@PathVariable Long id, @Valid @RequestBody SalvarLancamentoRequest pedido) {
        return lancamentoService.atualizar(usuarioAtual.id(), id, pedido);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        lancamentoService.excluir(usuarioAtual.id(), id);
        return ResponseEntity.noContent().build();
    }
}
