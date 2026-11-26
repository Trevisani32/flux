package com.flux.recorrencias.interno;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalendarioDaRecorrenciaTest {

    private static LocalDate d(String iso) {
        return LocalDate.parse(iso);
    }

    private static List<String> datas(List<CalendarioDaRecorrencia.Ocorrencia> ocorrencias) {
        return ocorrencias.stream().map(o -> o.periodo() + ">" + o.data()).toList();
    }

    @Test
    void mensalNoDia31SaiDaAncoraEUsaOUltimoDiaDosMesesCurtos() {
        var c = new CalendarioDaRecorrencia(Frequencia.MENSAL, 31, d("2026-12-31"), null, d("2026-12-31"));
        assertThat(datas(c.entre(d("2026-12-31"), d("2027-04-30")))).containsExactly(
                "2026-12-01>2026-12-31", "2027-01-01>2027-01-31", "2027-02-01>2027-02-28", "2027-03-01>2027-03-31",
                "2027-04-01>2027-04-30");
        assertThat(c.ajustada(d("2027-02-28"))).isTrue();
        assertThat(c.ajustada(d("2027-03-31"))).isFalse();
        assertThat(c.ajustada(d("2027-04-30"))).isTrue();
    }

    @Test
    void mensalNoDia30EmFevereiroENoAnoBissexto() {
        var c = new CalendarioDaRecorrencia(Frequencia.MENSAL, 30, d("2026-11-25"), null, d("2026-11-25"));
        assertThat(datas(c.proximas(d("2026-11-25"), 5))).containsExactly("2026-11-01>2026-11-30", "2026-12-01>2026-12-30",
                "2027-01-01>2027-01-30", "2027-02-01>2027-02-28", "2027-03-01>2027-03-30");
        assertThat(datas(c.entre(d("2028-02-01"), d("2028-02-29")))).containsExactly("2028-02-01>2028-02-29");
        assertThat(c.ajustada(d("2028-02-29"))).isTrue();
    }

    @Test
    void anualEm29DeFevereiroVolta29Em2028() {
        var c = new CalendarioDaRecorrencia(Frequencia.ANUAL, null, d("2024-02-29"), null, d("2026-11-25"));
        assertThat(datas(c.proximas(d("2026-11-25"), 3))).containsExactly("2027-01-01>2027-02-28", "2028-01-01>2028-02-29",
                "2029-01-01>2029-02-28");
        assertThat(c.ajustada(d("2027-02-28"))).isTrue();
        assertThat(c.ajustada(d("2028-02-29"))).isFalse();
        var desdeOInicio = new CalendarioDaRecorrencia(Frequencia.ANUAL, null, d("2024-02-29"), null, d("2024-02-29"));
        assertThat(datas(desdeOInicio.entre(d("2024-01-01"), d("2026-12-31")))).containsExactly("2024-01-01>2024-02-29",
                "2025-01-01>2025-02-28", "2026-01-01>2026-02-28");
    }

    @Test
    void semanalAtravessaASemana53EAViradaDoAno() {
        var c = new CalendarioDaRecorrencia(Frequencia.SEMANAL, null, d("2026-11-27"), null, d("2026-11-25"));
        assertThat(datas(c.proximas(d("2026-11-25"), 7))).containsExactly("2026-11-23>2026-11-27", "2026-11-30>2026-12-04",
                "2026-12-07>2026-12-11", "2026-12-14>2026-12-18", "2026-12-21>2026-12-25", "2026-12-28>2027-01-01",
                "2027-01-04>2027-01-08");
        assertThat(CalendarioDaRecorrencia.periodo(Frequencia.SEMANAL, d("2027-01-03"))).isEqualTo(d("2026-12-28"));
        assertThat(CalendarioDaRecorrencia.abrePeriodo(Frequencia.SEMANAL, d("2026-12-28"))).isTrue();
        assertThat(CalendarioDaRecorrencia.abrePeriodo(Frequencia.SEMANAL, d("2026-12-29"))).isFalse();
        assertThat(CalendarioDaRecorrencia.abrePeriodo(Frequencia.MENSAL, d("2026-12-01"))).isTrue();
        assertThat(CalendarioDaRecorrencia.abrePeriodo(Frequencia.ANUAL, d("2026-12-01"))).isFalse();
    }

    @Test
    void trocarODiaMantemOPeriodo() {
        var dia5 = new CalendarioDaRecorrencia(Frequencia.MENSAL, 5, d("2026-10-01"), null, d("2026-10-01"));
        var dia10 = new CalendarioDaRecorrencia(Frequencia.MENSAL, 10, d("2026-10-01"), null, d("2026-10-01"));
        assertThat(datas(dia5.entre(d("2026-11-01"), d("2026-11-30")))).containsExactly("2026-11-01>2026-11-05");
        assertThat(datas(dia10.entre(d("2026-11-01"), d("2026-11-30")))).containsExactly("2026-11-01>2026-11-10");
        assertThat(CalendarioDaRecorrencia.periodo(Frequencia.SEMANAL, d("2026-11-25")))
                .isEqualTo(CalendarioDaRecorrencia.periodo(Frequencia.SEMANAL, d("2026-11-27"))).isEqualTo(d("2026-11-23"));
    }

    @Test
    void soExistemOcorrenciasDesdeOMaiorEntreInicioECadastro() {
        var c = new CalendarioDaRecorrencia(Frequencia.MENSAL, 1, d("2026-01-01"), null, d("2026-11-25"));
        assertThat(c.primeira()).isEqualTo(d("2026-11-25"));
        assertThat(datas(c.entre(d("2026-01-01"), d("2026-12-31")))).containsExactly("2026-12-01>2026-12-01");
        var futura = new CalendarioDaRecorrencia(Frequencia.SEMANAL, null, d("2026-11-27"), null, d("2026-11-25"));
        assertThat(futura.primeira()).isEqualTo(d("2026-11-27"));
    }

    @Test
    void oFimLimitaEAEncerradaAntesDeComecarNaoTemOcorrencia() {
        var curso = new CalendarioDaRecorrencia(Frequencia.MENSAL, 15, d("2026-12-15"), d("2027-06-15"), d("2026-11-25"));
        assertThat(curso.proximas(d("2026-11-25"), 10)).extracting(CalendarioDaRecorrencia.Ocorrencia::data).containsExactly(
                d("2026-12-15"), d("2027-01-15"), d("2027-02-15"), d("2027-03-15"), d("2027-04-15"), d("2027-05-15"), d("2027-06-15"));
        var encerrada = new CalendarioDaRecorrencia(Frequencia.SEMANAL, null, d("2026-11-27"), d("2026-11-25"), d("2026-11-25"));
        assertThat(encerrada.entre(d("2026-11-01"), d("2027-11-25"))).isEmpty();
        assertThat(encerrada.proximas(d("2026-11-25"), 5)).isEmpty();
    }

    @Test
    void aOcorrenciaDoPeriodoSoExisteDentroDoIntervalo() {
        var c = new CalendarioDaRecorrencia(Frequencia.MENSAL, 30, d("2026-11-25"), d("2026-12-31"), d("2026-11-25"));
        assertThat(c.doPeriodo(d("2026-11-01"))).contains(new CalendarioDaRecorrencia.Ocorrencia(d("2026-11-01"), d("2026-11-30")));
        assertThat(c.doPeriodo(d("2026-10-01"))).isEmpty();
        assertThat(c.doPeriodo(d("2027-01-01"))).isEmpty();
    }

    @Test
    void aJanelaDaAtrasadaEDosSessentaDias() {
        assertThat(CalendarioDaRecorrencia.visivelDesde(d("2026-11-25"))).isEqualTo(d("2026-09-26"));
        assertThat(CalendarioDaRecorrencia.visivelDesde(d("2027-01-19"))).isEqualTo(d("2026-11-20"));
        assertThat(CalendarioDaRecorrencia.visivelDesde(d("2027-01-20"))).isEqualTo(d("2026-11-21"));
        assertThat(CalendarioDaRecorrencia.visivelAte(d("2026-11-25"))).isEqualTo(d("2027-11-25"));
        assertThat(CalendarioDaRecorrencia.atrasada(d("2026-11-24"), d("2026-11-25"))).isTrue();
        assertThat(CalendarioDaRecorrencia.atrasada(d("2026-11-25"), d("2026-11-25"))).isFalse();
    }

    @Test
    void diaDoMesSoNaMensalEDe1A31() {
        assertThatThrownBy(() -> new CalendarioDaRecorrencia(Frequencia.MENSAL, null, d("2026-11-25"), null, d("2026-11-25")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CalendarioDaRecorrencia(Frequencia.SEMANAL, 5, d("2026-11-25"), null, d("2026-11-25")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CalendarioDaRecorrencia(Frequencia.MENSAL, 32, d("2026-11-25"), null, d("2026-11-25")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
