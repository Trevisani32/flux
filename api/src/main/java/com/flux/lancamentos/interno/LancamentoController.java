package com.flux.lancamentos.interno;

import java.net.URI;
import java.time.YearMonth;
import java.util.List;

import com.flux.compartilhado.seguranca.UsuarioAtual;
import com.flux.lancamentos.interno.dto.ConverterEmPagamentoRequest;
import com.flux.lancamentos.interno.dto.ConverterRequest;
import com.flux.lancamentos.interno.dto.FaturasParaPagamentoResponse;
import com.flux.lancamentos.interno.dto.ItemDaLista;
import com.flux.lancamentos.interno.dto.ListaDoMesResponse;
import com.flux.lancamentos.interno.dto.PagamentoResponse;
import com.flux.lancamentos.interno.dto.ParesResponse;
import com.flux.lancamentos.interno.dto.RestaurarRequest;
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
    private final ConversaoService conversaoService;
    private final PagamentoService pagamentoService;

    public LancamentoController(UsuarioAtual usuarioAtual, LancamentoService lancamentoService, ListaDosLancamentos lista,
                                ConversaoService conversaoService, PagamentoService pagamentoService) {
        this.usuarioAtual = usuarioAtual;
        this.lancamentoService = lancamentoService;
        this.lista = lista;
        this.conversaoService = conversaoService;
        this.pagamentoService = pagamentoService;
    }

    /**
     * ?mes=2026-11&conta=3&categoria=12|sem&tipo=receita|despesa|transferencia|pagamento&busca=texto, todos opcionais;
     * desde a Fatia 05a, ?cartao=2 no lugar de ?conta= (decisão 23); desde a 05b, o tipo "pagamento" (decisão 32).
     */
    @GetMapping
    public ListaDoMesResponse listar(@RequestParam(required = false) YearMonth mes,
                                     @RequestParam(required = false) Long conta,
                                     @RequestParam(required = false) Long cartao,
                                     @RequestParam(required = false) String categoria,
                                     @RequestParam(required = false) String tipo,
                                     @RequestParam(required = false) String busca,
                                     @RequestParam(defaultValue = "true") boolean previstos) {
        return lista.listar(usuarioAtual.id(), FiltroDaLista.de(mes, conta, cartao, categoria, tipo, busca, previstos));
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

    /** O manual sai de vez; o importado é ignorado (decisão 30). */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        lancamentoService.excluir(usuarioAtual.id(), id);
        return ResponseEntity.noContent().build();
    }

    /** Volta um importado ignorado para a lista; o corpo ({"categoriaId": ...}) é opcional. */
    @PostMapping("/{id}/restaurar")
    public ItemDaLista restaurar(@PathVariable Long id, @RequestBody(required = false) RestaurarRequest pedido) {
        return lancamentoService.restaurar(usuarioAtual.id(), id, pedido);
    }

    /** Fatia 04c: o importado e os pares possíveis para virar transferência (decisão 27). */
    @GetMapping("/{id}/pares")
    public ParesResponse pares(@PathVariable Long id) {
        return conversaoService.pares(usuarioAtual.id(), id);
    }

    /** Fatia 04c: liga o importado a um par ou a um lado novo; responde a transferência criada. */
    @PostMapping("/{id}/converter")
    public ResponseEntity<ItemDaLista> converter(@PathVariable Long id, @Valid @RequestBody ConverterRequest pedido) {
        ItemDaLista criada = conversaoService.converter(usuarioAtual.id(), id, pedido);
        return ResponseEntity.created(URI.create("/api/transferencias/" + criada.id())).body(criada);
    }

    /** Fatia 05b: a despesa importada e as faturas que ela pode pagar, por cartão (decisão 37). */
    @GetMapping("/{id}/faturas-para-pagamento")
    public FaturasParaPagamentoResponse faturasParaPagamento(@PathVariable Long id) {
        return pagamentoService.faturasParaPagamento(usuarioAtual.id(), id);
    }

    /** Fatia 05b: a despesa importada vira o pagamento da fatura escolhida (decisão 37); responde o pagamento. */
    @PostMapping("/{id}/converter-em-pagamento")
    public ResponseEntity<PagamentoResponse> converterEmPagamento(@PathVariable Long id,
                                                                  @Valid @RequestBody ConverterEmPagamentoRequest pedido) {
        PagamentoResponse criado = pagamentoService.converter(usuarioAtual.id(), id, pedido.cartaoId(), pedido.mes());
        return ResponseEntity.created(URI.create("/api/pagamentos/" + criado.id())).body(criado);
    }
}
