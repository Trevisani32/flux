package com.flux.categorizacao.interno;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.flux.categorias.CategoriaResumo;
import com.flux.contas.ContaResumo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Decisões 6, 9, 23 e 24 da 04b: a primeira que casa, pela natureza do sinal e pela conta; a pausada não casa. */
class RegrasAtivasTest {

    private static final Instant AGORA = Instant.parse("2026-11-11T12:00:00Z");
    private static final Map<Long, CategoriaResumo> CATEGORIAS = Map.of(
            10L, new CategoriaResumo(10L, "Delivery", "DESPESA", 1L, "Alimentação", false),
            11L, new CategoriaResumo(11L, "App de transporte", "DESPESA", 2L, "Transporte", false),
            12L, new CategoriaResumo(12L, "Reembolsos", "RECEITA", null, null, false),
            13L, new CategoriaResumo(13L, "Lazer", "DESPESA", null, null, true));
    private static final Map<Long, ContaResumo> CONTAS = Map.of(
            1L, new ContaResumo(1L, "Banco Horizonte", BigDecimal.ZERO, null, false, "CORRENTE"),
            2L, new ContaResumo(2L, "Poupança antiga", BigDecimal.ZERO, null, true, "POUPANCA"));

    private static Regra regra(int posicao, Condicao condicao, String texto, Long contaId, long categoriaId) {
        Regra regra = new Regra(7L, posicao, AGORA);
        regra.alterar(condicao, texto, contaId, CATEGORIAS.get(categoriaId).natureza(), categoriaId, null, AGORA);
        return regra;
    }

    private static Integer posicaoDaPrimeira(RegrasAtivas ativas, long contaId, String valor, String descricao) {
        Regra regra = ativas.primeira(contaId, RegrasAtivas.natureza(new BigDecimal(valor)), Comparacao.chave(descricao));
        return regra == null ? null : regra.getPosicao();
    }

    @Test
    void valeAPrimeiraQueCasaNaNaturezaDoSinal() {
        RegrasAtivas ativas = new RegrasAtivas(List.of(
                regra(1, Condicao.CONTEM, "uber eats", null, 10L),
                regra(2, Condicao.CONTEM, "uber", null, 11L),
                regra(3, Condicao.COMECA_COM, "estorno", null, 12L)), CATEGORIAS, CONTAS);

        assertThat(posicaoDaPrimeira(ativas, 1L, "-52.00", "UBER EATS*PEDIDO 991")).isEqualTo(1);
        assertThat(posicaoDaPrimeira(ativas, 1L, "-23.90", "PAG*UBERTRIP 8812")).isEqualTo(2);
        assertThat(posicaoDaPrimeira(ativas, 1L, "-300.00", "UBERLANDIA PARK HOTEL")).isEqualTo(2);
        assertThat(posicaoDaPrimeira(ativas, 1L, "18.40", "ESTORNO UBER *TRIP")).isEqualTo(3);
        assertThat(posicaoDaPrimeira(ativas, 1L, "18.40", "UBER REEMBOLSO")).isNull();
        assertThat(posicaoDaPrimeira(ativas, 1L, "-9.00", "PADARIA")).isNull();
    }

    @Test
    void filtroDeContaSoPegaAConta() {
        RegrasAtivas ativas = new RegrasAtivas(List.of(regra(1, Condicao.CONTEM, "padaria", 1L, 10L)), CATEGORIAS, CONTAS);

        assertThat(posicaoDaPrimeira(ativas, 1L, "-9.00", "Compra no débito - PADARIA CENTRAL")).isEqualTo(1);
        assertThat(posicaoDaPrimeira(ativas, 3L, "-9.00", "Compra no débito - PADARIA CENTRAL")).isNull();
    }

    @Test
    void categoriaOuContaArquivadaPausaARegra() {
        Regra comLazer = regra(1, Condicao.CONTEM, "cinema", null, 13L);
        Regra daPoupanca = regra(2, Condicao.CONTEM, "cinema", 2L, 11L);
        Regra ativa = regra(3, Condicao.CONTEM, "cinema", null, 11L);
        RegrasAtivas ativas = new RegrasAtivas(List.of(comLazer, daPoupanca, ativa), CATEGORIAS, CONTAS);

        assertThat(RegrasAtivas.pausada(comLazer, CATEGORIAS, CONTAS)).isTrue();
        assertThat(RegrasAtivas.pausada(daPoupanca, CATEGORIAS, CONTAS)).isTrue();
        assertThat(RegrasAtivas.pausada(ativa, CATEGORIAS, CONTAS)).isFalse();
        assertThat(posicaoDaPrimeira(ativas, 2L, "-64.00", "CINEMA ARCOPLEX")).isEqualTo(3);
        assertThat(new RegrasAtivas(List.of(comLazer), CATEGORIAS, CONTAS).vazia()).isTrue();
    }
}
