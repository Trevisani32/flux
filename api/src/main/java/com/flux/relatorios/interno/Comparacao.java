package com.flux.relatorios.interno;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Um valor do mês comparado com a média dos meses anteriores (Fatia 06a, ADR 0020). Os valores chegam sem sinal (o que
 * entrou ou o que saiu) e com escala 2. Só serve para mostrar: nunca é gravado.
 *
 * <ul>
 *   <li>a média é a soma dos meses considerados dividida pela quantidade deles, com 2 casas e HALF_UP (a metade do centavo
 *       sobe: R$ 0,005 vira R$ 0,01);</li>
 *   <li>o percentual é inteiro, sobre a média já arredondada (a que a tela mostra), com HALF_UP: a metade vai para longe do
 *       zero (12,5% vira 13%, e −12,5% vira −13%). Ele vem sem sinal: o tipo diz se é acima ou abaixo;</li>
 *   <li>média zero: {@code NOVO} com valor no mês, {@code IGUAL} sem ele; valor zero com média: 100% abaixo;</li>
 *   <li>o percentual para em {@link Long#MAX_VALUE}: um mês de 1.000 despesas de 13 dígitos sobre uma média de centavos
 *       passaria dele, e o relatório não pode cair por isso.</li>
 * </ul>
 */
public record Comparacao(Tipo tipo, long percentual) {

    public enum Tipo {
        ACIMA,
        ABAIXO,
        IGUAL,
        NOVO
    }

    private static final BigDecimal CEM = BigDecimal.valueOf(100);
    private static final BigDecimal MAXIMO = BigDecimal.valueOf(Long.MAX_VALUE);

    /** A média de {@code soma} em {@code meses} meses; nula sem nenhum mês. */
    public static BigDecimal media(BigDecimal soma, int meses) {
        if (meses == 0) {
            return null;
        }
        return soma.divide(BigDecimal.valueOf(meses), 2, RoundingMode.HALF_UP);
    }

    /** O valor do mês comparado com a média; nulo sem média (nenhum mês anterior com dados). */
    public static Comparacao de(BigDecimal valor, BigDecimal media) {
        if (media == null) {
            return null;
        }
        if (media.signum() == 0) {
            return new Comparacao(valor.signum() == 0 ? Tipo.IGUAL : Tipo.NOVO, 0);
        }
        BigDecimal diferenca = valor.subtract(media).multiply(CEM).divide(media, 0, RoundingMode.HALF_UP);
        if (diferenca.signum() == 0) {
            return new Comparacao(Tipo.IGUAL, 0);
        }
        long percentual = diferenca.abs().min(MAXIMO).longValueExact();
        return new Comparacao(diferenca.signum() > 0 ? Tipo.ACIMA : Tipo.ABAIXO, percentual);
    }
}
