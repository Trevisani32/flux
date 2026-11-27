package com.flux.projecao.interno;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.flux.compartilhado.erro.CampoInvalidoException;
import com.flux.contas.ContaResumo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PedidoDaProjecaoTest {

    @Test
    void osPeriodosEOFimPadrao() {
        PedidoDaProjecao.Periodo nulo = PedidoDaProjecao.periodo(d("2026-11-25"), null);
        PedidoDaProjecao.Periodo vazio = PedidoDaProjecao.periodo(d("2026-11-25"), "");
        PedidoDaProjecao.Periodo espacos = PedidoDaProjecao.periodo(d("2026-11-25"), "  ");

        assertPeriodo(nulo, "2026-11-25", "2026-12-31", "2026-12-31", "2027-02-25", "2027-05-25", "2027-11-25");
        assertThat(vazio).isEqualTo(nulo);
        assertThat(espacos).isEqualTo(nulo);
        assertPeriodo(PedidoDaProjecao.periodo(d("2026-11-25"), "2026-11-26"), "2026-11-25", "2026-11-26", "2026-12-31",
                "2027-02-25", "2027-05-25", "2027-11-25");
        assertPeriodo(PedidoDaProjecao.periodo(d("2026-11-25"), "2027-11-25"), "2026-11-25", "2027-11-25", "2026-12-31",
                "2027-02-25", "2027-05-25", "2027-11-25");
        assertPeriodo(PedidoDaProjecao.periodo(d("2027-01-31"), null), "2027-01-31", "2027-02-28", "2027-02-28", "2027-04-30",
                "2027-07-31", "2028-01-31");
        assertPeriodo(PedidoDaProjecao.periodo(d("2028-02-29"), null), "2028-02-29", "2028-03-31", "2028-03-31", "2028-05-29",
                "2028-08-29", "2029-02-28");
    }

    @Test
    void asDatasQueNaoServem() {
        assertCampo("2026-11-25", "Escolha uma data depois de hoje.");
        assertCampo("2026-11-24", "Escolha uma data depois de hoje.");
        assertCampo("2027-11-26", "A projeção vai até 25/11/2027.");
        assertCampo("31/12/2026", "Escolha uma data válida.");
        assertCampo("2026-02-30", "Escolha uma data válida.");
        assertCampo("amanhã", "Escolha uma data válida.");
    }

    @Test
    void asContasEscolhidas() {
        List<ContaResumo> ativas = List.of(conta(1, "Banco", "CORRENTE"), conta(2, "Investimentos", "INVESTIMENTO"),
                conta(3, "Poupança", "POUPANCA"));

        assertThat(PedidoDaProjecao.escolhidas(null, ativas)).containsExactly(1L, 3L);
        assertThat(PedidoDaProjecao.escolhidas("3,1", ativas)).containsExactly(1L, 3L);
        assertThat(PedidoDaProjecao.escolhidas(" 2 , 2 ", ativas)).containsExactly(2L);
        assertThat(PedidoDaProjecao.escolhidas("1,abc,999,-5,", ativas)).containsExactly(1L);
        assertThat(PedidoDaProjecao.escolhidas("", ativas)).isEmpty();
        assertThat(PedidoDaProjecao.escolhidas("abc", ativas)).isEmpty();
        assertThat(PedidoDaProjecao.escolhidas(null, List.of(conta(2, "Investimentos", "INVESTIMENTO")))).isEmpty();
    }

    private static void assertCampo(String entrada, String mensagem) {
        assertThatThrownBy(() -> PedidoDaProjecao.periodo(d("2026-11-25"), entrada)).isInstanceOf(CampoInvalidoException.class)
                .satisfies(excecao -> {
                    CampoInvalidoException campo = (CampoInvalidoException) excecao;
                    assertThat(campo.getCampo()).isEqualTo("ate");
                    assertThat(campo.getMessage()).isEqualTo(mensagem);
                });
    }

    private static void assertPeriodo(PedidoDaProjecao.Periodo periodo, String hoje, String ate, String padrao, String tresMeses,
                                      String seisMeses, String dozeMeses) {
        assertThat(periodo.hoje()).isEqualTo(d(hoje));
        assertThat(periodo.ate()).isEqualTo(d(ate));
        assertThat(periodo.padrao()).isEqualTo(d(padrao));
        assertThat(periodo.tresMeses()).isEqualTo(d(tresMeses));
        assertThat(periodo.seisMeses()).isEqualTo(d(seisMeses));
        assertThat(periodo.dozeMeses()).isEqualTo(d(dozeMeses));
    }

    private static ContaResumo conta(long id, String nome, String tipo) {
        return new ContaResumo(id, nome, BigDecimal.ZERO.setScale(2), null, false, tipo, null);
    }

    private static LocalDate d(String data) {
        return LocalDate.parse(data);
    }
}
