package com.flux.lancamentos.interno;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import com.flux.cartoes.CalendarioDoCartao;
import com.flux.cartoes.CartaoResumo;

/**
 * As faturas gravadas de um cartão, por mês, com o calendário dele (ADR 0017). Responde as datas de
 * qualquer mês e grava uma fatura quando o primeiro lançamento cai nela (decisão 9). Vale para um pedido;
 * quem grava segura a trava de cartões.
 */
final class FaturasDoCartao {

    private final Long usuarioId;
    private final CartaoResumo cartao;
    private final CalendarioDoCartao calendario;
    private final TreeMap<YearMonth, Fatura> porMes = new TreeMap<>();

    FaturasDoCartao(Long usuarioId, CartaoResumo cartao, Collection<Fatura> gravadas) {
        this.usuarioId = usuarioId;
        this.cartao = cartao;
        this.calendario = cartao.calendario();
        gravadas.forEach(fatura -> porMes.put(fatura.mes(), fatura));
    }

    static FaturasDoCartao carregar(FaturaRepository repositorio, Long usuarioId, CartaoResumo cartao) {
        return new FaturasDoCartao(usuarioId, cartao, repositorio.findAllByUsuarioIdAndCartaoId(usuarioId, cartao.id()));
    }

    CartaoResumo cartao() {
        return cartao;
    }

    CalendarioDoCartao calendario() {
        return calendario;
    }

    /** O mês da fatura gravada mais antiga; exige alguma. */
    YearMonth primeiroMes() {
        return porMes.firstKey();
    }

    Map<YearMonth, Fatura> porMes() {
        return porMes;
    }

    /** As datas das gravadas, para o calendário. */
    Map<YearMonth, CalendarioDoCartao.Datas> gravadas() {
        Map<YearMonth, CalendarioDoCartao.Datas> datas = new HashMap<>();
        porMes.forEach((mes, fatura) -> datas.put(mes, fatura.datas()));
        return datas;
    }

    Optional<Fatura> gravada(YearMonth mes) {
        return Optional.ofNullable(porMes.get(mes));
    }

    CalendarioDoCartao.Datas datas(YearMonth mes) {
        return calendario.datas(mes, gravadas());
    }

    LocalDate inicio(YearMonth mes) {
        return calendario.inicio(mes, gravadas());
    }

    /**
     * "Voltar ao fechamento normal" (decisão 12): o normal guardado na fatura gravada, o dos dias que valiam para ela
     * (decisão 7), ou, sem fatura gravada, o dos dias do cartão; nos dois casos, dentro dos limites do ajuste.
     */
    LocalDate fechamentoNormal(YearMonth mes) {
        return gravada(mes).map(fatura -> calendario.fechamentoNormal(mes, gravadas(), fatura.getFechamentoNormal()))
                .orElseGet(() -> calendario.fechamentoNormal(mes, gravadas()));
    }

    YearMonth faturaDe(LocalDate data) {
        return calendario.faturaDe(data, gravadas());
    }

    CalendarioDoCartao.Situacao situacao(YearMonth mes, LocalDate hoje) {
        return calendario.situacao(mes, gravadas(), hoje);
    }

    /** A fatura do mês; sem ela, grava uma com as datas de agora (decisão 9). */
    Fatura obter(FaturaRepository repositorio, YearMonth mes, Instant agora) {
        Fatura fatura = porMes.get(mes);
        if (fatura == null) {
            fatura = repositorio.save(new Fatura(usuarioId, cartao.id(), mes, datas(mes), agora));
            porMes.put(mes, fatura);
        }
        return fatura;
    }

    /** Põe na lista uma fatura gravada por fora (a aberta, na troca de dias). */
    void incluir(Fatura fatura) {
        porMes.put(fatura.mes(), fatura);
    }
}
