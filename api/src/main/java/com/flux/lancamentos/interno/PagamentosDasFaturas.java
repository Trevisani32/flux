package com.flux.lancamentos.interno;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.flux.cartoes.CalendarioDoCartao;

/**
 * O total, o pago, o pendente e a situação de cada fatura de um cartão (Fatia 05b, decisões 31, 34, 36 e 38, ADR 0018),
 * a partir das faturas gravadas, dos totais dos lançamentos delas e dos pagamentos. Vale para um pedido.
 *
 * <ul>
 *   <li>pendente = total − pago, nunca negativo; zero na fatura marcada como paga fora do Flux;</li>
 *   <li>pago a mais = o que passou do total (o total negativo, um crédito, conta como zero);</li>
 *   <li>paga: a fechada pelas datas com pagamento que cobre o total, ou marcada como paga fora do Flux. A fechada sem
 *       pagamento, mesmo vazia ou só com crédito, continua fechada: nada foi pago;</li>
 *   <li>o limite disponível = limite − a soma dos pendentes de todas as faturas, inclusive as futuras.</li>
 * </ul>
 */
final class PagamentosDasFaturas {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final FaturasDoCartao doCartao;
    private final Map<Long, TotalDaFatura> totais;
    private final Map<Long, PagoDaFatura> pagos;

    PagamentosDasFaturas(FaturasDoCartao doCartao, Collection<TotalDaFatura> totais, Collection<PagoDaFatura> pagos) {
        this.doCartao = doCartao;
        this.totais = totais.stream().collect(Collectors.toMap(TotalDaFatura::faturaId, Function.identity()));
        this.pagos = pagos.stream().collect(Collectors.toMap(PagoDaFatura::faturaId, Function.identity()));
    }

    static PagamentosDasFaturas carregar(LancamentoRepository lancamentos, Long usuarioId, FaturasDoCartao doCartao) {
        return new PagamentosDasFaturas(doCartao, lancamentos.totaisDoCartao(usuarioId, doCartao.cartao().id()),
                lancamentos.pagosDoCartao(usuarioId, doCartao.cartao().id()));
    }

    /** Uma fatura vista pelo pagamento. {@code pagaFora} é a marca da decisão 36. */
    record Resumo(BigDecimal total, BigDecimal pago, long pagamentos, boolean pagaFora, BigDecimal pendente, BigDecimal pagoAMais,
                  CalendarioDoCartao.Situacao situacao) {
    }

    Resumo resumo(YearMonth mes, LocalDate hoje) {
        Optional<Fatura> gravada = doCartao.gravada(mes);
        BigDecimal total = gravada.map(fatura -> totais.get(fatura.getId())).map(TotalDaFatura::total).orElse(ZERO);
        PagoDaFatura pago = gravada.map(fatura -> pagos.get(fatura.getId())).orElse(null);
        BigDecimal valorPago = pago == null ? ZERO : pago.pago();
        boolean pagaFora = gravada.map(Fatura::pagaFora).orElse(false);
        CalendarioDoCartao.Situacao pelasDatas = doCartao.situacao(mes, hoje);
        boolean paga = pelasDatas == CalendarioDoCartao.Situacao.FECHADA
                && (pagaFora || (valorPago.signum() > 0 && valorPago.compareTo(total) >= 0));
        return new Resumo(total, valorPago, pago == null ? 0 : pago.pagamentos(), pagaFora, pendente(total, valorPago, pagaFora),
                pagoAMais(total, valorPago), paga ? CalendarioDoCartao.Situacao.PAGA : pelasDatas);
    }

    /** Decisão 38: a soma dos pendentes de todas as faturas gravadas (as que não existem não têm lançamento). */
    BigDecimal pendenteDeTodas() {
        return doCartao.porMes().values().stream()
                .map(fatura -> {
                    TotalDaFatura total = totais.get(fatura.getId());
                    PagoDaFatura pago = pagos.get(fatura.getId());
                    return pendente(total == null ? ZERO : total.total(), pago == null ? ZERO : pago.pago(), fatura.pagaFora());
                })
                .reduce(ZERO, BigDecimal::add).setScale(2, RoundingMode.UNNECESSARY);
    }

    /**
     * Decisão 38, na prévia da compra: a soma dos pendentes depois de somar {@code acrescimos} (o valor de cada parcela,
     * positivo, no mês da fatura dela) aos totais.
     */
    BigDecimal pendenteDeTodasCom(Map<YearMonth, BigDecimal> acrescimos) {
        BigDecimal soma = ZERO;
        for (Map.Entry<YearMonth, Fatura> gravada : doCartao.porMes().entrySet()) {
            Fatura fatura = gravada.getValue();
            TotalDaFatura total = totais.get(fatura.getId());
            PagoDaFatura pago = pagos.get(fatura.getId());
            BigDecimal comAcrescimo = (total == null ? ZERO : total.total()).add(acrescimos.getOrDefault(gravada.getKey(), ZERO));
            soma = soma.add(pendente(comAcrescimo, pago == null ? ZERO : pago.pago(), fatura.pagaFora()));
        }
        for (Map.Entry<YearMonth, BigDecimal> acrescimo : acrescimos.entrySet()) {
            if (doCartao.gravada(acrescimo.getKey()).isEmpty()) {
                soma = soma.add(pendente(acrescimo.getValue(), ZERO, false));
            }
        }
        return soma.setScale(2, RoundingMode.UNNECESSARY);
    }

    /** Decisão 35: a fatura do mês está paga (só a fechada pode estar). */
    boolean paga(YearMonth mes, LocalDate hoje) {
        return doCartao.gravada(mes).isPresent() && resumo(mes, hoje).situacao() == CalendarioDoCartao.Situacao.PAGA;
    }

    /**
     * Decisão 35, na prévia da compra (dono, 2026-11-20): a fatura do mês fica paga com {@code acrescimo} somado ao total
     * (positivo na compra, negativo no estorno; na edição, o novo menos o que a compra já tem nela), e o lançamento entra
     * nela travado. Vale para a que já estava paga e continua, e para a que passa a ser paga pelo lançamento (o estorno que
     * fecha a conta, a edição que diminui o valor): fechada pelas datas e marcada como paga fora do Flux (a marca vale para
     * a fatura inteira), ou com pagamento maior que zero que cobre o total novo.
     */
    boolean ficaPagaCom(YearMonth mes, LocalDate hoje, BigDecimal acrescimo) {
        if (doCartao.gravada(mes).isEmpty() || doCartao.situacao(mes, hoje) != CalendarioDoCartao.Situacao.FECHADA) {
            return false;
        }
        Resumo atual = resumo(mes, hoje);
        return atual.pagaFora() || (atual.pago().signum() > 0 && atual.pago().compareTo(atual.total().add(acrescimo)) >= 0);
    }

    /** Decisão 38: pendente = total − pago, nunca negativo; zero na paga fora do Flux. */
    static BigDecimal pendente(BigDecimal total, BigDecimal pago, boolean pagaFora) {
        if (pagaFora) {
            return ZERO;
        }
        BigDecimal falta = total.subtract(pago);
        return (falta.signum() > 0 ? falta : ZERO).setScale(2, RoundingMode.UNNECESSARY);
    }

    /** Decisão 31: o que foi pago além do total; com o total negativo (crédito), além de zero. */
    static BigDecimal pagoAMais(BigDecimal total, BigDecimal pago) {
        BigDecimal sobra = pago.subtract(total.signum() > 0 ? total : ZERO);
        return (sobra.signum() > 0 ? sobra : ZERO).setScale(2, RoundingMode.UNNECESSARY);
    }
}
