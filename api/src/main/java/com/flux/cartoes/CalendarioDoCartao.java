package com.flux.cartoes;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * As datas do cartão (ADR 0017), sem banco. O mês de referência de uma fatura é o mês do vencimento.
 * As faturas gravadas guardam as datas delas (decisão 9); um mês sem fatura gravada usa as datas
 * padrão dos dias do cartão, presas entre as gravadas vizinhas, para os fechamentos nunca saírem de
 * ordem depois de uma troca de dias (decisão 7).
 */
public final class CalendarioDoCartao {

    /** Datas de uma fatura: o fechamento é sempre antes do vencimento, que cai no mês de referência. */
    public record Datas(LocalDate fechamento, LocalDate vencimento) {
    }

    /**
     * Decisão 12: o fechamento ajustado vai de {@code de} a {@code ate}. {@code ateOVencimento} diz qual
     * limite vale no fim: a véspera do vencimento (true) ou a do fechamento da fatura seguinte (false).
     */
    public record Limites(LocalDate de, LocalDate ate, boolean ateOVencimento) {
    }

    /** Decisão 10: calculada, nunca gravada. */
    public enum Situacao {
        FUTURA,
        ABERTA,
        FECHADA
    }

    private final int diaFechamento;
    private final int diaVencimento;

    public CalendarioDoCartao(int diaFechamento, int diaVencimento) {
        if (!diasValidos(diaFechamento, diaVencimento)) {
            throw new IllegalArgumentException("Dias inválidos: fecha " + diaFechamento + " e vence " + diaVencimento + ".");
        }
        this.diaFechamento = diaFechamento;
        this.diaVencimento = diaVencimento;
    }

    /**
     * Decisão 3: dias de 1 a 31. Vencimento maior que o fechamento vence no mesmo mês; senão, no mês
     * seguinte. Com o vencimento no mesmo mês, o fechamento vai até o dia 27: em fevereiro, 28 a 31
     * empatariam com o vencimento.
     */
    public static boolean diasValidos(int diaFechamento, int diaVencimento) {
        return diaFechamento >= 1 && diaFechamento <= 31 && diaVencimento >= 1 && diaVencimento <= 31
                && (diaVencimento <= diaFechamento || diaFechamento <= 27);
    }

    /** O dia no mês; o dia que não existe vira o último do mês (31 em fevereiro é 28 ou 29). */
    public static LocalDate dia(YearMonth mes, int dia) {
        return mes.atDay(Math.min(dia, mes.lengthOfMonth()));
    }

    public int diaFechamento() {
        return diaFechamento;
    }

    public int diaVencimento() {
        return diaVencimento;
    }

    private boolean venceNoMesDoFechamento() {
        return diaVencimento > diaFechamento;
    }

    /** As datas pelos dias do cartão, sem olhar as faturas gravadas. */
    public Datas padrao(YearMonth mes) {
        YearMonth mesDoFechamento = venceNoMesDoFechamento() ? mes : mes.minusMonths(1);
        return new Datas(dia(mesDoFechamento, diaFechamento), dia(mes, diaVencimento));
    }

    /** Decisão 8, pelos dias do cartão: a compra até o fechamento entra na fatura que fecha naquele mês; depois, na seguinte. */
    public YearMonth mesPadrao(LocalDate data) {
        YearMonth mesDaData = YearMonth.from(data);
        YearMonth fechaNoMesDaData = venceNoMesDoFechamento() ? mesDaData : mesDaData.plusMonths(1);
        return data.isAfter(dia(mesDaData, diaFechamento)) ? fechaNoMesDaData.plusMonths(1) : fechaNoMesDaData;
    }

    /**
     * As datas de um mês: as gravadas ou, sem fatura gravada, as padrão, com o fechamento preso entre o dia
     * seguinte ao fechamento da gravada anterior e a véspera do fechamento da gravada seguinte. O
     * vencimento padrão sempre cai depois desse fechamento.
     */
    public Datas datas(YearMonth mes, Map<YearMonth, Datas> gravadas) {
        Datas gravada = gravadas.get(mes);
        if (gravada != null) {
            return gravada;
        }
        Datas padrao = padrao(mes);
        LocalDate fechamento = padrao.fechamento();
        Datas anterior = gravadas.get(mes.minusMonths(1));
        if (anterior != null && !fechamento.isAfter(anterior.fechamento())) {
            fechamento = anterior.fechamento().plusDays(1);
        }
        Datas seguinte = gravadas.get(mes.plusMonths(1));
        if (seguinte != null && !fechamento.isBefore(seguinte.fechamento())) {
            fechamento = seguinte.fechamento().minusDays(1);
        }
        return new Datas(fechamento, padrao.vencimento());
    }

    /** O primeiro dia do período da fatura: o dia seguinte ao fechamento da anterior. */
    public LocalDate inicio(YearMonth mes, Map<YearMonth, Datas> gravadas) {
        return datas(mes.minusMonths(1), gravadas).fechamento().plusDays(1);
    }

    /**
     * Decisão 8 com os fechamentos gravados: a primeira fatura que fecha no dia da data ou depois. A busca
     * começa no mês da data, porque a fatura de uma data nunca vence antes dele; depois de uma troca de
     * dias, as faturas gravadas com os dias antigos podem ficar antes da padrão − 1.
     */
    public YearMonth faturaDe(LocalDate data, Map<YearMonth, Datas> gravadas) {
        YearMonth mes = YearMonth.from(data);
        while (data.isAfter(datas(mes, gravadas).fechamento())) {
            mes = mes.plusMonths(1);
        }
        return mes;
    }

    /** Decisão 10. */
    public Situacao situacao(YearMonth mes, Map<YearMonth, Datas> gravadas, LocalDate hoje) {
        if (hoje.isBefore(inicio(mes, gravadas))) {
            return Situacao.FUTURA;
        }
        return hoje.isAfter(datas(mes, gravadas).fechamento()) ? Situacao.FECHADA : Situacao.ABERTA;
    }

    /**
     * Decisão 12: do dia seguinte ao fechamento da anterior à véspera do vencimento desta e do fechamento da
     * seguinte. As vizinhas são lidas sem esta fatura: assim o ajuste não muda as datas delas.
     */
    public Limites limitesDoAjuste(YearMonth mes, Map<YearMonth, Datas> gravadas) {
        Map<YearMonth, Datas> semEla = new HashMap<>(gravadas);
        semEla.remove(mes);
        LocalDate de = datas(mes.minusMonths(1), semEla).fechamento().plusDays(1);
        LocalDate vencimento = datas(mes, gravadas).vencimento();
        LocalDate fechamentoDaSeguinte = datas(mes.plusMonths(1), semEla).fechamento();
        boolean ateOVencimento = !fechamentoDaSeguinte.isBefore(vencimento);
        return new Limites(de, (ateOVencimento ? vencimento : fechamentoDaSeguinte).minusDays(1), ateOVencimento);
    }

    /** "Voltar ao fechamento normal" (decisão 12): o fechamento pelos dias do cartão, dentro dos limites do ajuste. */
    public LocalDate fechamentoNormal(YearMonth mes, Map<YearMonth, Datas> gravadas) {
        return fechamentoNormal(mes, gravadas, padrao(mes).fechamento());
    }

    /**
     * "Voltar ao fechamento normal" de uma fatura gravada: o fechamento {@code daFatura}, o dos dias que valiam para
     * ela (decisão 7: depois de uma troca de dias, a aberta e as anteriores ficam com os dias de antes), dentro dos
     * limites do ajuste.
     */
    public LocalDate fechamentoNormal(YearMonth mes, Map<YearMonth, Datas> gravadas, LocalDate daFatura) {
        Limites limites = limitesDoAjuste(mes, gravadas);
        if (daFatura.isBefore(limites.de())) {
            return limites.de();
        }
        return daFatura.isAfter(limites.ate()) ? limites.ate() : daFatura;
    }

    /**
     * Decisão 7, com os dias novos neste calendário: as datas novas das faturas gravadas depois da
     * {@code aberta}. A aberta e as anteriores ficam como estão; as seguintes voltam às datas padrão (sem
     * ajuste), e a primeira delas nunca fecha antes do dia seguinte ao fechamento da aberta.
     */
    public Map<YearMonth, Datas> recalcular(YearMonth aberta, Map<YearMonth, Datas> gravadas) {
        Map<YearMonth, Datas> base = new HashMap<>();
        gravadas.forEach((mes, datas) -> {
            if (!mes.isAfter(aberta)) {
                base.put(mes, datas);
            }
        });
        Map<YearMonth, Datas> novas = new TreeMap<>();
        gravadas.keySet().stream().filter(mes -> mes.isAfter(aberta)).sorted().forEach(mes -> {
            Datas datas = datas(mes, base);
            base.put(mes, datas);
            novas.put(mes, datas);
        });
        return novas;
    }
}
