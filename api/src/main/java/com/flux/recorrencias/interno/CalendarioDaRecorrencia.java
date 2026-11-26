package com.flux.recorrencias.interno;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.flux.cartoes.CalendarioDoCartao;

/**
 * As datas das ocorrências de uma recorrência e a chave do período (Fatia 07a, ADR 0022), sem banco. Toda data sai da
 * âncora (o dia do mês, o dia da semana ou o dia e o mês do início), nunca da ocorrência anterior: encadeando, o dia 31
 * virava 28 para sempre depois de fevereiro. O período é o mês na mensal, a semana de segunda a domingo na semanal e o
 * ano na anual, guardado como o primeiro dia dele: a tratada é reconhecida pelo período, não pela data, e mudar o dia
 * não traz de volta a do período já tratado.
 */
final class CalendarioDaRecorrencia {

    /** Decisão 12: a atrasada aparece até 60 dias depois da data dela. */
    static final int DIAS_DA_ATRASADA = 60;
    /** Decisão 20: as previstas vão até 12 meses depois de hoje, o limite da data de um lançamento. */
    static final int MESES_A_FRENTE = 12;

    /** Uma ocorrência: o período (a chave) e a data prevista. */
    record Ocorrencia(LocalDate periodo, LocalDate data) {
    }

    private final Frequencia frequencia;
    private final Integer diaDoMes;
    private final LocalDate comecaEm;
    private final LocalDate terminaEm;
    private final LocalDate ocorrenciasDesde;

    /** {@code diaDoMes} só na mensal (1 a 31); {@code terminaEm} pode ser nulo. */
    CalendarioDaRecorrencia(Frequencia frequencia, Integer diaDoMes, LocalDate comecaEm, LocalDate terminaEm,
                            LocalDate ocorrenciasDesde) {
        if ((frequencia == Frequencia.MENSAL) != (diaDoMes != null) || diaDoMes != null && (diaDoMes < 1 || diaDoMes > 31)) {
            throw new IllegalArgumentException("Dia do mês inválido na frequência " + frequencia + ": " + diaDoMes);
        }
        this.frequencia = frequencia;
        this.diaDoMes = diaDoMes;
        this.comecaEm = comecaEm;
        this.terminaEm = terminaEm;
        this.ocorrenciasDesde = ocorrenciasDesde;
    }

    /** O primeiro dia do período da data: o dia 1 do mês, a segunda-feira da semana ou o 1º de janeiro. */
    static LocalDate periodo(Frequencia frequencia, LocalDate data) {
        return switch (frequencia) {
            case MENSAL -> data.withDayOfMonth(1);
            case SEMANAL -> data.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case ANUAL -> data.withDayOfYear(1);
        };
    }

    /** Se o dia abre um período da frequência: só ele serve de chave. */
    static boolean abrePeriodo(Frequencia frequencia, LocalDate dia) {
        return periodo(frequencia, dia).equals(dia);
    }

    /** Decisão 12: a não tratada aparece a partir de hoje − 60 dias. */
    static LocalDate visivelDesde(LocalDate hoje) {
        return hoje.minusDays(DIAS_DA_ATRASADA);
    }

    /** Decisão 20: e até hoje + 12 meses. */
    static LocalDate visivelAte(LocalDate hoje) {
        return hoje.plusMonths(MESES_A_FRENTE);
    }

    /** A de antes de hoje é atrasada; a de hoje ainda é prevista. */
    static boolean atrasada(LocalDate data, LocalDate hoje) {
        return data.isBefore(hoje);
    }

    /** Decisão 9: a primeira data em que pode haver ocorrência, o maior entre o início e o dia do cadastro. */
    LocalDate primeira() {
        return comecaEm.isAfter(ocorrenciasDesde) ? comecaEm : ocorrenciasDesde;
    }

    /** A data no período, pela âncora; o dia que o mês não tem vira o último dele (31 → 30/04, 29/02 → 28/02). */
    LocalDate dataNoPeriodo(LocalDate periodo) {
        return switch (frequencia) {
            case MENSAL -> CalendarioDoCartao.dia(YearMonth.from(periodo), diaDoMes);
            case SEMANAL -> periodo.with(TemporalAdjusters.nextOrSame(comecaEm.getDayOfWeek()));
            case ANUAL -> CalendarioDoCartao.dia(YearMonth.of(periodo.getYear(), comecaEm.getMonth()), comecaEm.getDayOfMonth());
        };
    }

    /** A data caiu noutro dia que o pedido: o 30 em fevereiro, o 29/02 num ano sem ele. */
    boolean ajustada(LocalDate data) {
        return switch (frequencia) {
            case MENSAL -> data.getDayOfMonth() != diaDoMes;
            case SEMANAL -> false;
            case ANUAL -> data.getDayOfMonth() != comecaEm.getDayOfMonth();
        };
    }

    /** A ocorrência do período, se a data dele fica entre a primeira e o fim. */
    Optional<Ocorrencia> doPeriodo(LocalDate periodo) {
        LocalDate data = dataNoPeriodo(periodo);
        boolean dentro = !data.isBefore(primeira()) && (terminaEm == null || !data.isAfter(terminaEm));
        return dentro ? Optional.of(new Ocorrencia(periodo, data)) : Optional.empty();
    }

    /** As ocorrências com data de {@code de} a {@code ate}, inclusive, em ordem. */
    List<Ocorrencia> entre(LocalDate de, LocalDate ate) {
        LocalDate inicio = de.isAfter(primeira()) ? de : primeira();
        LocalDate fim = terminaEm != null && terminaEm.isBefore(ate) ? terminaEm : ate;
        List<Ocorrencia> ocorrencias = new ArrayList<>();
        for (LocalDate periodo = periodo(frequencia, inicio); !periodo.isAfter(fim); periodo = seguinte(periodo)) {
            LocalDate data = dataNoPeriodo(periodo);
            if (!data.isBefore(inicio) && !data.isAfter(fim)) {
                ocorrencias.add(new Ocorrencia(periodo, data));
            }
        }
        return ocorrencias;
    }

    /** As {@code quantas} primeiras ocorrências com data a partir de {@code de}, inclusive (a prévia do diálogo). */
    List<Ocorrencia> proximas(LocalDate de, int quantas) {
        LocalDate inicio = de.isAfter(primeira()) ? de : primeira();
        List<Ocorrencia> ocorrencias = new ArrayList<>();
        for (LocalDate periodo = periodo(frequencia, inicio); ocorrencias.size() < quantas
                && (terminaEm == null || !periodo.isAfter(terminaEm)); periodo = seguinte(periodo)) {
            LocalDate data = dataNoPeriodo(periodo);
            if (!data.isBefore(inicio) && (terminaEm == null || !data.isAfter(terminaEm))) {
                ocorrencias.add(new Ocorrencia(periodo, data));
            }
        }
        return ocorrencias;
    }

    private LocalDate seguinte(LocalDate periodo) {
        return switch (frequencia) {
            case MENSAL -> periodo.plusMonths(1);
            case SEMANAL -> periodo.plusWeeks(1);
            case ANUAL -> periodo.plusYears(1);
        };
    }
}
