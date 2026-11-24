package com.flux.relatorios.interno;

import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JanelaDoRelatorioTest {

    @Test
    void oMesPedidoFicaNaFaixaDoPrimeiroMesAoMesAtual() {
        YearMonth atual = YearMonth.of(2026, 11);
        YearMonth primeiro = YearMonth.of(2026, 9);
        assertThat(JanelaDoRelatorio.de(null, primeiro, atual).mes()).isEqualTo(atual);
        assertThat(JanelaDoRelatorio.de(YearMonth.of(2026, 8), primeiro, atual).mes()).isEqualTo(atual);
        assertThat(JanelaDoRelatorio.de(YearMonth.of(2026, 12), primeiro, atual).mes()).isEqualTo(atual);
        assertThat(JanelaDoRelatorio.de(YearMonth.of(2026, 10), primeiro, atual).mes()).isEqualTo(YearMonth.of(2026, 10));
        assertThat(JanelaDoRelatorio.de(YearMonth.of(2026, 3), null, atual).mes()).isEqualTo(atual);
        assertThat(JanelaDoRelatorio.de(null, YearMonth.of(2027, 1), atual).mes()).isEqualTo(atual);
    }

    @Test
    void osGraficosTemAte12MesesEAMediaAte3() {
        YearMonth atual = YearMonth.of(2026, 11);
        assertThat(JanelaDoRelatorio.de(null, YearMonth.of(2025, 1), atual))
                .isEqualTo(new JanelaDoRelatorio(atual, YearMonth.of(2025, 12), List.of(YearMonth.of(2026, 8), YearMonth.of(2026, 9), YearMonth.of(2026, 10))));
        assertThat(JanelaDoRelatorio.de(YearMonth.of(2026, 3), YearMonth.of(2024, 1), atual).inicio()).isEqualTo(YearMonth.of(2025, 4));
        JanelaDoRelatorio primeira = JanelaDoRelatorio.de(YearMonth.of(2026, 9), YearMonth.of(2026, 9), atual);
        assertThat(primeira.media()).isEmpty();
        assertThat(primeira.inicioDaMedia()).isEqualTo(YearMonth.of(2026, 9));
        JanelaDoRelatorio segundo = JanelaDoRelatorio.de(YearMonth.of(2026, 10), YearMonth.of(2026, 9), atual);
        assertThat(segundo.media()).containsExactly(YearMonth.of(2026, 9));
        assertThat(segundo.inicioDaMedia()).isEqualTo(YearMonth.of(2026, 9));
    }
}
