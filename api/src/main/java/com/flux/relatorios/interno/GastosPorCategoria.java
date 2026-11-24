package com.flux.relatorios.interno;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.flux.compartilhado.texto.TextoComparavel;
import com.flux.relatorios.interno.ConsultasDoRelatorio.CategoriaDeDespesa;
import com.flux.relatorios.interno.ConsultasDoRelatorio.GastoNoMes;
import com.flux.relatorios.interno.dto.RelatorioDoMesResponse.Categoria;

/**
 * Os gastos por categoria (Fatia 06a, ADR 0020), sem banco. Só despesas, sem sinal.
 *
 * <ul>
 *   <li>uma linha por categoria principal com gasto no mês ou nos meses da média; a principal soma as subcategorias e o
 *       lançado direto nela; "Sem categoria" é uma linha como as outras, com o id nulo;</li>
 *   <li>a principal leva as subcategorias com gasto no mês ou na média e, se houver alguma, o item "Direto em Alimentação"
 *       quando também houve gasto direto nela; sem nenhuma subcategoria com gasto, a lista vem vazia;</li>
 *   <li>a média de cada linha é a soma dos meses da média dividida pela quantidade deles (o mês sem gasto conta como zero),
 *       e a comparação é a do {@link Comparacao};</li>
 *   <li>a ordem, nas principais e dentro delas: o gasto do mês, maior primeiro; no empate, a média, maior primeiro; depois o
 *       nome, em ordem alfabética do pt-BR; por fim o id, com "Sem categoria" por último.</li>
 * </ul>
 */
final class GastosPorCategoria {

    static final String SEM_CATEGORIA = "Sem categoria";
    static final String DIRETO_EM = "Direto em ";

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private GastosPorCategoria() {
    }

    static List<Categoria> montar(List<GastoNoMes> gastos, List<CategoriaDeDespesa> categorias, YearMonth mes,
                                  List<YearMonth> mesesDaMedia) {
        Map<Long, CategoriaDeDespesa> porId = categorias.stream().collect(Collectors.toMap(CategoriaDeDespesa::id, Function.identity()));
        Map<Long, Soma> principais = new HashMap<>();
        Map<Long, Soma> subcategorias = new HashMap<>();
        Map<Long, Soma> diretos = new HashMap<>();
        Soma semCategoria = new Soma();
        for (GastoNoMes gasto : gastos) {
            boolean doMes = gasto.mes().equals(mes);
            if (!doMes && !mesesDaMedia.contains(gasto.mes())) {
                continue;
            }
            if (gasto.categoriaId() == null) {
                semCategoria.somar(gasto.valor(), doMes);
                continue;
            }
            CategoriaDeDespesa categoria = Objects.requireNonNull(porId.get(gasto.categoriaId()), "categoria de despesa");
            Long principal = categoria.maeId() == null ? categoria.id() : categoria.maeId();
            principais.computeIfAbsent(principal, id -> new Soma()).somar(gasto.valor(), doMes);
            (categoria.maeId() == null ? diretos : subcategorias).computeIfAbsent(categoria.id(), id -> new Soma())
                    .somar(gasto.valor(), doMes);
        }

        int meses = mesesDaMedia.size();
        Comparator<Categoria> ordem = ordem();
        List<Categoria> linhas = new ArrayList<>();
        principais.forEach((id, soma) -> {
            CategoriaDeDespesa principal = Objects.requireNonNull(porId.get(id), "categoria principal");
            List<Categoria> filhas = new ArrayList<>();
            subcategorias.forEach((subId, daSub) -> {
                CategoriaDeDespesa sub = porId.get(subId);
                if (id.equals(sub.maeId()) && daSub.temGasto()) {
                    filhas.add(linha(sub.id(), sub.nome(), sub.arquivada(), false, daSub, meses, List.of()));
                }
            });
            Soma direto = diretos.get(id);
            if (!filhas.isEmpty() && direto != null && direto.temGasto()) {
                filhas.add(linha(id, DIRETO_EM + principal.nome(), false, true, direto, meses, List.of()));
            }
            filhas.sort(ordem);
            if (soma.temGasto()) {
                linhas.add(linha(id, principal.nome(), principal.arquivada(), false, soma, meses, List.copyOf(filhas)));
            }
        });
        if (semCategoria.temGasto()) {
            linhas.add(linha(null, SEM_CATEGORIA, false, false, semCategoria, meses, List.of()));
        }
        linhas.sort(ordem);
        return List.copyOf(linhas);
    }

    private static Categoria linha(Long id, String nome, boolean arquivada, boolean direto, Soma soma, int meses,
                                   List<Categoria> subcategorias) {
        BigDecimal media = Comparacao.media(soma.daMedia, meses);
        return new Categoria(id, nome, arquivada, direto, soma.doMes, media, Comparacao.de(soma.doMes, media), subcategorias);
    }

    private static Comparator<Categoria> ordem() {
        return Comparator.comparing(Categoria::valor, Comparator.reverseOrder())
                .thenComparing(categoria -> categoria.media() == null ? ZERO : categoria.media(), Comparator.reverseOrder())
                .thenComparing(Categoria::nome, TextoComparavel.ordemAlfabetica())
                .thenComparing(Categoria::id, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    /** O gasto do mês e a soma dos meses da média. */
    private static final class Soma {

        private BigDecimal doMes = ZERO;
        private BigDecimal daMedia = ZERO;

        void somar(BigDecimal valor, boolean noMes) {
            if (noMes) {
                doMes = doMes.add(valor);
            } else {
                daMedia = daMedia.add(valor);
            }
        }

        boolean temGasto() {
            return doMes.signum() > 0 || daMedia.signum() > 0;
        }
    }
}
