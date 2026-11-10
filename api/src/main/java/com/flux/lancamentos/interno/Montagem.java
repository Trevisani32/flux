package com.flux.lancamentos.interno;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.flux.categorias.CategoriaResumo;
import com.flux.contas.ContaResumo;
import com.flux.lancamentos.interno.dto.ItemDaLista;

/**
 * Monta os itens da lista com os nomes de contas e categorias, lidos uma vez por pedido
 * pelas consultas dos módulos donos (no máximo 50 contas e 200 categorias por usuário).
 */
final class Montagem {

    private final Map<Long, ContaResumo> contas;
    private final Map<Long, CategoriaResumo> categorias;
    private final LocalDate hoje;

    Montagem(List<ContaResumo> contas, List<CategoriaResumo> categorias, LocalDate hoje) {
        this.contas = contas.stream().collect(Collectors.toMap(ContaResumo::id, Function.identity()));
        this.categorias = categorias.stream().collect(Collectors.toMap(CategoriaResumo::id, Function.identity()));
        this.hoje = hoje;
    }

    ContaResumo conta(Long contaId) {
        return contas.get(contaId);
    }

    /** Receita ou despesa. */
    ItemDaLista doLancamento(Lancamento lancamento) {
        ContaResumo conta = contas.get(lancamento.getContaId());
        CategoriaResumo categoria = lancamento.getCategoriaId() == null ? null : categorias.get(lancamento.getCategoriaId());
        return new ItemDaLista(lancamento.getTipo(), lancamento.getId(), lancamento.getData(), lancamento.getDescricao(),
                lancamento.getValor(), lancamento.getObservacao(), conta.id(), conta.nome(),
                categoria == null ? null : categoria.id(), categoria == null ? null : categoria.nome(),
                categoria == null ? null : categoria.nomeDaMae(), categoria != null && categoria.arquivada(),
                null, null, null, lancamento.getData().isAfter(hoje), conta.arquivada());
    }

    /**
     * A transferência numa linha só (decisão 26). {@code referencia} é o lado que dá a data,
     * a descrição e a observação: o da conta filtrada ou, sem filtro de conta, a saída.
     */
    ItemDaLista daTransferencia(Lancamento saida, Lancamento entrada, Lancamento referencia, String direcao) {
        ContaResumo origem = contas.get(saida.getContaId());
        ContaResumo destino = contas.get(entrada.getContaId());
        return new ItemDaLista(TipoLancamento.TRANSFERENCIA, saida.getTransferenciaId(), referencia.getData(),
                referencia.getDescricao(), entrada.getValor(), referencia.getObservacao(), origem.id(), origem.nome(),
                null, null, null, false, destino.id(), destino.nome(), direcao, referencia.getData().isAfter(hoje),
                origem.arquivada() || destino.arquivada());
    }
}
