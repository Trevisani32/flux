package com.flux.lancamentos.interno;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/** Decisão 27 da 04c: o par tem o valor oposto, em outra conta ativa, até 5 dias para cada lado; o mais perto primeiro. */
class ParesTest {

    private static final Instant AGORA = Instant.parse("2026-11-10T12:00:00Z");
    private static final Set<Long> ATIVAS = Set.of(1L, 2L, 3L);

    @Test
    void serveSoOValorOpostoEmOutraContaAtivaComAteCincoDias() {
        Lancamento ted = lancamento(1, 1, "2026-12-02", "-1000.00");

        assertThat(Pares.serve(ted, lancamento(2, 2, "2026-12-03", "1000.00"), ATIVAS)).isTrue();
        assertThat(Pares.serve(ted, lancamento(3, 2, "2026-12-07", "1000.00"), ATIVAS)).as("5 dias depois").isTrue();
        assertThat(Pares.serve(ted, lancamento(4, 2, "2026-11-27", "1000.00"), ATIVAS)).as("5 dias antes").isTrue();
        assertThat(Pares.serve(ted, lancamento(5, 2, "2026-12-08", "1000.00"), ATIVAS)).as("6 dias depois").isFalse();
        assertThat(Pares.serve(ted, lancamento(6, 2, "2026-11-26", "1000.00"), ATIVAS)).as("6 dias antes").isFalse();
        assertThat(Pares.serve(ted, lancamento(7, 2, "2026-12-02", "-1000.00"), ATIVAS)).as("mesmo sinal").isFalse();
        assertThat(Pares.serve(ted, lancamento(8, 2, "2026-12-02", "999.99"), ATIVAS)).as("outro valor").isFalse();
        assertThat(Pares.serve(ted, lancamento(9, 1, "2026-12-02", "1000.00"), ATIVAS)).as("mesma conta").isFalse();
        assertThat(Pares.serve(ted, lancamento(10, 4, "2026-12-02", "1000.00"), ATIVAS)).as("conta arquivada").isFalse();
        Lancamento ignorado = lancamento(11, 2, "2026-12-02", "1000.00");
        ignorado.ignorar(AGORA);
        assertThat(Pares.serve(ted, ignorado, ATIVAS)).as("excluído").isFalse();
        Lancamento lado = lancamento(12, 2, "2026-12-02", "1000.00");
        lado.converterEmLado(30L, "Transferência", AGORA);
        assertThat(Pares.serve(ted, lado, ATIVAS)).as("numa transferência").isFalse();
        assertThat(Pares.serve(ted, ted, ATIVAS)).as("ele mesmo").isFalse();
    }

    @Test
    void oMaisPertoVemPrimeiroDepoisOMaisAntigoEOMenorId() {
        Lancamento ted = lancamento(1, 1, "2026-12-02", "-500.00");
        Lancamento quatroDepois = lancamento(2, 2, "2026-12-06", "500.00");
        Lancamento umAntes = lancamento(3, 3, "2026-12-01", "500.00");
        Lancamento umDepois = lancamento(4, 2, "2026-12-03", "500.00");
        Lancamento umDepoisDeNovo = lancamento(5, 3, "2026-12-03", "500.00");
        Lancamento mesmoDia = lancamento(6, 2, "2026-12-02", "500.00");

        assertThat(Pares.de(ted, List.of(quatroDepois, umDepoisDeNovo, umDepois, umAntes, mesmoDia, ted), ATIVAS))
                .containsExactly(mesmoDia, umAntes, umDepois, umDepoisDeNovo, quatroDepois);
    }

    /** Uma receita ou despesa manual, com o id dado (o banco daria um). {@code valor} com sinal. */
    private static Lancamento lancamento(long id, long contaId, String data, String valor) {
        BigDecimal comSinal = new BigDecimal(valor);
        Lancamento lancamento = new Lancamento(7L, null, AGORA);
        lancamento.alterar(comSinal.signum() > 0 ? TipoLancamento.RECEITA : TipoLancamento.DESPESA, contaId, LocalDate.parse(data), "Teste",
                comSinal, null, null, AGORA);
        ReflectionTestUtils.setField(lancamento, "id", id);
        return lancamento;
    }
}