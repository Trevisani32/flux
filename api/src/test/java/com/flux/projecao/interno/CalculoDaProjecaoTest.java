package com.flux.projecao.interno;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.flux.projecao.interno.CalculoDaProjecao.Dia;
import com.flux.projecao.interno.CalculoDaProjecao.Entrada;
import com.flux.projecao.interno.CalculoDaProjecao.Resumo;
import com.flux.projecao.interno.CalculoDaProjecao.Trecho;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalculoDaProjecaoTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 25);

    @Test
    void oExemploDeBancoHorizonte() {
        var resultado = CalculoDaProjecao.calcular(HOJE, d("2026-12-31"), saldos("1", "1240.35"), entradasDoBancoHorizonte());

        assertThat(resultado.pendencias()).containsExactly("Fatura Lua nov", "Água");
        assertResumo(resultado.total(), "1240.35", "-508.70", "731.65", "-2397.95", "2026-12-04", "1485.46", 4,
                trecho("2026-12-01", "2026-12-04"));
        assertThat(resultado.porConta()).containsExactlyEntriesOf(Map.of(1L, resultado.total()));
        assertDias(resultado.dias(), diasDoBancoHorizonte());
        assertEscalaDois(resultado);
    }

    @Test
    void aTransferenciaInternaSomeDoTotalEDaLista() {
        List<Entrada<String>> entradas = new ArrayList<>();
        for (Entrada<String> entrada : entradasDoBancoHorizonte()) {
            entradas.add(entrada.item().equals("Reserva")
                    ? new Entrada<>(entrada.contaId(), entrada.dia(), entrada.valor(), false, entrada.item()) : entrada);
        }
        entradas.add(e(4, "2026-12-06", "500.00", false, "Reserva"));
        var resultado = CalculoDaProjecao.calcular(HOJE, d("2026-12-31"), saldos("1", "1240.35", "4", "14920.40"), entradas);

        assertResumo(resultado.total(), "16160.75", "-508.70", "15652.05", "12522.45", "2026-12-04", "16905.86", 0);
        assertResumo(resultado.porConta().get(4L), "14920.40", "0.00", "14920.40", "14920.40", "2026-11-25", "15420.40", 0);
        assertResumo(resultado.porConta().get(1L), "1240.35", "-508.70", "731.65", "-2397.95", "2026-12-04", "1485.46", 4,
                trecho("2026-12-01", "2026-12-04"));
        assertDias(resultado.dias(), List.of(
                dia("2026-11-26", "-119.90", "15532.15", "Internet"),
                dia("2026-11-27", "-180.00", "15352.15", "Diarista"),
                dia("2026-11-28", "-99.90", "15252.25", "Academia"),
                dia("2026-11-30", "-279.80", "14972.45", "Luz", "Seguro"),
                dia("2026-12-01", "-2270.00", "12702.45", "Aluguel", "Condomínio"),
                dia("2026-12-04", "-180.00", "12522.45", "Diarista"),
                dia("2026-12-05", "6915.10", "19437.55", "Salário", "Fatura Lua dez"),
                dia("2026-12-10", "-1135.59", "18301.96", "Fatura Horizonte dez"),
                dia("2026-12-11", "-180.00", "18121.96", "Diarista"),
                dia("2026-12-15", "-350.00", "17771.96", "Curso"),
                dia("2026-12-18", "-180.00", "17591.96", "Diarista"),
                dia("2026-12-20", "-96.40", "17495.56", "Água"),
                dia("2026-12-25", "-180.00", "17315.56", "Diarista"),
                dia("2026-12-26", "-119.90", "17195.66", "Internet"),
                dia("2026-12-28", "-99.90", "17095.76", "Academia"),
                dia("2026-12-30", "-189.90", "16905.86", "Luz")));
    }

    @Test
    void osTrechosNegativosEOsDias() {
        var resultado = CalculoDaProjecao.calcular(HOJE, d("2026-12-10"), saldos("1", "100.00"), List.of(
                e(1, "2026-11-25", "-150.00", true, "Hoje"), e(1, "2026-11-27", "100.00", true, "Receita"),
                e(1, "2026-12-01", "-80.00", true, "Despesa"), e(1, "2026-12-03", "30.00", true, "Receita 2"),
                e(1, "2026-12-05", "-1.00", true, "Despesa 2")));

        assertResumo(resultado.total(), "100.00", "-150.00", "-50.00", "-50.00", "2026-11-25", "-1.00", 10,
                trecho("2026-11-25", "2026-11-26"), trecho("2026-12-01", "2026-12-02"),
                trecho("2026-12-05", "2026-12-10"));
        assertDias(resultado.dias(), List.of(dia("2026-11-27", "100.00", "50.00", "Receita"),
                dia("2026-12-01", "-80.00", "-30.00", "Despesa"), dia("2026-12-03", "30.00", "0.00", "Receita 2"),
                dia("2026-12-05", "-1.00", "-1.00", "Despesa 2")));
    }

    @Test
    void oMenorEmpatadoFicaComOPrimeiroDia() {
        var resultado = CalculoDaProjecao.calcular(HOJE, d("2026-11-30"), saldos("1", "100.00"), List.of(
                e(1, "2026-11-26", "-50.00", true, "Primeiro"), e(1, "2026-11-27", "10.00", true, "Sobe"),
                e(1, "2026-11-28", "-10.00", true, "Empate")));

        assertThat(resultado.total().menor()).isEqualByComparingTo("50.00");
        assertThat(resultado.total().diaDoMenor()).isEqualTo(d("2026-11-26"));
        assertThat(resultado.total().fim()).isEqualByComparingTo("50.00");
        assertThat(resultado.total().diasNegativos()).isEqualTo(0);
    }

    @Test
    void semEntradasAContaNegativaFicaNegativaAteOFim() {
        var resultado = CalculoDaProjecao.calcular(HOJE, d("2026-11-27"), saldos("1", "10.00", "2", "-5.00"), List.of());

        assertResumo(resultado.total(), "5.00", "0.00", "5.00", "5.00", "2026-11-25", "5.00", 0);
        assertResumo(resultado.porConta().get(2L), "-5.00", "0.00", "-5.00", "-5.00", "2026-11-25", "-5.00", 3,
                trecho("2026-11-25", "2026-11-27"));
        assertThat(resultado.pendencias()).isEmpty();
        assertThat(resultado.dias()).isEmpty();
    }

    @Test
    void semContaNenhuma() {
        var resultado = CalculoDaProjecao.calcular(HOJE, d("2026-11-26"), new LinkedHashMap<>(), List.of());

        assertResumo(resultado.total(), "0.00", "0.00", "0.00", "0.00", "2026-11-25", "0.00", 0);
        assertThat(resultado.porConta()).isEmpty();
        assertThat(resultado.dias()).isEmpty();
    }

    @Test
    void entradaForaDoPeriodoOuDeOutraConta() {
        Map<Long, BigDecimal> saldos = saldos("1", "10.00");

        assertThatThrownBy(() -> CalculoDaProjecao.calcular(HOJE, d("2026-11-30"), saldos,
                List.of(e(1, "2026-11-24", "1.00", true, "Antes")))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CalculoDaProjecao.calcular(HOJE, d("2026-11-30"), saldos,
                List.of(e(1, "2026-12-01", "1.00", true, "Depois")))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CalculoDaProjecao.calcular(HOJE, d("2026-11-30"), saldos,
                List.of(e(9, "2026-11-26", "1.00", true, "Outra")))).isInstanceOf(IllegalArgumentException.class);
    }

    private static List<Entrada<String>> entradasDoBancoHorizonte() {
        return List.of(e(1, "2026-11-25", "-412.30", true, "Fatura Lua nov"), e(1, "2026-11-25", "-96.40", true, "Água"),
                e(1, "2026-11-26", "-119.90", true, "Internet"), e(1, "2026-11-27", "-180.00", true, "Diarista"),
                e(1, "2026-11-28", "-99.90", true, "Academia"), e(1, "2026-11-30", "-189.90", true, "Luz"),
                e(1, "2026-11-30", "-89.90", true, "Seguro"), e(1, "2026-12-01", "-1850.00", true, "Aluguel"),
                e(1, "2026-12-01", "-420.00", true, "Condomínio"), e(1, "2026-12-04", "-180.00", true, "Diarista"),
                e(1, "2026-12-05", "8200.00", true, "Salário"), e(1, "2026-12-05", "-1284.90", true, "Fatura Lua dez"),
                e(1, "2026-12-06", "-500.00", true, "Reserva"), e(1, "2026-12-10", "-1135.59", true, "Fatura Horizonte dez"),
                e(1, "2026-12-11", "-180.00", true, "Diarista"), e(1, "2026-12-15", "-350.00", true, "Curso"),
                e(1, "2026-12-18", "-180.00", true, "Diarista"), e(1, "2026-12-20", "-96.40", true, "Água"),
                e(1, "2026-12-25", "-180.00", true, "Diarista"), e(1, "2026-12-26", "-119.90", true, "Internet"),
                e(1, "2026-12-28", "-99.90", true, "Academia"), e(1, "2026-12-30", "-189.90", true, "Luz"));
    }

    private static List<Dia<String>> diasDoBancoHorizonte() {
        return List.of(dia("2026-11-26", "-119.90", "611.75", "Internet"), dia("2026-11-27", "-180.00", "431.75", "Diarista"),
                dia("2026-11-28", "-99.90", "331.85", "Academia"), dia("2026-11-30", "-279.80", "52.05", "Luz", "Seguro"),
                dia("2026-12-01", "-2270.00", "-2217.95", "Aluguel", "Condomínio"),
                dia("2026-12-04", "-180.00", "-2397.95", "Diarista"), dia("2026-12-05", "6915.10", "4517.15", "Salário",
                        "Fatura Lua dez"),
                dia("2026-12-06", "-500.00", "4017.15", "Reserva"), dia("2026-12-10", "-1135.59", "2881.56",
                        "Fatura Horizonte dez"),
                dia("2026-12-11", "-180.00", "2701.56", "Diarista"), dia("2026-12-15", "-350.00", "2351.56", "Curso"),
                dia("2026-12-18", "-180.00", "2171.56", "Diarista"), dia("2026-12-20", "-96.40", "2075.16", "Água"),
                dia("2026-12-25", "-180.00", "1895.16", "Diarista"), dia("2026-12-26", "-119.90", "1775.26", "Internet"),
                dia("2026-12-28", "-99.90", "1675.36", "Academia"), dia("2026-12-30", "-189.90", "1485.46", "Luz"));
    }

    private static void assertDias(List<Dia<String>> atual, List<Dia<String>> esperado) {
        assertThat(atual).containsExactlyElementsOf(esperado);
    }

    private static void assertResumo(Resumo resumo, String hoje, String pendencias, String inicio, String menor, String dia,
                                     String fim, int diasNegativos, Trecho... trechos) {
        assertThat(resumo.hoje()).isEqualByComparingTo(hoje);
        assertThat(resumo.pendencias()).isEqualByComparingTo(pendencias);
        assertThat(resumo.inicio()).isEqualByComparingTo(inicio);
        assertThat(resumo.menor()).isEqualByComparingTo(menor);
        assertThat(resumo.diaDoMenor()).isEqualTo(d(dia));
        assertThat(resumo.fim()).isEqualByComparingTo(fim);
        assertThat(resumo.diasNegativos()).isEqualTo(diasNegativos);
        assertThat(resumo.trechos()).containsExactly(trechos);
    }

    private static void assertEscalaDois(CalculoDaProjecao.Resultado<String> resultado) {
        List<BigDecimal> valores = new ArrayList<>();
        adicionar(valores, resultado.total());
        resultado.porConta().values().forEach(resumo -> adicionar(valores, resumo));
        resultado.dias().forEach(dia -> {
            valores.add(dia.movimento());
            valores.add(dia.saldo());
        });
        assertThat(valores).allSatisfy(valor -> assertThat(valor.scale()).isEqualTo(2));
    }

    private static void adicionar(List<BigDecimal> valores, Resumo resumo) {
        valores.add(resumo.hoje());
        valores.add(resumo.pendencias());
        valores.add(resumo.inicio());
        valores.add(resumo.menor());
        valores.add(resumo.fim());
    }

    private static Dia<String> dia(String data, String movimento, String saldo, String... itens) {
        return new Dia<>(d(data), bd(movimento), bd(saldo), List.of(itens));
    }

    private static Entrada<String> e(long conta, String data, String valor, boolean naLista, String item) {
        return new Entrada<>(conta, d(data), bd(valor), naLista, item);
    }

    private static Trecho trecho(String de, String ate) {
        return new Trecho(d(de), d(ate));
    }

    private static Map<Long, BigDecimal> saldos(String... valores) {
        Map<Long, BigDecimal> resultado = new LinkedHashMap<>();
        for (int indice = 0; indice < valores.length; indice += 2) {
            resultado.put(Long.valueOf(valores[indice]), bd(valores[indice + 1]));
        }
        return resultado;
    }

    private static BigDecimal bd(String valor) {
        return new BigDecimal(valor).setScale(2);
    }

    private static LocalDate d(String data) {
        return LocalDate.parse(data);
    }
}
