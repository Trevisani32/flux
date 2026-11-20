package com.flux.lancamentos.interno;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

import com.flux.cartoes.CalendarioDoCartao;
import com.flux.suporte.CartoesBase;
import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static com.flux.suporte.ApiDeTeste.assertCampo;
import static com.flux.suporte.ApiDeTeste.texto;
import static org.assertj.core.api.Assertions.assertThat;

/** A fatura de um mês e o ajuste do fechamento (decisões 8 a 13 da 05a). O Cartão Horizonte fecha dia 3 e vence dia 10. */
class FaturasIT extends CartoesBase {

    @Test
    void aFaturaMostraTotaisItensSituacaoENavegacao() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novoCartao(usuario, "Cartão Horizonte", 3, 10, novaConta(usuario, "Banco Horizonte"));
        long reembolsos = idDaCategoria(usuario, "Reembolsos");
        novaCompra(usuario, horizonte, "2025-11-05", "Sapataria", "450", 3);
        novaCompra(usuario, horizonte, "2025-11-09", "Loja Eletro", "1990", 10);
        api.idCriado(api.post(usuario, "/api/estornos", estorno(horizonte, "2025-11-11", "Estorno Sapataria", "50", reembolsos)));

        MvcTestResult dezembro = api.get(usuario, "/api/cartoes/{id}/faturas/{mes}", horizonte, "2025-12");
        assertThat(dezembro).hasStatus(200).bodyJson().extractingPath("inicio").isEqualTo("2025-11-04");
        assertThat(dezembro).bodyJson().extractingPath("gravada").isEqualTo(true);
        assertThat(dezembro).bodyJson().extractingPath("fechamento").isEqualTo("2025-12-03");
        assertThat(dezembro).bodyJson().extractingPath("vencimento").isEqualTo("2025-12-10");
        assertThat(dezembro).bodyJson().extractingPath("situacao").isEqualTo("FECHADA");
        assertThat(dezembro).bodyJson().extractingPath("ajusteDe").isEqualTo("2025-11-04");
        assertThat(dezembro).bodyJson().extractingPath("ajusteAte").isEqualTo("2025-12-09");
        assertThat(dezembro).bodyJson().extractingPath("quantidadeCompras").isEqualTo(2);
        assertThat(dezembro).bodyJson().extractingPath("quantidadeEstornos").isEqualTo(1);
        assertThat(texto(dezembro)).contains("\"compras\":349.00").contains("\"estornos\":50.00").contains("\"total\":299.00")
                .contains("\"depois\":2091.00");
        assertThat(dezembro).bodyJson().extractingPath("depoisAte").isEqualTo("2026-09");
        assertThat(dezembro).bodyJson().extractingPath("itens[*].descricao").asArray()
                .containsExactly("Estorno Sapataria", "Loja Eletro", "Sapataria");
        assertThat(dezembro).bodyJson().extractingPath("itens[1].parcela").isEqualTo(1);
        assertThat(dezembro).bodyJson().extractingPath("itens[1].parcelas").isEqualTo(10);
        assertThat(dezembro).bodyJson().extractingPath("itens[0].tipo").isEqualTo("ESTORNO");
        assertThat(dezembro).bodyJson().extractingPath("itens[0].categoriaNome").isEqualTo("Reembolsos");
        assertThat(dezembro).bodyJson().extractingPath("primeiroMes").isEqualTo("2025-12");

        LocalDate hoje = LocalDate.now(relogioDeTeste);
        YearMonth aberta = new CalendarioDoCartao(3, 10).faturaDe(hoje, Map.of());
        assertThat(dezembro).bodyJson().extractingPath("ultimoMes").isEqualTo(aberta.plusMonths(12).toString());
        MvcTestResult vazia = api.get(usuario, "/api/cartoes/{id}/faturas/{mes}", horizonte, aberta.plusMonths(3).toString());
        assertThat(vazia).hasStatus(200).bodyJson().extractingPath("itens").asArray().isEmpty();
        assertThat(vazia).bodyJson().extractingPath("situacao").isEqualTo("FUTURA");
        assertThat(vazia).bodyJson().extractingPath("gravada").isEqualTo(false);
        assertThat(vazia).bodyJson().extractingPath("vencimento").isEqualTo(aberta.plusMonths(3).atDay(10).toString());
        assertThat(api.get(usuario, "/api/cartoes/{id}/faturas/{mes}", horizonte, aberta.toString())).bodyJson()
                .extractingPath("situacao").isEqualTo("ABERTA");
        assertCampo(api.get(usuario, "/api/cartoes/{id}/faturas/{mes}", horizonte, "2025-13"), "mes", "Mês inválido.");
        assertThat(api.get(usuario, "/api/cartoes/{id}/faturas/{mes}", 999_999L, "2025-12")).hasStatus(404).bodyJson()
                .extractingPath("detail").isEqualTo("Cartão não encontrado.");
    }

    /**
     * Decisão 12: com dezembro fechando em 04/12, a compra e o estorno de 04/12 passam de janeiro para dezembro, a compra
     * com as parcelas; com 02/12, os de 03/12 e 04/12 vão para janeiro. "Voltar ao fechamento normal" desfaz. O aviso
     * conta as compras e os estornos à parte.
     */
    @Test
    void oAjusteMudaAsComprasDoTrechoDeFaturaComAsParcelas() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novoCartao(usuario, "Cartão Horizonte", 3, 10, novaConta(usuario, "Banco Horizonte"));
        long dia3 = novaCompra(usuario, horizonte, "2025-12-03", "No dia 3", "100", 1);
        long dia4 = novaCompra(usuario, horizonte, "2025-12-04", "No dia 4", "200", 2);
        long devolucao = api.idCriado(api.post(usuario, "/api/estornos", estorno(horizonte, "2025-12-04", "Devolução", "50", null)));
        assertThat(parcelas(dia4)).containsExactly("1 2026-01-10 -100.00 2026-01", "2 2026-02-10 -100.00 2026-02");

        MvcTestResult ajuste = api.put(usuario, "/api/cartoes/{id}/faturas/{mes}/fechamento", "{\"data\":\"2025-12-04\"}", horizonte, "2025-12");
        assertThat(ajuste).hasStatus(200).bodyJson().extractingPath("comprasMovidas").isEqualTo(1);
        assertThat(ajuste).bodyJson().extractingPath("estornosMovidos").isEqualTo(1);
        assertThat(parcelas(devolucao)).containsExactly("1 2025-12-10 50.00 2025-12");
        assertThat(ajuste).bodyJson().extractingPath("fatura.fechamento").isEqualTo("2025-12-04");
        assertThat(ajuste).bodyJson().extractingPath("fatura.fechamentoAjustado").isEqualTo(true);
        assertThat(ajuste).bodyJson().extractingPath("fatura.fechamentoNormal").isEqualTo("2025-12-03");
        assertThat(parcelas(dia4)).containsExactly("1 2025-12-10 -100.00 2025-12", "2 2026-01-10 -100.00 2026-01");
        assertThat(api.get(usuario, "/api/cartoes/{id}/faturas/{mes}", horizonte, "2026-01")).bodyJson().extractingPath("inicio")
                .isEqualTo("2025-12-05");

        MvcTestResult paraTras = api.put(usuario, "/api/cartoes/{id}/faturas/{mes}/fechamento", "{\"data\":\"2025-12-02\"}", horizonte, "2025-12");
        assertThat(paraTras).hasStatus(200).bodyJson().extractingPath("comprasMovidas").isEqualTo(2);
        assertThat(paraTras).bodyJson().extractingPath("estornosMovidos").isEqualTo(1);
        assertThat(parcelas(dia3)).containsExactly("1 2026-01-10 -100.00 2026-01");
        assertThat(parcelas(dia4)).containsExactly("1 2026-01-10 -100.00 2026-01", "2 2026-02-10 -100.00 2026-02");

        MvcTestResult normal = api.delete(usuario, "/api/cartoes/{id}/faturas/{mes}/fechamento", horizonte, "2025-12");
        assertThat(normal).hasStatus(200).bodyJson().extractingPath("comprasMovidas").isEqualTo(1);
        assertThat(normal).bodyJson().extractingPath("estornosMovidos").isEqualTo(0);
        assertThat(normal).bodyJson().extractingPath("fatura.fechamentoAjustado").isEqualTo(false);
        assertThat(parcelas(dia3)).containsExactly("1 2025-12-10 -100.00 2025-12");
        assertThat(faturas(horizonte)).contains("2025-12 2025-12-03 2025-12-10 false");
        assertThat(api.delete(usuario, "/api/cartoes/{id}/faturas/{mes}/fechamento", horizonte, "2025-12")).hasStatus(200).bodyJson()
                .extractingPath("comprasMovidas").isEqualTo(0);
    }

    /** Decisão 12: a data fora dos limites e o cartão arquivado são recusados; a fatura sem lançamento nasce com o ajuste. */
    @Test
    void oAjusteForaDosLimitesECartaoArquivadoSaoRecusados() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novoCartao(usuario, "Cartão Horizonte", 3, 10, novaConta(usuario, "Banco Horizonte"));
        MvcTestResult fora = api.put(usuario, "/api/cartoes/{id}/faturas/{mes}/fechamento", "{\"data\":\"2025-12-10\"}", horizonte, "2025-12");
        assertThat(fora).hasStatus(422).bodyJson().extractingPath("detail").isEqualTo("Escolha uma data de 04/11/2025 a 09/12/2025: depois "
                + "do fechamento de novembro e antes do vencimento de dezembro.");
        assertThat(fora).bodyJson().extractingPath("campo").isEqualTo("data");
        assertThat(api.put(usuario, "/api/cartoes/{id}/faturas/{mes}/fechamento", "{\"data\":\"2025-11-03\"}", horizonte, "2025-12"))
                .hasStatus(422);
        assertThat(contar("faturas", usuario)).isZero();
        assertThat(api.put(usuario, "/api/cartoes/{id}/faturas/{mes}/fechamento", "{\"data\":\"2025-11-04\"}", horizonte, "2025-12"))
                .hasStatus(200).bodyJson().extractingPath("comprasMovidas").isEqualTo(0);
        assertThat(faturas(horizonte)).containsExactly("2025-12 2025-11-04 2025-12-10 true");
        assertCampo(api.put(usuario, "/api/cartoes/{id}/faturas/{mes}/fechamento", "{\"data\":null}", horizonte, "2025-12"), "data",
                "Informe a data do fechamento.");
        assertThat(api.acao(usuario, "/api/cartoes/{id}/arquivar", horizonte)).hasStatus(200);
        assertThat(api.put(usuario, "/api/cartoes/{id}/faturas/{mes}/fechamento", "{\"data\":\"2025-11-05\"}", horizonte, "2025-12"))
                .hasStatus(422).bodyJson().extractingPath("detail").isEqualTo("Cartão Horizonte está arquivado. Reative o cartão para ajustar o fechamento.");
        assertThat(api.get(usuario, "/api/cartoes/{id}/faturas/{mes}", horizonte, "2025-12")).hasStatus(200).bodyJson()
                .extractingPath("cartaoArquivado").isEqualTo(true);
    }

    /** Decisões 12 e 21: o ajuste que levaria um mês a passar de 1.000 lançamentos é recusado, sem mudar nada. */
    @Test
    void oAjusteQuePassariaDoLimiteDoMesNaoMudaNada() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long conta = novaConta(usuario, "Banco Horizonte");
        long horizonte = novoCartao(usuario, "Cartão Horizonte", 3, 10, conta);
        long dia4 = novaCompra(usuario, horizonte, "2025-12-04", "No dia 4", "100", 1);
        jdbc.update("insert into lancamentos (usuario_id, conta_id, data, descricao, descricao_chave, valor, tipo, origem) "
                + "select ?, ?, date '2025-12-01' + (n % 28), 'Lote', 'lote', -1, 'DESPESA', 'MANUAL' from generate_series(1, 1000) n",
                usuario.id(), conta);
        MvcTestResult recusado = api.put(usuario, "/api/cartoes/{id}/faturas/{mes}/fechamento", "{\"data\":\"2025-12-04\"}", horizonte, "2025-12");
        assertThat(recusado).hasStatus(422).bodyJson().extractingPath("detail")
                .isEqualTo("O ajuste passaria do limite de 1.000 lançamentos em dezembro de 2025.");
        assertThat(parcelas(dia4)).containsExactly("1 2026-01-10 -100.00 2026-01");
        assertThat(faturas(horizonte)).containsExactly("2026-01 2026-01-03 2026-01-10 false");
    }
}
