package com.flux.lancamentos.interno;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.flux.cartoes.CartaoResumo;
import com.flux.cartoes.CartoesConsulta;
import com.flux.lancamentos.LancamentosDasRecorrencias;
import com.flux.lancamentos.interno.dto.CompraResponse;
import com.flux.lancamentos.interno.dto.ItemDaLista;
import com.flux.lancamentos.interno.dto.SalvarLancamentoRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** A porta de lançamentos que as recorrências usam sem acessar as tabelas deste módulo diretamente. */
@Component
public class RecorrenciasNosLancamentos implements LancamentosDasRecorrencias {

    private final LancamentoService lancamentoService;
    private final CompraService compraService;
    private final RegrasDosLancamentos regras;
    private final LancamentoRepository lancamentos;
    private final CompraRepository compras;
    private final FaturaRepository faturas;
    private final CartoesConsulta cartoes;

    public RecorrenciasNosLancamentos(LancamentoService lancamentoService, CompraService compraService, RegrasDosLancamentos regras,
                                      LancamentoRepository lancamentos, CompraRepository compras, FaturaRepository faturas,
                                      CartoesConsulta cartoes) {
        this.lancamentoService = lancamentoService;
        this.compraService = compraService;
        this.regras = regras;
        this.lancamentos = lancamentos;
        this.compras = compras;
        this.faturas = faturas;
        this.cartoes = cartoes;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Lancado lancar(Long usuarioId, NovoLancamento novo) {
        regras.exigirLimiteTotal(usuarioId, 1);
        regras.exigirLimiteDosMeses(usuarioId, Map.of(YearMonth.from(novo.data()), 1L), "Este lançamento passaria");
        ItemDaLista item = lancamentoService.criar(usuarioId, new SalvarLancamentoRequest(TipoLancamento.valueOf(novo.tipo()),
                novo.valor(), novo.data(), novo.descricao(), novo.contaId(), novo.categoriaId(), novo.observacao()));
        return new Lancado(item.id(), item.data());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Comprado comprar(Long usuarioId, NovaCompra nova) {
        CompraResponse compra = compraService.criar(usuarioId, new CompraService.Dados(TipoCompra.COMPRA, nova.cartaoId(), nova.data(),
                nova.descricao(), nova.valor(), 1, 1, nova.categoriaId(), nova.observacao()));
        var primeira = compra.parcelasNoFlux().getFirst();
        return new Comprado(compra.id(), nova.data(), YearMonth.parse(primeira.mes()), primeira.vencimento());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Alvo> lancamentosDaConta(Long usuarioId, Long contaId, LocalDate de, LocalDate ate) {
        return lancamentos.findAllByUsuarioIdAndContaIdAndTipoInAndIgnoradoEmIsNullAndDataBetweenOrderByDataAscIdAsc(usuarioId,
                contaId, List.of(TipoLancamento.RECEITA, TipoLancamento.DESPESA), de, ate).stream().map(this::alvoDoLancamento)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Alvo> comprasDoCartao(Long usuarioId, Long cartaoId, LocalDate de, LocalDate ate) {
        return alvosDasCompras(usuarioId, compras.findAllByUsuarioIdAndCartaoIdAndTipoAndDataCompraBetweenOrderByDataCompraAscIdAsc(
                usuarioId, cartaoId, TipoCompra.COMPRA, de, ate));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Alvo> lancamentos(Long usuarioId, Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return lancamentos.findAllByUsuarioIdAndIdIn(usuarioId, ids).stream().map(this::alvoDoLancamento).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Alvo> compras(Long usuarioId, Collection<Long> ids) {
        return ids.isEmpty() ? List.of() : alvosDasCompras(usuarioId, compras.findAllByUsuarioIdAndIdIn(usuarioId, ids));
    }

    @Override
    @Transactional(readOnly = true)
    public List<FaturaDaData> faturasDe(Long usuarioId, Long cartaoId, List<LocalDate> datas) {
        CartaoResumo cartao = cartoes.buscar(usuarioId, cartaoId).orElse(null);
        if (cartao == null) {
            return List.of();
        }
        FaturasDoCartao doCartao = FaturasDoCartao.carregar(faturas, usuarioId, cartao);
        return datas.stream().map(data -> {
            YearMonth mes = doCartao.faturaDe(data);
            var datasDaFatura = doCartao.datas(mes);
            return new FaturaDaData(data, mes, datasDaFatura.fechamento(), datasDaFatura.vencimento());
        }).toList();
    }

    private List<Alvo> alvosDasCompras(Long usuarioId, List<Compra> comprasDoUsuario) {
        if (comprasDoUsuario.isEmpty()) {
            return List.of();
        }
        List<Long> ids = comprasDoUsuario.stream().map(Compra::getId).toList();
        Map<Long, Lancamento> primeiraParcela = new HashMap<>();
        lancamentos.findAllByUsuarioIdAndCompraIdInOrderByCompraIdAscNumeroParcelaAsc(usuarioId, ids)
                .forEach(parcela -> primeiraParcela.putIfAbsent(parcela.getCompraId(), parcela));
        List<Alvo> resultado = new ArrayList<>();
        for (Compra compra : comprasDoUsuario) {
            Lancamento primeira = primeiraParcela.get(compra.getId());
            if (primeira == null) {
                continue;
            }
            BigDecimal valor = compra.getTipo() == TipoCompra.COMPRA ? compra.getValorTotal().negate() : compra.getValorTotal();
            resultado.add(new Alvo(compra.getId(), null, compra.getCartaoId(), compra.getTipo().name(), compra.getDataCompra(), valor,
                    primeira.getDescricao(), compra.getImportacaoId() == null ? "MANUAL" : "IMPORTADO", primeira.getCategoriaId(),
                    YearMonth.from(primeira.getData()), false));
        }
        return resultado;
    }

    private Alvo alvoDoLancamento(Lancamento lancamento) {
        return new Alvo(lancamento.getId(), lancamento.getContaId(), lancamento.getCartaoId(), lancamento.getTipo().name(),
                lancamento.getData(), lancamento.getValor(), lancamento.getDescricao(), lancamento.getOrigem().name(),
                lancamento.getCategoriaId(), YearMonth.from(lancamento.getData()), lancamento.getIgnoradoEm() != null);
    }
}
