package com.flux.relatorios.interno;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Os meses de um relatório (Fatia 06a, ADR 0020), sem banco.
 *
 * <ul>
 *   <li>{@code mes}, o mês mostrado: o pedido, se estiver entre o primeiro mês com dados e o mês atual; senão (sem pedido,
 *       fora da faixa ou sem dados), o atual. Não há mês futuro;</li>
 *   <li>{@code inicio}, o primeiro dos até 12 meses dos gráficos, que terminam em {@code mes} e nunca começam antes do
 *       primeiro mês com dados;</li>
 *   <li>{@code media}, os até 3 meses antes de {@code mes} que não são anteriores ao primeiro mês com dados, do mais antigo
 *       ao mais novo. Vazia, não há comparação.</li>
 * </ul>
 */
public record JanelaDoRelatorio(YearMonth mes, YearMonth inicio, List<YearMonth> media) {

    static final int MESES_DOS_GRAFICOS = 12;
    static final int MESES_DA_MEDIA = 3;

    /** {@code primeiro} é o mês do primeiro dado; nulo quando o usuário não tem nenhuma conta. */
    public static JanelaDoRelatorio de(YearMonth pedido, YearMonth primeiro, YearMonth atual) {
        if (primeiro == null) {
            return new JanelaDoRelatorio(atual, atual, List.of());
        }
        YearMonth primeiroComDados = primeiro.isAfter(atual) ? atual : primeiro;
        YearMonth mes = pedido == null || pedido.isAfter(atual) || pedido.isBefore(primeiroComDados) ? atual : pedido;
        YearMonth inicio = mes.minusMonths(MESES_DOS_GRAFICOS - 1);
        if (inicio.isBefore(primeiroComDados)) {
            inicio = primeiroComDados;
        }
        List<YearMonth> media = new ArrayList<>();
        for (int antes = MESES_DA_MEDIA; antes >= 1; antes--) {
            YearMonth anterior = mes.minusMonths(antes);
            if (!anterior.isBefore(primeiroComDados)) {
                media.add(anterior);
            }
        }
        return new JanelaDoRelatorio(mes, inicio, List.copyOf(media));
    }

    /** O primeiro mês que os gastos por categoria leem: o primeiro da média ou, sem média, o próprio mês. */
    public YearMonth inicioDaMedia() {
        return media.isEmpty() ? mes : media.getFirst();
    }
}
