package com.flux.lancamentos.interno;

import java.time.YearMonth;

import com.flux.compartilhado.erro.CampoInvalidoException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FiltroDaListaTest {

    @Test
    void leOsParametrosDaUrl() {
        FiltroDaLista filtro = FiltroDaLista.de(YearMonth.of(2026, 11), 3L, null, "12", "despesa", "  Mercado   bom ", true);
        assertThat(filtro).isEqualTo(new FiltroDaLista(YearMonth.of(2026, 11), 3L, null, 12L, false, TipoLancamento.DESPESA,
                "Mercado bom", true));
        assertThat(FiltroDaLista.de(null, null, null, "sem", "transferencia", "   ", true))
                .isEqualTo(new FiltroDaLista(null, null, null, null, true, TipoLancamento.TRANSFERENCIA, null, true));
        assertThat(FiltroDaLista.de(null, null, null, null, "receita", null, true).tipo()).isEqualTo(TipoLancamento.RECEITA);
        assertThat(FiltroDaLista.de(null, null, null, "", "", "", true))
                .isEqualTo(new FiltroDaLista(null, null, null, null, false, null, null, true));
    }

    @Test
    void recusaCategoriaETipoDesconhecidosEBuscaLonga() {
        assertThatThrownBy(() -> FiltroDaLista.de(null, null, null, "abc", null, null, true))
                .isInstanceOf(CampoInvalidoException.class).hasMessage("Filtro de categoria inválido.");
        assertThatThrownBy(() -> FiltroDaLista.de(null, null, null, null, "DESPESA", null, true))
                .isInstanceOf(CampoInvalidoException.class).hasMessage("Filtro de tipo inválido.");
        assertThatThrownBy(() -> FiltroDaLista.de(null, null, null, null, null, "x".repeat(101), true))
                .isInstanceOf(CampoInvalidoException.class).hasMessage("Use no máximo 100 caracteres.");
        assertThat(FiltroDaLista.de(null, null, null, null, null, "x".repeat(100), true).busca()).hasSize(100);
    }

    /** Fatia 05a (decisão 23): o filtro é de uma conta ou de um cartão, nunca dos dois. */
    @Test
    void filtraPorUmCartaoOuPorUmaContaNuncaOsDois() {
        assertThat(FiltroDaLista.de(null, null, 2L, null, null, null, true))
                .isEqualTo(new FiltroDaLista(null, null, 2L, null, false, null, null, true));
        assertThatThrownBy(() -> FiltroDaLista.de(null, 3L, 2L, null, null, null, true))
                .isInstanceOf(CampoInvalidoException.class).hasMessage("Filtre por uma conta ou por um cartão, não pelos dois.");
    }

    @Test
    void quantidadesNoSingularNoPluralEComMilhar() {
        assertThat(Quantidades.lancamentos(1)).isEqualTo("1 lançamento");
        assertThat(Quantidades.lancamentos(1234)).isEqualTo("1.234 lançamentos");
        assertThat(Quantidades.transferencias(1)).isEqualTo("1 transferência");
        assertThat(Quantidades.transferencias(3)).isEqualTo("3 transferências");
    }
}
