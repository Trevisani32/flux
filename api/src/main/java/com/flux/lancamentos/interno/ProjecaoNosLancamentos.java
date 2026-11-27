package com.flux.lancamentos.interno;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.flux.cartoes.CartaoResumo;
import com.flux.lancamentos.LancamentosDaProjecao;
import com.flux.lancamentos.LancamentosDaProjecao.CompraPrevista;
import com.flux.lancamentos.LancamentosDaProjecao.FaturaProjetada;
import com.flux.lancamentos.LancamentosDaProjecao.LancamentoFuturo;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ProjecaoNosLancamentos implements LancamentosDaProjecao {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final LancamentoRepository lancamentos;
    private final FaturaRepository faturas;

    public ProjecaoNosLancamentos(LancamentoRepository lancamentos, FaturaRepository faturas) {
        this.lancamentos = lancamentos;
        this.faturas = faturas;
    }

    @Override
    @Transactional(readOnly = true)
    public List<LancamentoFuturo> futuros(Long usuarioId, Collection<Long> contaIds, LocalDate de, LocalDate ate) {
        if (contaIds.isEmpty() || de.isAfter(ate)) {
            return List.of();
        }
        List<Lancamento> encontrados = lancamentos
                .findAllByUsuarioIdAndContaIdInAndTipoInAndIgnoradoEmIsNullAndDataBetweenOrderByDataAscIdAsc(usuarioId, contaIds,
                        List.of(TipoLancamento.RECEITA, TipoLancamento.DESPESA, TipoLancamento.TRANSFERENCIA), de, ate);
        Set<Long> transferencias = encontrados.stream().map(Lancamento::getTransferenciaId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, List<Lancamento>> lados = transferencias.isEmpty() ? Map.of() : lancamentos
                .findAllByUsuarioIdAndTransferenciaIdIn(usuarioId, transferencias).stream()
                .collect(Collectors.groupingBy(Lancamento::getTransferenciaId));
        return encontrados.stream().map(lancamento -> new LancamentoFuturo(lancamento.getId(), lancamento.getContaId(),
                lancamento.getData(), lancamento.getTipo().name(), lancamento.getValor().setScale(2), lancamento.getDescricao(),
                outraConta(lancamento, lados))).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FaturaProjetada> faturas(Long usuarioId, List<CartaoResumo> cartoes, LocalDate ate,
                                         List<CompraPrevista> previstas) {
        if (cartoes.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Fatura>> gravadas = faturas.findAllByUsuarioId(usuarioId).stream()
                .collect(Collectors.groupingBy(Fatura::getCartaoId));
        Map<Long, TotalDaFatura> totais = lancamentos.totaisDasFaturas(usuarioId).stream()
                .collect(Collectors.toMap(TotalDaFatura::faturaId, Function.identity()));
        Map<Long, PagoDaFatura> pagos = lancamentos.pagosDasFaturas(usuarioId).stream()
                .collect(Collectors.toMap(PagoDaFatura::faturaId, Function.identity()));
        Map<Long, List<CompraPrevista>> previstasPorCartao = previstas.stream()
                .collect(Collectors.groupingBy(CompraPrevista::cartaoId, LinkedHashMap::new, Collectors.toList()));
        List<FaturaProjetada> resultado = new ArrayList<>();
        for (CartaoResumo cartao : cartoes) {
            FaturasDoCartao doCartao = new FaturasDoCartao(usuarioId, cartao, gravadas.getOrDefault(cartao.id(), List.of()));
            Map<YearMonth, List<CompraPrevista>> porMes = new LinkedHashMap<>();
            for (CompraPrevista prevista : previstasPorCartao.getOrDefault(cartao.id(), List.of())) {
                porMes.computeIfAbsent(doCartao.faturaDe(prevista.data()), ignorado -> new ArrayList<>()).add(prevista);
            }
            Set<YearMonth> meses = new TreeSet<>(doCartao.porMes().keySet());
            meses.addAll(porMes.keySet());
            for (YearMonth mes : meses) {
                var datas = doCartao.datas(mes);
                if (datas.vencimento().isAfter(ate)) {
                    continue;
                }
                Fatura gravada = doCartao.gravada(mes).orElse(null);
                TotalDaFatura totalDaFatura = gravada == null ? null : totais.get(gravada.getId());
                PagoDaFatura pagoDaFatura = gravada == null ? null : pagos.get(gravada.getId());
                BigDecimal total = totalDaFatura == null ? ZERO : totalDaFatura.total();
                BigDecimal pago = pagoDaFatura == null ? ZERO : pagoDaFatura.pago();
                boolean pagaFora = gravada != null && gravada.pagaFora();
                List<CompraPrevista> dela = porMes.getOrDefault(mes, List.of());
                BigDecimal previsto = dela.stream().map(CompraPrevista::valor).reduce(ZERO, BigDecimal::add);
                BigDecimal saida = PagamentosDasFaturas.pendente(total.add(previsto), pago, pagaFora);
                if (saida.signum() > 0) {
                    resultado.add(new FaturaProjetada(cartao.id(), mes, datas.vencimento(),
                            PagamentosDasFaturas.pendente(total, pago, pagaFora), previsto.setScale(2), saida, List.copyOf(dela)));
                }
            }
        }
        resultado.sort(Comparator.comparing(FaturaProjetada::vencimento).thenComparing(FaturaProjetada::cartaoId)
                .thenComparing(FaturaProjetada::mes));
        return List.copyOf(resultado);
    }

    private static Long outraConta(Lancamento lancamento, Map<Long, List<Lancamento>> lados) {
        if (lancamento.getTransferenciaId() == null) {
            return null;
        }
        return lados.getOrDefault(lancamento.getTransferenciaId(), List.of()).stream()
                .filter(outro -> !outro.getId().equals(lancamento.getId())).map(Lancamento::getContaId).findFirst().orElse(null);
    }
}
