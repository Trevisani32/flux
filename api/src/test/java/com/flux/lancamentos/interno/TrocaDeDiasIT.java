package com.flux.lancamentos.interno;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.flux.cartoes.CalendarioDoCartao;
import com.flux.cartoes.CalendarioDoCartao.Datas;
import com.flux.suporte.CartoesBase;
import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Decisão 7 da 05a: os dias novos valem da fatura seguinte à aberta. A aberta e as anteriores ficam (gravadas desde a
 * véspera do mês de 10 anos atrás, ou da primeira fatura com lançamento, se for antes), as futuras voltam às datas dos
 * dias novos e as compras delas trocam de fatura se for o caso. Depende de hoje: as datas esperadas saem do calendário.
 */
class TrocaDeDiasIT extends CartoesBase {

    private static final CalendarioDoCartao ANTIGO = new CalendarioDoCartao(3, 10);
    private static final CalendarioDoCartao NOVO = new CalendarioDoCartao(13, 20);

    @Test
    void aTrocaGuardaAAbertaEAsFechadasERecalculaAsFuturas() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long conta = novaConta(usuario, "Banco Horizonte");
        long horizonte = novoCartao(usuario, "Cartão Horizonte", 3, 10, conta);
        LocalDate hoje = LocalDate.now(relogioDeTeste);
        LocalDate daqui40Dias = hoje.plusDays(40);
        long antiga = novaCompra(usuario, horizonte, "2025-01-15", "Antiga", "10", 1);
        long deHoje = novaCompra(usuario, horizonte, hoje.toString(), "De hoje", "20", 1);
        long futura = novaCompra(usuario, horizonte, daqui40Dias.toString(), "Futura", "60", 2);
        YearMonth aberta = ANTIGO.faturaDe(hoje, Map.of());
        LocalDate fechamentoDaAberta = ANTIGO.padrao(aberta).fechamento();

        MvcTestResult previa = api.post(usuario, "/api/cartoes/{id}/previa-dos-dias", "{\"diaFechamento\":13,\"diaVencimento\":20}", horizonte);
        Datas primeira = NOVO.datas(aberta.plusMonths(1), Map.of(aberta, ANTIGO.padrao(aberta)));
        assertThat(previa).hasStatus(200).bodyJson().extractingPath("troca.abertaMes").isEqualTo(aberta.toString());
        assertThat(previa).bodyJson().extractingPath("troca.abertaFechamento").isEqualTo(fechamentoDaAberta.toString());
        assertThat(previa).bodyJson().extractingPath("troca.primeiraMes").isEqualTo(aberta.plusMonths(1).toString());
        assertThat(previa).bodyJson().extractingPath("troca.primeiraInicio").isEqualTo(fechamentoDaAberta.plusDays(1).toString());
        assertThat(previa).bodyJson().extractingPath("troca.primeiraFechamento").isEqualTo(primeira.fechamento().toString());
        assertThat(previa).bodyJson().extractingPath("troca.primeiraVencimento").isEqualTo(primeira.vencimento().toString());
        assertThat(previa).bodyJson().extractingPath("valemJa").isEqualTo(false);
        MvcTestResult osMesmos = api.post(usuario, "/api/cartoes/{id}/previa-dos-dias", "{\"diaFechamento\":3,\"diaVencimento\":10}", horizonte);
        assertThat(osMesmos).bodyJson().extractingPath("troca").isNull();
        assertThat(osMesmos).bodyJson().extractingPath("valemJa").isEqualTo(false);

        assertThat(api.put(usuario, "/api/cartoes/{id}", cartao("Cartão Horizonte", "5000", 13, 20, conta, "null"), horizonte)).hasStatus(200)
                .bodyJson().extractingPath("diaFechamento").isEqualTo(13);

        // Da véspera do mês mais antigo entre o de 10 anos atrás e o da primeira fatura com lançamento (fevereiro de
        // 2025) até a aberta, todas gravadas com os dias antigos.
        Map<YearMonth, Datas> gravadas = gravadas(horizonte);
        YearMonth janela = YearMonth.from(hoje.minusYears(10));
        YearMonth inicio = (janela.isBefore(YearMonth.of(2025, 2)) ? janela : YearMonth.of(2025, 2)).minusMonths(1);
        long meses = ChronoUnit.MONTHS.between(inicio, aberta) + 1;
        for (YearMonth mes = inicio; !mes.isAfter(aberta); mes = mes.plusMonths(1)) {
            assertThat(gravadas.get(mes)).as(mes.toString()).isEqualTo(ANTIGO.padrao(mes));
        }
        assertThat(gravadas.keySet().stream().filter(mes -> !mes.isAfter(aberta)).count()).isEqualTo(meses);
        assertThat(parcelas(antiga)).containsExactly("1 2025-02-10 -10.00 2025-02");
        assertThat(parcelas(deHoje)).containsExactly("1 " + ANTIGO.padrao(aberta).vencimento() + " -20.00 " + aberta);

        // As futuras gravadas estão nos dias novos, e a compra futura caiu na fatura do período dela.
        gravadas.forEach((mes, datas) -> {
            if (mes.isAfter(aberta)) {
                assertThat(datas).as(mes.toString()).isEqualTo(NOVO.datas(mes, semEla(gravadas, mes)));
            }
        });
        YearMonth daFutura = NOVO.faturaDe(daqui40Dias, gravadas);
        assertThat(parcelas(futura)).containsExactly("1 " + gravadas.get(daFutura).vencimento() + " -30.00 " + daFutura,
                "2 " + gravadas.get(daFutura.plusMonths(1)).vencimento() + " -30.00 " + daFutura.plusMonths(1));
        assertThat(NOVO.inicio(daFutura, gravadas)).isBeforeOrEqualTo(daqui40Dias);

        // Outra troca não puxa o começo para trás: as gravadas vazias não contam.
        assertThat(api.put(usuario, "/api/cartoes/{id}", cartao("Cartão Horizonte", "5000", 3, 10, conta, "null"), horizonte)).hasStatus(200);
        assertThat(gravadas(horizonte).keySet()).allMatch(mes -> !mes.isBefore(inicio));
        assertThat(gravadas(horizonte).keySet().stream().filter(mes -> !mes.isAfter(aberta)).count()).isEqualTo(meses);
    }

    /**
     * A compra em andamento não grava a fatura das parcelas de antes do Flux (decisão 17). Editada depois da troca, só
     * no valor, as parcelas ficam nas faturas delas: nenhuma vai para uma fatura que já fechou.
     */
    @Test
    void aCompraEmAndamentoEditadaDepoisDaTrocaFicaNasFaturasDela() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long conta = novaConta(usuario, "Banco Horizonte");
        long horizonte = novoCartao(usuario, "Cartão Horizonte", 3, 10, conta);
        YearMonth agora = YearMonth.from(LocalDate.now(relogioDeTeste));
        // Dia 15, depois do fechamento (3): a 1ª parcela é do mês seguinte, e a 5ª, deste mês.
        String data = agora.minusMonths(5).atDay(15).toString();
        long compra = api.idCriado(api.post(usuario, "/api/compras", compra(horizonte, data, "Geladeira", "2490", 10, 5, null)));
        List<String> mesesAntes = parcelas(compra).stream().map(linha -> linha.substring(linha.lastIndexOf(' ') + 1)).toList();
        assertThat(mesesAntes).first().isEqualTo(agora.toString());

        assertThat(api.put(usuario, "/api/cartoes/{id}", cartao("Cartão Horizonte", "5000", 20, 27, conta, "null"), horizonte)).hasStatus(200);
        assertThat(api.put(usuario, "/api/compras/{id}", compra(horizonte, data, "Geladeira", "2500", 10, 5, null), compra)).hasStatus(200);
        assertThat(parcelas(compra)).extracting(linha -> linha.substring(linha.lastIndexOf(' ') + 1)).containsExactlyElementsOf(mesesAntes);
        assertThat(parcelas(compra)).allMatch(linha -> linha.contains(" -250.00 "));
        assertThat(parcelas(compra).getFirst()).isEqualTo("5 " + agora.atDay(10) + " -250.00 " + agora);
    }

    /** A compra esquecida, lançada depois da troca num mês passado sem compra, cai pelos dias que valiam na época. */
    @Test
    void aCompraEsquecidaDepoisDaTrocaCaiPelosDiasDeAntes() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long conta = novaConta(usuario, "Banco Horizonte");
        long horizonte = novoCartao(usuario, "Cartão Horizonte", 3, 10, conta);
        YearMonth agora = YearMonth.from(LocalDate.now(relogioDeTeste));
        novaCompra(usuario, horizonte, agora.minusMonths(3).atDay(15).toString(), "Loja", "100", 1);
        assertThat(api.put(usuario, "/api/cartoes/{id}", cartao("Cartão Horizonte", "5000", 20, 27, conta, "null"), horizonte)).hasStatus(200);

        // Pelos dias de antes (fecha 3), o dia 15 cai na fatura do mês seguinte; pelos novos (fecha 20), cairia na do mês.
        long esquecida = novaCompra(usuario, horizonte, agora.minusMonths(6).atDay(15).toString(), "Esquecida", "10", 1);
        assertThat(parcelas(esquecida)).containsExactly("1 " + agora.minusMonths(5).atDay(10) + " -10.00 " + agora.minusMonths(5));
    }

    /** Os dias novos que trariam uma compra para um mês já com 1.000 lançamentos são recusados, e nada muda. */
    @Test
    void osDiasNovosQuePassariamDoLimiteDoMesNaoMudamNada() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long conta = novaConta(usuario, "Banco Horizonte");
        long horizonte = novoCartao(usuario, "Cartão Horizonte", 3, 10, conta);
        YearMonth aberta = ANTIGO.faturaDe(LocalDate.now(relogioDeTeste), Map.of());
        // Dia 8, depois do fechamento antigo (3) e antes do novo (13): a compra passa do mês seguinte para o dela.
        YearMonth doMes = aberta.plusMonths(2);
        long compra = novaCompra(usuario, horizonte, doMes.atDay(8).toString(), "Loja", "100", 1);
        assertThat(parcelas(compra)).containsExactly("1 " + doMes.plusMonths(1).atDay(10) + " -100.00 " + doMes.plusMonths(1));
        List<String> antes = faturas(horizonte);
        jdbc.update("insert into lancamentos (usuario_id, conta_id, data, descricao, descricao_chave, valor, tipo, origem) "
                + "select ?, ?, ?::date + (n % 28), 'Lote', 'lote', -1, 'DESPESA', 'MANUAL' from generate_series(1, 1000) n",
                usuario.id(), conta, doMes.atDay(1));

        MvcTestResult recusado = api.put(usuario, "/api/cartoes/{id}", cartao("Cartão Horizonte", "5000", 13, 20, conta, "null"), horizonte);
        assertThat(recusado).hasStatus(422).bodyJson().extractingPath("detail").isEqualTo("Os dias novos passariam do limite de 1.000 "
                + "lançamentos em " + doMes.atDay(1).format(DateTimeFormatter.ofPattern("MMMM 'de' yyyy", Locale.forLanguageTag("pt-BR"))) + ".");
        assertThat(api.get(usuario, "/api/cartoes/{id}", horizonte)).bodyJson().extractingPath("diaFechamento").isEqualTo(3);
        assertThat(faturas(horizonte)).isEqualTo(antes);
        assertThat(parcelas(compra)).containsExactly("1 " + doMes.plusMonths(1).atDay(10) + " -100.00 " + doMes.plusMonths(1));
    }

    /**
     * Decisões 7 e 12: depois de uma troca de dias, o fechamento normal da aberta e das anteriores é o dos dias de antes;
     * o das seguintes, o dos dias novos. "Voltar ao fechamento normal" na aberta ajustada volta a ele, e a compra fica.
     */
    @Test
    void depoisDaTrocaOFechamentoNormalDaAbertaEDasAnterioresEODosDiasDeAntes() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long conta = novaConta(usuario, "Banco Horizonte");
        long horizonte = novoCartao(usuario, "Cartão Horizonte", 3, 10, conta);
        LocalDate hoje = LocalDate.now(relogioDeTeste);
        YearMonth aberta = ANTIGO.faturaDe(hoje, Map.of());
        LocalDate normal = ANTIGO.padrao(aberta).fechamento();
        LocalDate ajustado = normal.plusDays(1);
        long compra = novaCompra(usuario, horizonte, hoje.toString(), "Loja", "300", 3);
        assertThat(api.put(usuario, "/api/cartoes/{id}/faturas/{mes}/fechamento", "{\"data\":\"" + ajustado + "\"}", horizonte,
                aberta.toString())).hasStatus(200);
        assertThat(api.put(usuario, "/api/cartoes/{id}", cartao("Cartão Horizonte", "5000", 13, 20, conta, "null"), horizonte)).hasStatus(200);

        // Pelos dias novos (fecha 13), o normal da aberta, preso nos limites do ajuste, seria o dia 9.
        MvcTestResult daAberta = api.get(usuario, "/api/cartoes/{id}/faturas/{mes}", horizonte, aberta.toString());
        assertThat(daAberta).hasStatus(200).bodyJson().extractingPath("fechamento").isEqualTo(ajustado.toString());
        assertThat(daAberta).bodyJson().extractingPath("fechamentoNormal").isEqualTo(normal.toString());
        YearMonth anterior = aberta.minusMonths(2);
        assertThat(api.get(usuario, "/api/cartoes/{id}/faturas/{mes}", horizonte, anterior.toString())).bodyJson()
                .extractingPath("fechamentoNormal").isEqualTo(ANTIGO.padrao(anterior).fechamento().toString());
        MvcTestResult seguinte = api.get(usuario, "/api/cartoes/{id}/faturas/{mes}", horizonte, aberta.plusMonths(1).toString());
        assertThat(seguinte).bodyJson().extractingPath("fechamentoNormal")
                .isEqualTo(gravadas(horizonte).get(aberta.plusMonths(1)).fechamento().toString());

        List<String> antes = parcelas(compra);
        MvcTestResult deVolta = api.delete(usuario, "/api/cartoes/{id}/faturas/{mes}/fechamento", horizonte, aberta.toString());
        assertThat(deVolta).hasStatus(200).bodyJson().extractingPath("fatura.fechamento").isEqualTo(normal.toString());
        assertThat(deVolta).bodyJson().extractingPath("fatura.fechamentoAjustado").isEqualTo(false);
        assertThat(parcelas(compra)).isEqualTo(antes);
    }

    /** O cartão novo, sem lançamento nem ajuste (exceção da decisão 7): os dias novos valem já, inclusive na aberta. */
    @Test
    void semFaturaOsDiasNovosValemJa() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long conta = novaConta(usuario, "Banco Horizonte");
        long horizonte = novoCartao(usuario, "Cartão Horizonte", 3, 10, conta);
        MvcTestResult previa = api.post(usuario, "/api/cartoes/{id}/previa-dos-dias", "{\"diaFechamento\":13,\"diaVencimento\":20}", horizonte);
        assertThat(previa).hasStatus(200).bodyJson().extractingPath("troca").isNull();
        assertThat(previa).bodyJson().extractingPath("valemJa").isEqualTo(true);
        assertThat(api.put(usuario, "/api/cartoes/{id}", cartao("Cartão Horizonte", "5000", 13, 20, conta, "null"), horizonte)).hasStatus(200);
        assertThat(contar("faturas", usuario)).isZero();
        YearMonth aberta = NOVO.faturaDe(LocalDate.now(relogioDeTeste), Map.of());
        assertThat(api.get(usuario, "/api/cartoes/{id}", horizonte)).bodyJson().extractingPath("faturaAberta.fechamento")
                .isEqualTo(NOVO.padrao(aberta).fechamento().toString());
    }

    /**
     * Exceção das decisões 7 e 9, aprovada pelo dono: com a compra lançada e excluída, o cartão fica sem lançamento e sem
     * ajuste. Os dias novos valem já, inclusive na aberta, as faturas vazias que restaram saem, e a compra lançada de
     * novo cai pelos dias novos.
     */
    @Test
    void comAsComprasExcluidasOsDiasNovosValemJaEAsFaturasVaziasSaem() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long conta = novaConta(usuario, "Banco Horizonte");
        long horizonte = novoCartao(usuario, "Cartão Horizonte", 3, 10, conta);
        LocalDate hoje = LocalDate.now(relogioDeTeste);
        long porEngano = novaCompra(usuario, horizonte, hoje.toString(), "Por engano", "300", 3);
        assertThat(api.delete(usuario, "/api/compras/{id}", porEngano)).hasStatus(204);
        assertThat(contar("faturas", usuario)).isEqualTo(3);

        MvcTestResult previa = api.post(usuario, "/api/cartoes/{id}/previa-dos-dias", "{\"diaFechamento\":20,\"diaVencimento\":27}", horizonte);
        assertThat(previa).hasStatus(200).bodyJson().extractingPath("troca").isNull();
        assertThat(previa).bodyJson().extractingPath("valemJa").isEqualTo(true);
        assertThat(api.put(usuario, "/api/cartoes/{id}", cartao("Cartão Horizonte", "5000", 20, 27, conta, "null"), horizonte)).hasStatus(200);
        assertThat(contar("faturas", usuario)).isZero();

        // A aberta passa a ser a dos dias novos: o fechamento muda em qualquer dia do mês (de 3 para 20).
        CalendarioDoCartao corrigido = new CalendarioDoCartao(20, 27);
        YearMonth aberta = corrigido.faturaDe(hoje, Map.of());
        LocalDate fechamento = corrigido.padrao(aberta).fechamento();
        assertThat(fechamento).isNotEqualTo(ANTIGO.padrao(ANTIGO.faturaDe(hoje, Map.of())).fechamento());
        assertThat(api.get(usuario, "/api/cartoes/{id}", horizonte)).bodyJson().extractingPath("faturaAberta.fechamento")
                .isEqualTo(fechamento.toString());

        long deNovo = novaCompra(usuario, horizonte, hoje.toString(), "Loja", "300", 3);
        assertThat(parcelas(deNovo)).containsExactly("1 " + corrigido.padrao(aberta).vencimento() + " -100.00 " + aberta,
                "2 " + corrigido.padrao(aberta.plusMonths(1)).vencimento() + " -100.00 " + aberta.plusMonths(1),
                "3 " + corrigido.padrao(aberta.plusMonths(2)).vencimento() + " -100.00 " + aberta.plusMonths(2));
    }

    /** Com um ajuste de fechamento, mesmo sem nenhum lançamento, o cartão não está vazio: vale a decisão 7. */
    @Test
    void comUmAjusteDeFechamentoAExcecaoNaoVale() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long conta = novaConta(usuario, "Banco Horizonte");
        long horizonte = novoCartao(usuario, "Cartão Horizonte", 3, 10, conta);
        LocalDate hoje = LocalDate.now(relogioDeTeste);
        long porEngano = novaCompra(usuario, horizonte, hoje.toString(), "Por engano", "300", 3);
        assertThat(api.delete(usuario, "/api/compras/{id}", porEngano)).hasStatus(204);
        YearMonth aberta = ANTIGO.faturaDe(hoje, Map.of());
        LocalDate ajustado = ANTIGO.padrao(aberta).fechamento().plusDays(1);
        assertThat(api.put(usuario, "/api/cartoes/{id}/faturas/{mes}/fechamento", "{\"data\":\"" + ajustado + "\"}", horizonte,
                aberta.toString())).hasStatus(200);

        MvcTestResult previa = api.post(usuario, "/api/cartoes/{id}/previa-dos-dias", "{\"diaFechamento\":20,\"diaVencimento\":27}", horizonte);
        assertThat(previa).hasStatus(200).bodyJson().extractingPath("valemJa").isEqualTo(false);
        assertThat(previa).bodyJson().extractingPath("troca.abertaFechamento").isEqualTo(ajustado.toString());
        assertThat(api.put(usuario, "/api/cartoes/{id}", cartao("Cartão Horizonte", "5000", 20, 27, conta, "null"), horizonte)).hasStatus(200);
        assertThat(faturas(horizonte)).contains(aberta + " " + ajustado + " " + ANTIGO.padrao(aberta).vencimento() + " true");
        assertThat(api.get(usuario, "/api/cartoes/{id}", horizonte)).bodyJson().extractingPath("faturaAberta.fechamento")
                .isEqualTo(ajustado.toString());
    }

    private Map<YearMonth, Datas> gravadas(long cartaoId) {
        Map<YearMonth, Datas> gravadas = new HashMap<>();
        jdbc.query("select mes_referencia, data_fechamento, data_vencimento from faturas where cartao_id = ?", linha -> {
            gravadas.put(YearMonth.from(linha.getDate(1).toLocalDate()),
                    new Datas(linha.getDate(2).toLocalDate(), linha.getDate(3).toLocalDate()));
        }, cartaoId);
        return gravadas;
    }

    private static Map<YearMonth, Datas> semEla(Map<YearMonth, Datas> gravadas, YearMonth mes) {
        Map<YearMonth, Datas> outras = new HashMap<>(gravadas);
        outras.keySet().removeIf(outro -> !outro.isBefore(mes));
        return outras;
    }
}
