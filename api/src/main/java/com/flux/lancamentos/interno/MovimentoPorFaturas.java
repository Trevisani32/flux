package com.flux.lancamentos.interno;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.flux.cartoes.CalendarioDoCartao;
import com.flux.cartoes.CartaoResumo;
import com.flux.cartoes.MovimentoDosCartoes;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** A fatura aberta, a seguinte e o que está em aberto de cada cartão (decisões 5, 26 e 29), para o módulo de cartões. */
@Component
public class MovimentoPorFaturas implements MovimentoDosCartoes {

    private final FaturaRepository faturas;
    private final LancamentoRepository lancamentos;

    public MovimentoPorFaturas(FaturaRepository faturas, LancamentoRepository lancamentos) {
        this.faturas = faturas;
        this.lancamentos = lancamentos;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Movimento> movimento(Long usuarioId, List<CartaoResumo> cartoes, LocalDate hoje) {
        if (cartoes.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<Fatura>> porCartao = faturas.findAllByUsuarioId(usuarioId).stream()
                .collect(Collectors.groupingBy(Fatura::getCartaoId));
        Map<Long, TotalDaFatura> totais = lancamentos.totaisDasFaturas(usuarioId).stream()
                .collect(Collectors.toMap(TotalDaFatura::faturaId, Function.identity()));
        Map<Long, Movimento> movimentos = new HashMap<>();
        for (CartaoResumo cartao : cartoes) {
            FaturasDoCartao doCartao = new FaturasDoCartao(usuarioId, cartao, porCartao.getOrDefault(cartao.id(), List.of()));
            YearMonth aberta = doCartao.faturaDe(hoje);
            BigDecimal emAberto = BigDecimal.ZERO;
            long lancamentosEmAberto = 0;
            for (Map.Entry<YearMonth, Fatura> gravada : doCartao.porMes().entrySet()) {
                TotalDaFatura total = totais.get(gravada.getValue().getId());
                if (!gravada.getKey().isBefore(aberta) && total != null) {
                    emAberto = emAberto.add(total.total());
                    lancamentosEmAberto += total.lancamentos();
                }
            }
            movimentos.put(cartao.id(), new Movimento(fatura(doCartao, aberta, totais), fatura(doCartao, aberta.plusMonths(1), totais),
                    emAberto.setScale(2, RoundingMode.UNNECESSARY), lancamentosEmAberto));
        }
        return movimentos;
    }

    private static FaturaResumida fatura(FaturasDoCartao doCartao, YearMonth mes, Map<Long, TotalDaFatura> totais) {
        CalendarioDoCartao.Datas datas = doCartao.datas(mes);
        TotalDaFatura total = doCartao.gravada(mes).map(fatura -> totais.get(fatura.getId())).orElse(null);
        return new FaturaResumida(mes, doCartao.inicio(mes), datas.fechamento(), datas.vencimento(),
                total == null ? BigDecimal.ZERO.setScale(2) : total.total(), total == null ? 0 : total.lancamentos());
    }
}
