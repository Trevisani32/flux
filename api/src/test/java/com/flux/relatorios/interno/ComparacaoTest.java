package com.flux.relatorios.interno;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ComparacaoTest {

    @Test
    void aMediaArredondaNoCentavoComHalfUp() {
        assertThat(Comparacao.media(valor("26410.00"), 3)).isEqualByComparingTo("8803.33");
        assertThat(Comparacao.media(valor("0.05"), 3)).isEqualByComparingTo("0.02");
        assertThat(Comparacao.media(valor("0.04"), 3)).isEqualByComparingTo("0.01");
        assertThat(Comparacao.media(valor("0.01"), 2)).isEqualByComparingTo("0.01");
        assertThat(Comparacao.media(valor("10.00"), 0)).isNull();
    }

    @Test
    void oPercentualEInteiroSobreAMediaArredondadaComHalfUp() {
        assertThat(Comparacao.de(valor("118.00"), valor("100.00"))).isEqualTo(comparacao(Comparacao.Tipo.ACIMA, 18));
        assertThat(Comparacao.de(valor("88.00"), valor("100.00"))).isEqualTo(comparacao(Comparacao.Tipo.ABAIXO, 12));
        assertThat(Comparacao.de(valor("112.50"), valor("100.00"))).isEqualTo(comparacao(Comparacao.Tipo.ACIMA, 13));
        assertThat(Comparacao.de(valor("87.50"), valor("100.00"))).isEqualTo(comparacao(Comparacao.Tipo.ABAIXO, 13));
        assertThat(Comparacao.de(valor("100.49"), valor("100.00"))).isEqualTo(comparacao(Comparacao.Tipo.IGUAL, 0));
        assertThat(Comparacao.de(valor("99.50"), valor("100.00"))).isEqualTo(comparacao(Comparacao.Tipo.ABAIXO, 1));
        assertThat(Comparacao.de(valor("99.51"), valor("100.00"))).isEqualTo(comparacao(Comparacao.Tipo.IGUAL, 0));
        assertThat(Comparacao.de(valor("9999999999999.99"), valor("0.01"))).isEqualTo(comparacao(Comparacao.Tipo.ACIMA, 99999999999999800L));
        assertThat(Comparacao.de(valor("92233720368547758.07"), valor("1.00")))
                .isEqualTo(comparacao(Comparacao.Tipo.ACIMA, 9223372036854775707L));
        assertThat(Comparacao.de(valor("92233720368547759.07"), valor("1.00")))
                .isEqualTo(comparacao(Comparacao.Tipo.ACIMA, Long.MAX_VALUE));
        assertThat(Comparacao.de(valor("10000000000000000.00"), valor("0.01"))).isEqualTo(comparacao(Comparacao.Tipo.ACIMA, Long.MAX_VALUE));
    }

    @Test
    void mediaZeroValorZeroESemMedia() {
        assertThat(Comparacao.de(valor("0.00"), valor("100.00"))).isEqualTo(comparacao(Comparacao.Tipo.ABAIXO, 100));
        assertThat(Comparacao.de(valor("50.00"), valor("0.00"))).isEqualTo(comparacao(Comparacao.Tipo.NOVO, 0));
        assertThat(Comparacao.de(valor("0.00"), valor("0.00"))).isEqualTo(comparacao(Comparacao.Tipo.IGUAL, 0));
        assertThat(Comparacao.de(valor("50.00"), null)).isNull();
    }

    private static BigDecimal valor(String valor) {
        return new BigDecimal(valor);
    }

    private static Comparacao comparacao(Comparacao.Tipo tipo, long percentual) {
        return new Comparacao(tipo, percentual);
    }
}
