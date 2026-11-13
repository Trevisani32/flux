package com.flux.lancamentos.interno;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.flux.cartoes.CartaoResumo;
import com.flux.categorias.CategoriaResumo;
import com.flux.contas.ContaResumo;
import com.flux.lancamentos.interno.dto.ItemDaLista;

/**
 * Monta os itens da lista com os nomes de contas, cartões e categorias, lidos uma vez por pedido
 * pelas consultas dos módulos donos (no máximo 50 contas, 20 cartões e 200 categorias por usuário).
 * O lançamento de fatura (Fatia 05a) precisa da compra dele, no mapa {@code compras}.
 */
final class Montagem {

    private final Map<Long, ContaResumo> contas;
    private final Map<Long, CartaoResumo> cartoes;
    private final Map<Long, CategoriaResumo> categorias;
    private final Map<Long, Compra> compras;
    private final LocalDate hoje;

    Montagem(List<ContaResumo> contas, List<CartaoResumo> cartoes, List<CategoriaResumo> categorias, Map<Long, Compra> compras,
             LocalDate hoje) {
        this.contas = contas.stream().collect(Collectors.toMap(ContaResumo::id, Function.identity()));
        this.cartoes = cartoes.stream().collect(Collectors.toMap(CartaoResumo::id, Function.identity()));
        this.categorias = categorias.stream().collect(Collectors.toMap(CategoriaResumo::id, Function.identity()));
        this.compras = compras;
        this.hoje = hoje;
    }

    ContaResumo conta(Long contaId) {
        return contas.get(contaId);
    }

    CartaoResumo cartao(Long cartaoId) {
        return cartoes.get(cartaoId);
    }

    /** Receita ou despesa: de conta ou, desde a Fatia 05a, de fatura. */
    ItemDaLista doLancamento(Lancamento lancamento) {
        return lancamento.deFatura() ? daFatura(lancamento) : daConta(lancamento);
    }

    private ItemDaLista daConta(Lancamento lancamento) {
        ContaResumo conta = contas.get(lancamento.getContaId());
        CategoriaResumo categoria = categoria(lancamento);
        return new ItemDaLista(lancamento.getTipo(), lancamento.getId(), lancamento.getData(), lancamento.getDescricao(),
                lancamento.getValor(), lancamento.getObservacao(), conta.id(), conta.nome(),
                categoria == null ? null : categoria.id(), categoria == null ? null : categoria.nome(),
                categoria == null ? null : categoria.nomeDaMae(), categoria != null && categoria.arquivada(),
                null, null, null, lancamento.getData().isAfter(hoje), conta.arquivada(), lancamento.getOrigem(),
                lancamento.getDescricaoBanco(), null, null, null, null, null, false, null, null, null);
    }

    /**
     * Fatia 05a (decisão 22): a parcela ou o estorno, na data do vencimento da fatura, com o cartão, a compra, o
     * número da parcela e a data da compra. Só leitura com o cartão arquivado (decisão 5).
     */
    private ItemDaLista daFatura(Lancamento lancamento) {
        CartaoResumo cartao = cartoes.get(lancamento.getCartaoId());
        Compra compra = compras.get(lancamento.getCompraId());
        CategoriaResumo categoria = categoria(lancamento);
        return new ItemDaLista(lancamento.getTipo(), lancamento.getId(), lancamento.getData(), lancamento.getDescricao(),
                lancamento.getValor(), lancamento.getObservacao(), null, null,
                categoria == null ? null : categoria.id(), categoria == null ? null : categoria.nome(),
                categoria == null ? null : categoria.nomeDaMae(), categoria != null && categoria.arquivada(),
                null, null, null, lancamento.getData().isAfter(hoje), cartao.arquivado(), lancamento.getOrigem(), null, null,
                cartao.id(), cartao.nome(), cartao.cor(), compra.getId(), compra.estorno(), lancamento.getNumeroParcela(),
                compra.getParcelas(), compra.getDataCompra());
    }

    /**
     * A transferência numa linha só (decisão 26). {@code referencia} é o lado que dá a data,
     * a descrição e a observação: o da conta filtrada ou, sem filtro de conta, a saída. Os
     * {@code lados} vão sempre na ordem saída, entrada (Fatia 04c).
     */
    ItemDaLista daTransferencia(Lancamento saida, Lancamento entrada, Lancamento referencia, String direcao) {
        ContaResumo origem = contas.get(saida.getContaId());
        ContaResumo destino = contas.get(entrada.getContaId());
        return new ItemDaLista(TipoLancamento.TRANSFERENCIA, saida.getTransferenciaId(), referencia.getData(),
                referencia.getDescricao(), entrada.getValor(), referencia.getObservacao(), origem.id(), origem.nome(),
                null, null, null, false, destino.id(), destino.nome(), direcao, referencia.getData().isAfter(hoje),
                origem.arquivada() || destino.arquivada(), referencia.getOrigem(), null,
                List.of(lado(saida, origem), lado(entrada, destino)), null, null, null, null, false, null, null, null);
    }

    private CategoriaResumo categoria(Lancamento lancamento) {
        return lancamento.getCategoriaId() == null ? null : categorias.get(lancamento.getCategoriaId());
    }

    private static ItemDaLista.Lado lado(Lancamento lancamento, ContaResumo conta) {
        return new ItemDaLista.Lado(conta.id(), conta.nome(), lancamento.getData(), lancamento.getOrigem(),
                lancamento.getDescricaoBanco(), lancamento.getObservacao());
    }
}
