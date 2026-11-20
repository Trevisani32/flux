package com.flux.cartoes;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.flux.cartoes.CalendarioDoCartao.Datas;
import com.flux.cartoes.CalendarioDoCartao.Limites;
import com.flux.cartoes.CalendarioDoCartao.Situacao;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A classe pura de datas do cartão (decisões 3, 7, 8, 10, 12, 13 e 48 da 05a, ADR 0017): as bordas de cada regra e
 * varreduras dos pares de dias, da fatura de cada data e da troca de dias com ajustes.
 */
class CalendarioDoCartaoTest {

    private static final CalendarioDoCartao HORIZONTE = new CalendarioDoCartao(3, 10);
    private static final CalendarioDoCartao LUA = new CalendarioDoCartao(25, 5);

    private static LocalDate d(String data) {
        return LocalDate.parse(data);
    }

    private static YearMonth m(String mes) {
        return YearMonth.parse(mes);
    }

    @Test
    void osDiasQueQuebramSaoSoOsSeisDaRegra() {
        List<String> recusados = new ArrayList<>();
        List<String> quebram = new ArrayList<>();
        for (int fechamento = 1; fechamento <= 31; fechamento++) {
            for (int vencimento = 1; vencimento <= 31; vencimento++) {
                if (!CalendarioDoCartao.diasValidos(fechamento, vencimento)) {
                    recusados.add(fechamento + "/" + vencimento);
                }
                // Pelas datas: algum mês de 2024 a 2032 com o fechamento no vencimento ou depois.
                YearMonth mes = YearMonth.of(2024, 1);
                boolean quebra = false;
                while (!mes.isAfter(YearMonth.of(2032, 12))) {
                    YearMonth mesDoFechamento = vencimento > fechamento ? mes : mes.minusMonths(1);
                    if (!CalendarioDoCartao.dia(mesDoFechamento, fechamento).isBefore(CalendarioDoCartao.dia(mes, vencimento))) {
                        quebra = true;
                    }
                    mes = mes.plusMonths(1);
                }
                if (quebra) {
                    quebram.add(fechamento + "/" + vencimento);
                }
            }
        }
        assertThat(recusados).containsExactly("28/29", "28/30", "28/31", "29/30", "29/31", "30/31");
        assertThat(quebram).isEqualTo(recusados);
        assertThat(CalendarioDoCartao.diasValidos(27, 28)).isTrue();
        assertThat(CalendarioDoCartao.diasValidos(31, 31)).isTrue();
        assertThat(CalendarioDoCartao.diasValidos(25, 5)).isTrue();
        assertThat(CalendarioDoCartao.diasValidos(0, 5)).isFalse();
        assertThat(CalendarioDoCartao.diasValidos(5, 32)).isFalse();
        assertThatThrownBy(() -> new CalendarioDoCartao(28, 30)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void oDiaQueNaoExisteViraOUltimoDoMes() {
        assertThat(CalendarioDoCartao.dia(m("2027-02"), 31)).isEqualTo(d("2027-02-28"));
        assertThat(CalendarioDoCartao.dia(m("2028-02"), 31)).isEqualTo(d("2028-02-29"));
        assertThat(CalendarioDoCartao.dia(m("2026-04"), 31)).isEqualTo(d("2026-04-30"));
        assertThat(new CalendarioDoCartao(31, 7).padrao(m("2027-03"))).isEqualTo(new Datas(d("2027-02-28"), d("2027-03-07")));
        assertThat(new CalendarioDoCartao(31, 7).padrao(m("2028-03"))).isEqualTo(new Datas(d("2028-02-29"), d("2028-03-07")));
        assertThat(new CalendarioDoCartao(31, 31).padrao(m("2027-03"))).isEqualTo(new Datas(d("2027-02-28"), d("2027-03-31")));
    }

    @Test
    void aFaturaDeUmaDataPeloFechamento() {
        Map<YearMonth, Datas> nenhuma = Map.of();
        assertThat(HORIZONTE.faturaDe(d("2026-11-02"), nenhuma)).isEqualTo(m("2026-11"));
        assertThat(HORIZONTE.faturaDe(d("2026-11-03"), nenhuma)).isEqualTo(m("2026-11"));
        assertThat(HORIZONTE.faturaDe(d("2026-11-04"), nenhuma)).isEqualTo(m("2026-12"));
        assertThat(HORIZONTE.faturaDe(d("2026-12-20"), nenhuma)).isEqualTo(m("2027-01"));
        assertThat(HORIZONTE.padrao(m("2026-11"))).isEqualTo(new Datas(d("2026-11-03"), d("2026-11-10")));
        assertThat(HORIZONTE.padrao(m("2026-12"))).isEqualTo(new Datas(d("2026-12-03"), d("2026-12-10")));
        assertThat(LUA.faturaDe(d("2026-11-20"), nenhuma)).isEqualTo(m("2026-12"));
        assertThat(LUA.padrao(m("2026-12"))).isEqualTo(new Datas(d("2026-11-25"), d("2026-12-05")));
        assertThat(LUA.faturaDe(d("2026-11-26"), nenhuma)).isEqualTo(m("2027-01"));
        CalendarioDoCartao fecha31 = new CalendarioDoCartao(31, 7);
        assertThat(fecha31.faturaDe(d("2027-02-15"), nenhuma)).isEqualTo(m("2027-03"));
        assertThat(fecha31.faturaDe(d("2027-02-28"), nenhuma)).isEqualTo(m("2027-03"));
        assertThat(fecha31.faturaDe(d("2027-03-01"), nenhuma)).isEqualTo(m("2027-04"));
        assertThat(fecha31.faturaDe(d("2028-02-29"), nenhuma)).isEqualTo(m("2028-03"));
        CalendarioDoCartao fecha10Vence10 = new CalendarioDoCartao(10, 10);
        assertThat(fecha10Vence10.faturaDe(d("2026-12-31"), nenhuma)).isEqualTo(m("2027-02"));
        assertThat(fecha10Vence10.padrao(m("2027-01"))).isEqualTo(new Datas(d("2026-12-10"), d("2027-01-10")));
        assertThat(HORIZONTE.inicio(m("2026-12"), nenhuma)).isEqualTo(d("2026-11-04"));
    }

    /** Decisões 15 e 17: as parcelas de uma 10x, de uma 48x e da compra em andamento. */
    @Test
    void asParcelasCaemUmaFaturaDepoisDaOutra() {
        YearMonth primeira = HORIZONTE.faturaDe(d("2026-11-09"), Map.of());
        assertThat(primeira).isEqualTo(m("2026-12"));
        assertThat(primeira.plusMonths(9)).isEqualTo(m("2027-09"));
        assertThat(primeira.plusMonths(47)).isEqualTo(m("2030-11"));
        assertThat(HORIZONTE.faturaDe(d("2026-07-15"), Map.of()).plusMonths(4)).isEqualTo(m("2026-12"));
        assertThat(HORIZONTE.faturaDe(d("2026-06-20"), Map.of()).plusMonths(5)).isEqualTo(m("2026-12"));
    }

    @Test
    void aSituacaoPeloPeriodo() {
        LocalDate hoje = d("2026-11-12");
        assertThat(HORIZONTE.situacao(m("2026-11"), Map.of(), hoje)).isEqualTo(Situacao.FECHADA);
        assertThat(HORIZONTE.situacao(m("2026-12"), Map.of(), hoje)).isEqualTo(Situacao.ABERTA);
        assertThat(HORIZONTE.situacao(m("2027-01"), Map.of(), hoje)).isEqualTo(Situacao.FUTURA);
        assertThat(HORIZONTE.situacao(m("2026-12"), Map.of(), d("2026-12-03"))).isEqualTo(Situacao.ABERTA);
        assertThat(HORIZONTE.situacao(m("2026-12"), Map.of(), d("2026-12-04"))).isEqualTo(Situacao.FECHADA);
        assertThat(HORIZONTE.situacao(m("2026-12"), Map.of(), d("2026-11-04"))).isEqualTo(Situacao.ABERTA);
        assertThat(HORIZONTE.situacao(m("2026-12"), Map.of(), d("2026-11-03"))).isEqualTo(Situacao.FUTURA);
    }

    /** Decisão 12: os limites, o fechamento normal e as compras que trocam de fatura com o ajuste. */
    @Test
    void oAjusteDeFechamento() {
        assertThat(HORIZONTE.limitesDoAjuste(m("2026-12"), Map.of())).isEqualTo(new Limites(d("2026-11-04"), d("2026-12-09"), true));
        assertThat(HORIZONTE.fechamentoNormal(m("2026-12"), Map.of())).isEqualTo(d("2026-12-03"));
        assertThat(LUA.limitesDoAjuste(m("2026-12"), Map.of())).isEqualTo(new Limites(d("2026-10-26"), d("2026-12-04"), true));
        // Com a fatura seguinte ajustada para antes do vencimento desta, o limite é a véspera do fechamento dela.
        Map<YearMonth, Datas> janeiroCedo = Map.of(m("2027-01"), new Datas(d("2026-11-27"), d("2027-01-05")));
        assertThat(LUA.limitesDoAjuste(m("2026-12"), janeiroCedo)).isEqualTo(new Limites(d("2026-10-26"), d("2026-11-26"), false));

        Map<YearMonth, Datas> dezembroEm5 = Map.of(m("2026-12"), new Datas(d("2026-12-05"), d("2026-12-10")));
        assertThat(HORIZONTE.faturaDe(d("2026-12-04"), dezembroEm5)).isEqualTo(m("2026-12"));
        assertThat(HORIZONTE.faturaDe(d("2026-12-05"), dezembroEm5)).isEqualTo(m("2026-12"));
        assertThat(HORIZONTE.faturaDe(d("2026-12-06"), dezembroEm5)).isEqualTo(m("2027-01"));
        assertThat(HORIZONTE.inicio(m("2027-01"), dezembroEm5)).isEqualTo(d("2026-12-06"));
        assertThat(HORIZONTE.fechamentoNormal(m("2026-12"), dezembroEm5)).isEqualTo(d("2026-12-03"));

        Map<YearMonth, Datas> dezembroEm1 = Map.of(m("2026-12"), new Datas(d("2026-12-01"), d("2026-12-10")));
        assertThat(HORIZONTE.faturaDe(d("2026-12-02"), dezembroEm1)).isEqualTo(m("2027-01"));
        assertThat(HORIZONTE.inicio(m("2027-01"), dezembroEm1)).isEqualTo(d("2026-12-02"));
        assertThat(HORIZONTE.limitesDoAjuste(m("2027-01"), dezembroEm1)).isEqualTo(new Limites(d("2026-12-02"), d("2027-01-09"), true));
    }

    /**
     * Decisão 7: os dias novos valem da fatura seguinte à aberta. A primeira com os dias novos nunca fecha antes do
     * dia seguinte ao fechamento da aberta, e a busca acha a fatura gravada com os dias antigos antes da padrão − 1.
     */
    @Test
    void aTrocaDeDiasGuardaAAbertaEPrendeAPrimeiraDepoisDela() {
        Map<YearMonth, Datas> gravadas = new HashMap<>();
        gravadas.put(m("2026-12"), new Datas(d("2026-12-27"), d("2026-12-28")));
        gravadas.put(m("2027-01"), new Datas(d("2027-01-27"), d("2027-01-28")));
        gravadas.put(m("2027-03"), new Datas(d("2027-03-27"), d("2027-03-28")));
        CalendarioDoCartao novo = new CalendarioDoCartao(5, 1);
        Map<YearMonth, Datas> novas = novo.recalcular(m("2026-12"), gravadas);
        assertThat(novas).containsOnlyKeys(m("2027-01"), m("2027-03"));
        assertThat(novas.get(m("2027-01"))).isEqualTo(new Datas(d("2026-12-28"), d("2027-01-01")));
        assertThat(novas.get(m("2027-03"))).isEqualTo(novo.padrao(m("2027-03")));
        gravadas.putAll(novas);
        assertThat(novo.datas(m("2027-02"), gravadas)).isEqualTo(novo.padrao(m("2027-02")));
        assertThat(novo.faturaDe(d("2026-12-28"), gravadas)).isEqualTo(m("2027-01"));
        assertThat(novo.faturaDe(d("2026-12-29"), gravadas)).isEqualTo(m("2027-02"));

        Map<YearMonth, Datas> antigaDeMesmoMes = Map.of(m("2026-11"), new Datas(d("2026-11-25"), d("2026-11-28")));
        CalendarioDoCartao vencimentoNoMesSeguinte = new CalendarioDoCartao(5, 3);
        assertThat(vencimentoNoMesSeguinte.mesPadrao(d("2026-11-20"))).isEqualTo(m("2027-01"));
        assertThat(vencimentoNoMesSeguinte.faturaDe(d("2026-11-20"), antigaDeMesmoMes)).isEqualTo(m("2026-11"));
    }

    /**
     * Varredura: cartões sorteados com faturas gravadas e ajustes, depois uma troca de dias, como a faz a
     * TrocaDeDiasNasFaturas. Nenhuma fatura fecha no vencimento ou depois nem sai de ordem, a aberta e as anteriores
     * ficam, toda data que ainda se lança (de 10 anos atrás ao fechamento da aberta) fica na fatura de antes, e a busca
     * acha, para cada data, a fatura cujo período a contém (conferido contra uma busca de força bruta).
     */
    @Test
    void varreduraDaTrocaDeDiasComAjustes() {
        List<int[]> pares = new ArrayList<>();
        for (int fechamento = 1; fechamento <= 31; fechamento++) {
            for (int vencimento = 1; vencimento <= 31; vencimento++) {
                if (CalendarioDoCartao.diasValidos(fechamento, vencimento)) {
                    pares.add(new int[] {fechamento, vencimento});
                }
            }
        }
        assertThat(pares).hasSize(955);
        Random sorteio = new Random(2026);
        List<String> erros = new ArrayList<>();
        for (int caso = 0; caso < 20_000 && erros.size() < 5; caso++) {
            int[] antigos = pares.get(sorteio.nextInt(pares.size()));
            int[] novos = pares.get(sorteio.nextInt(pares.size()));
            CalendarioDoCartao antigo = new CalendarioDoCartao(antigos[0], antigos[1]);
            CalendarioDoCartao novo = new CalendarioDoCartao(novos[0], novos[1]);
            LocalDate hoje = d("2026-01-01").plusDays(sorteio.nextInt(730));
            Map<YearMonth, Datas> gravadas = new HashMap<>();
            YearMonth abertaPadrao = antigo.faturaDe(hoje, gravadas);
            for (int passo = -3; passo <= 6; passo++) {
                YearMonth mes = abertaPadrao.plusMonths(passo);
                if (sorteio.nextBoolean()) {
                    gravadas.put(mes, antigo.datas(mes, gravadas));
                }
            }
            for (int ajuste = 0; ajuste < 3; ajuste++) {
                YearMonth mes = abertaPadrao.plusMonths(sorteio.nextInt(8) - 2);
                Limites limites = antigo.limitesDoAjuste(mes, gravadas);
                long dias = limites.ate().toEpochDay() - limites.de().toEpochDay();
                gravadas.put(mes, new Datas(limites.de().plusDays(sorteio.nextLong(dias + 1)), antigo.datas(mes, gravadas).vencimento()));
            }
            String antes = conferir(antigo, gravadas, abertaPadrao);
            if (antes != null) {
                erros.add("antes da troca, " + antigos[0] + "/" + antigos[1] + ": " + antes);
                continue;
            }
            // Uma em cada quatro vezes, uma compra antiga, de antes da janela de 10 anos, com a fatura gravada.
            YearMonth janela = YearMonth.from(hoje.minusYears(10));
            List<YearMonth> comLancamento = new ArrayList<>(gravadas.keySet());
            if (sorteio.nextInt(4) == 0) {
                YearMonth antiga = janela.minusMonths(1 + sorteio.nextInt(24));
                gravadas.put(antiga, antigo.datas(antiga, gravadas));
                comLancamento.add(antiga);
            }
            Map<YearMonth, Datas> antesDaTroca = new HashMap<>(gravadas);
            // A troca (TrocaDeDiasNasFaturas): grava, com as datas de agora, da véspera do mês mais antigo entre a
            // primeira fatura com lançamento e o de 10 anos atrás até a aberta, e recalcula as seguintes.
            YearMonth aberta = antigo.faturaDe(hoje, gravadas);
            YearMonth primeira = comLancamento.stream().filter(mes -> mes.isBefore(janela)).min(YearMonth::compareTo).orElse(janela);
            for (YearMonth mes = primeira.minusMonths(1); !mes.isAfter(aberta); mes = mes.plusMonths(1)) {
                gravadas.putIfAbsent(mes, antigo.datas(mes, gravadas));
            }
            Map<YearMonth, Datas> passadas = new HashMap<>(gravadas);
            passadas.keySet().removeIf(mes -> mes.isAfter(aberta));
            Map<YearMonth, LocalDate> inicios = new HashMap<>();
            for (YearMonth mes = primeira; !mes.isAfter(aberta); mes = mes.plusMonths(1)) {
                inicios.put(mes, antigo.inicio(mes, gravadas));
            }
            LocalDate fechamentoDaAberta = antigo.datas(aberta, gravadas).fechamento();
            List<LocalDate> datasQueSeLancam = new ArrayList<>();
            long diasNaJanela = fechamentoDaAberta.toEpochDay() - hoje.minusYears(10).toEpochDay();
            for (int amostra = 0; amostra < 20; amostra++) {
                datasQueSeLancam.add(hoje.minusYears(10).plusDays(sorteio.nextLong(diasNaJanela + 1)));
            }
            datasQueSeLancam.add(hoje.minusYears(10));
            datasQueSeLancam.add(fechamentoDaAberta);
            Map<LocalDate, YearMonth> faturasDeAntes = new HashMap<>();
            datasQueSeLancam.forEach(data -> faturasDeAntes.put(data, antigo.faturaDe(data, antesDaTroca)));
            gravadas.putAll(novo.recalcular(aberta, gravadas));
            inicios.forEach((mes, inicio) -> {
                if (!novo.inicio(mes, gravadas).equals(inicio)) {
                    erros.add("o período de " + mes + " mudou na troca de " + antigos[0] + "/" + antigos[1] + " para " + novos[0] + "/" + novos[1]);
                }
            });
            String depois = conferir(novo, gravadas, aberta);
            if (depois != null) {
                erros.add(antigos[0] + "/" + antigos[1] + " para " + novos[0] + "/" + novos[1] + " em " + hoje + ": " + depois);
            }
            passadas.forEach((mes, datas) -> {
                if (!novo.datas(mes, gravadas).equals(datas)) {
                    erros.add("a fatura " + mes + " mudou na troca de " + antigos[0] + "/" + antigos[1] + " para " + novos[0] + "/" + novos[1]);
                }
            });
            if (!novo.faturaDe(hoje, gravadas).equals(aberta)) {
                erros.add("a aberta mudou na troca de " + antigos[0] + "/" + antigos[1] + " para " + novos[0] + "/" + novos[1] + " em " + hoje);
            }
            faturasDeAntes.forEach((data, fatura) -> {
                if (!novo.faturaDe(data, gravadas).equals(fatura)) {
                    erros.add("a data " + data + " mudou de fatura na troca de " + antigos[0] + "/" + antigos[1] + " para " + novos[0] + "/"
                        + novos[1] + " em " + hoje);
                }
            });
        }
        assertThat(erros).isEmpty();
    }

    /** Os fechamentos de 6 meses antes a 14 depois: em ordem, antes do vencimento e com cada data na fatura certa. */
    private static String conferir(CalendarioDoCartao calendario, Map<YearMonth, Datas> gravadas, YearMonth centro) {
        LocalDate fechamentoAnterior = null;
        for (YearMonth mes = centro.minusMonths(6); !mes.isAfter(centro.plusMonths(14)); mes = mes.plusMonths(1)) {
            Datas datas = calendario.datas(mes, gravadas);
            if (!datas.fechamento().isBefore(datas.vencimento()) || !YearMonth.from(datas.vencimento()).equals(mes)) {
                return mes + " fecha em " + datas.fechamento() + " e vence em " + datas.vencimento();
            }
            if (fechamentoAnterior != null && !datas.fechamento().isAfter(fechamentoAnterior)) {
                return mes + " fecha em " + datas.fechamento() + ", antes da anterior";
            }
            fechamentoAnterior = datas.fechamento();
        }
        LocalDate inicio = calendario.datas(centro.minusMonths(5), gravadas).fechamento();
        LocalDate fim = calendario.datas(centro.plusMonths(13), gravadas).fechamento();
        for (LocalDate data = inicio; !data.isAfter(fim); data = data.plusDays(1)) {
            YearMonth achada = calendario.faturaDe(data, gravadas);
            YearMonth forcaBruta = centro.minusMonths(6);
            while (data.isAfter(calendario.datas(forcaBruta, gravadas).fechamento())) {
                forcaBruta = forcaBruta.plusMonths(1);
            }
            if (!achada.equals(forcaBruta)) {
                return data + " caiu em " + achada + ", e não em " + forcaBruta;
            }
        }
        return null;
    }
}
