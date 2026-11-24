package com.flux.relatorios.interno;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import com.flux.relatorios.interno.ConsultasDoRelatorio.CategoriaDeDespesa;
import com.flux.relatorios.interno.ConsultasDoRelatorio.GastoNoMes;
import com.flux.relatorios.interno.dto.RelatorioDoMesResponse.Categoria;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GastosPorCategoriaTest {

    private static final YearMonth MES = YearMonth.of(2026, 11);
    private static final List<YearMonth> MESES_DA_MEDIA = List.of(MES.minusMonths(3), MES.minusMonths(2), MES.minusMonths(1));

    @Test
    void aPrincipalSomaAsSubcategoriasEODiretoEComparaComAMedia() {
        Categoria moradia = linha(
                montar(gastosDoMesDeReferencia(), categoriasDoMesDeReferencia()),
                "Moradia");
        assertThat(moradia.valor()).isEqualByComparingTo("1260.50");
        assertThat(moradia.media()).isEqualByComparingTo("1016.67");
        assertThat(moradia.comparacao()).isEqualTo(comparacao(Comparacao.Tipo.ACIMA, 24));
        Categoria aluguel = linha(moradia.subcategorias(), "Aluguel");
        assertThat(aluguel.valor()).isEqualByComparingTo("1200.00");
        assertThat(aluguel.media()).isEqualByComparingTo("1000.00");
        assertThat(aluguel.comparacao()).isEqualTo(comparacao(Comparacao.Tipo.ACIMA, 20));
        Categoria direto = linha(moradia.subcategorias(), "Direto em Moradia");
        assertThat(direto.valor()).isEqualByComparingTo("60.50");
        assertThat(direto.media()).isEqualByComparingTo("16.67");
        assertThat(direto.comparacao()).isEqualTo(comparacao(Comparacao.Tipo.ACIMA, 263));
    }

    @Test
    void asSubcategoriasSoAparecemComGastoEODiretoSoComElas() {
        List<Categoria> semSubcategoria = montar(List.of(gasto(1L, MES, "10")), categoriasDoMesDeReferencia());
        assertThat(linha(semSubcategoria, "Moradia").subcategorias()).isEmpty();

        List<Categoria> comSubcategoria = montar(
                List.of(gasto(1L, MES, "10"), gasto(2L, MES, "1")),
                categoriasDoMesDeReferencia());
        assertThat(linha(comSubcategoria, "Moradia").subcategorias()).extracting(Categoria::nome)
                .containsExactly("Direto em Moradia", "Aluguel");
    }

    @Test
    void semCategoriaEArquivadaSaoLinhasComoAsOutras() {
        List<Categoria> linhas = montar(
                List.of(gasto(null, MES, "3"), gasto(4L, MES.minusMonths(1), "30")),
                categoriasDoMesDeReferencia());
        assertThat(linhas).extracting(Categoria::nome).contains("Sem categoria", "Arquivada");
        Categoria arquivada = linha(linhas, "Arquivada");
        assertThat(arquivada.arquivada()).isTrue();
        assertThat(arquivada.valor()).isEqualByComparingTo("0.00");
        assertThat(arquivada.media()).isEqualByComparingTo("10.00");
        assertThat(arquivada.comparacao()).isEqualTo(comparacao(Comparacao.Tipo.ABAIXO, 100));
    }

    @Test
    void aOrdemEOGastoDoMesDepoisAMediaDepoisONome() {
        List<CategoriaDeDespesa> categorias = List.of(
                new CategoriaDeDespesa(4L, "Transporte", null, false),
                new CategoriaDeDespesa(5L, "Sem categoria", null, false),
                new CategoriaDeDespesa(6L, "Educação", null, false));
        List<Categoria> linhas = montar(
                List.of(gasto(4L, MES, "50"), gasto(5L, MES, "50"), gasto(null, MES, "50"), gasto(6L, MES, "50")),
                categorias);
        assertThat(linhas).extracting(linha -> linha.nome() + ":" + linha.id()).containsExactly(
                "Educação:6",
                "Sem categoria:5",
                "Sem categoria:null",
                "Transporte:4");
    }

    @Test
    void semMesesNaMediaNaoHaComparacao() {
        List<Categoria> linhas = GastosPorCategoria.montar(
                List.of(
                        gasto(2L, MES, "5"),
                        gasto(2L, MES.minusMonths(2), "80"),
                        gasto(2L, MES.minusMonths(4), "90")),
                categoriasDoMesDeReferencia(),
                MES,
                List.of());
        Categoria linha = linhas.getFirst();
        assertThat(linha.valor()).isEqualByComparingTo("5.00");
        assertThat(linha.media()).isNull();
        assertThat(linha.comparacao()).isNull();
    }

    private static List<Categoria> montar(List<GastoNoMes> gastos, List<CategoriaDeDespesa> categorias) {
        return GastosPorCategoria.montar(gastos, categorias, MES, MESES_DA_MEDIA);
    }

    private static List<GastoNoMes> gastosDoMesDeReferencia() {
        return List.of(
                gasto(2L, MES.minusMonths(3), "1000"),
                gasto(2L, MES.minusMonths(2), "900"),
                gasto(1L, MES.minusMonths(2), "50"),
                gasto(2L, MES.minusMonths(1), "1100"),
                gasto(2L, MES, "1200"),
                gasto(1L, MES, "60.50"));
    }

    private static List<CategoriaDeDespesa> categoriasDoMesDeReferencia() {
        return List.of(
                new CategoriaDeDespesa(1L, "Moradia", null, false),
                new CategoriaDeDespesa(2L, "Aluguel", 1L, false),
                new CategoriaDeDespesa(4L, "Arquivada", null, true));
    }

    private static Categoria linha(List<Categoria> linhas, String nome) {
        return linhas.stream().filter(linha -> linha.nome().equals(nome)).findFirst().orElseThrow();
    }

    private static GastoNoMes gasto(Long categoria, YearMonth mes, String valor) {
        return new GastoNoMes(categoria, mes, new BigDecimal(valor));
    }

    private static Comparacao comparacao(Comparacao.Tipo tipo, long percentual) {
        return new Comparacao(tipo, percentual);
    }
}
