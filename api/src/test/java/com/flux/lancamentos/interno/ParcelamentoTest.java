package com.flux.lancamentos.interno;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Decisão 15 da 05a (ADR 0017): o total ÷ N truncado no centavo, o resto na 1ª, nenhuma parcela zero. */
class ParcelamentoTest {

    private static List<String> dividir(String total, int parcelas) {
        return Parcelamento.dividir(new BigDecimal(total), parcelas).stream().map(BigDecimal::toPlainString).toList();
    }

    @Test
    void oRestoVaiParaAPrimeira() {
        assertThat(dividir("100.00", 3)).containsExactly("33.34", "33.33", "33.33");
        assertThat(dividir("1000.00", 7)).containsExactly("142.90", "142.85", "142.85", "142.85", "142.85", "142.85", "142.85");
        assertThat(dividir("1990.00", 10)).containsOnly("199.00").hasSize(10);
        assertThat(dividir("59.90", 1)).containsExactly("59.90");
        assertThat(dividir("0.48", 48)).containsOnly("0.01").hasSize(48);
        assertThat(dividir("0.50", 48).getFirst()).isEqualTo("0.03");
        assertThat(dividir("9999999999999.99", 48).getFirst()).isEqualTo("208333333333.48");
        assertThat(dividir("9999999999999.99", 48).get(1)).isEqualTo("208333333333.33");
    }

    @Test
    void oMinimoEUmCentavoPorParcela() {
        assertThat(Parcelamento.minimo(48)).isEqualByComparingTo("0.48");
        assertThat(Parcelamento.minimo(1)).isEqualByComparingTo("0.01");
        assertThatThrownBy(() -> Parcelamento.dividir(new BigDecimal("0.47"), 48)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Parcelamento.dividir(new BigDecimal("10.00"), 49)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Parcelamento.dividir(new BigDecimal("10.00"), 0)).isInstanceOf(IllegalArgumentException.class);
    }

    /** As 238.872 divisões de R$ 0,01 a R$ 50,00 em 1x a 48x que têm ao menos 1 centavo por parcela. */
    @Test
    void varreduraSomaOTotalNuncaZeraEAPrimeiraPassaAsOutrasEmMenosDeNCentavos() {
        int divisoes = 0;
        List<String> erradas = new ArrayList<>();
        for (int parcelas = 1; parcelas <= 48; parcelas++) {
            for (long centavos = parcelas; centavos <= 5000; centavos++) {
                BigDecimal total = BigDecimal.valueOf(centavos, 2);
                List<BigDecimal> valores = Parcelamento.dividir(total, parcelas);
                BigDecimal outra = valores.getLast();
                BigDecimal diferenca = valores.getFirst().subtract(outra);
                boolean certa = valores.size() == parcelas
                        && valores.stream().reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(total) == 0
                        && valores.stream().allMatch(valor -> valor.signum() > 0 && valor.scale() == 2)
                        && valores.subList(1, parcelas).stream().allMatch(valor -> valor.compareTo(outra) == 0)
                        && diferenca.signum() >= 0 && diferenca.compareTo(BigDecimal.valueOf(parcelas - 1L, 2)) <= 0;
                if (!certa) {
                    erradas.add(total + " em " + parcelas + "x");
                }
                divisoes++;
            }
        }
        assertThat(erradas).isEmpty();
        assertThat(divisoes).isEqualTo(238_872);
    }
}
