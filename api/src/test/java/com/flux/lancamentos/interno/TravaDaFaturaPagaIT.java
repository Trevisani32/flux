package com.flux.lancamentos.interno;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static com.flux.suporte.ApiDeTeste.texto;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Decisões 7, 12 e 35 da Fatia 05 (ADR 0017 e ADR 0018): a compra e o estorno com parcela numa fatura paga só mudam os
 * textos e não se excluem; a compra nova numa fatura que continua paga com ela entra travada, e a prévia avisa antes,
 * também quando o lançamento deixa a fatura paga (o estorno, a edição que diminui o valor); a edição não leva a compra
 * livre para uma fatura paga; a
 * fatura paga não se ajusta, e o ajuste não move compra de uma fatura paga; o cartão com pagamento ou com fatura paga fora
 * do Flux não é vazio na troca de dias, e a troca não mexe nas faturas pagas.
 */
class TravaDaFaturaPagaIT extends PagamentosBase {

    @Test
    void aCompraComParcelaNaFaturaPagaSoMudaOsTextosENaoSeExclui() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long conta = novaConta(usuario, "Banco Horizonte");
        long cartao = cartaoQueFecha5(usuario, "Cartão Horizonte", conta);
        long compras = idDaCategoria(usuario, "Compras");
        long sapatos = api.idCriado(api.post(usuario, "/api/compras", compra(cartao, diaUmAtras(3), "Sapatos", "450", 3, 1, null)));
        long devolucao = api.idCriado(api.post(usuario, "/api/estornos", estorno(cartao, diaUmAtras(2), "Estorno Loja", "50", null)));
        String paga = mesAtras(2);
        pagarHoje(usuario, cartao, paga, conta, "100");
        String nome = FaturaService.nomeDoMes(YearMonth.parse(paga), hoje());

        String trava = "A parcela 2/3 está na fatura de " + nome + ", que já foi paga: só a descrição, a categoria e a observação mudam.";
        assertThat(api.get(usuario, "/api/compras/{id}", sapatos)).bodyJson().extractingPath("travaDaFaturaPaga").isEqualTo(trava);
        MvcTestResult regravar = api.put(usuario, "/api/compras/{id}", compra(cartao, diaUmAtras(3), "Sapatos", "600", 3, 1, null), sapatos);
        assertThat(regravar).hasStatus(422).bodyJson().extractingPath("detail").isEqualTo(trava);
        assertThat(regravar).bodyJson().extractingPath("codigo").isEqualTo("FATURA_PAGA");
        assertThat(api.delete(usuario, "/api/compras/{id}", sapatos)).hasStatus(422).bodyJson().extractingPath("detail").isEqualTo(trava);
        assertThat(api.put(usuario, "/api/compras/{id}", compra(cartao, diaUmAtras(3), "Sapatos de corrida", "450", 3, 1, compras), sapatos))
                .hasStatus(200).bodyJson().extractingPath("descricao").isEqualTo("Sapatos de corrida");
        assertThat(parcelas(sapatos)).hasSize(3);

        String doEstorno = "O estorno está na fatura de " + nome + ", que já foi paga: só a descrição, a categoria e a observação mudam.";
        assertThat(api.delete(usuario, "/api/estornos/{id}", devolucao)).hasStatus(422).bodyJson().extractingPath("detail").isEqualTo(doEstorno);

        // Lançar compra nova numa fatura paga pode: quando o total novo passa do pago, ela volta a ter pendente, e a trava sai.
        novaCompra(usuario, cartao, diaUmAtras(2), "Esquecida", "30", 1);
        assertThat(fatura(usuario, cartao, paga)).bodyJson().extractingPath("situacao").isEqualTo("FECHADA");
        assertThat(api.get(usuario, "/api/compras/{id}", sapatos)).bodyJson().extractingPath("travaDaFaturaPaga").isNull();
        assertThat(api.delete(usuario, "/api/estornos/{id}", devolucao)).hasStatus(204);
    }

    @Test
    void aCompraNovaNumaFaturaQueContinuaPagaEntraTravadaEAPreviaAvisaAntes() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long conta = novaConta(usuario, "Banco Horizonte");
        long cartao = cartaoQueFecha5(usuario, "Cartão Horizonte", conta);

        // Paga fora do Flux: a marca vale para a fatura inteira, e a compra nova entra paga e travada.
        String pagaFora = mesAtras(3);
        String nomeDaPagaFora = FaturaService.nomeDoMes(YearMonth.parse(pagaFora), hoje());
        novaCompra(usuario, cartao, diaUmAtras(3), "Mercado", "100", 1);
        assertThat(api.put(usuario, "/api/cartoes/{id}/faturas/{mes}/paga-fora", "", cartao, pagaFora)).hasStatus(200);
        assertThat(api.post(usuario, "/api/compras/previa", compra(cartao, diaUmAtras(3), "x", "40", 1, 1, null))).hasStatus(200)
                .bodyJson().extractingPath("avisoDaFaturaPaga").isEqualTo("A compra vai para a fatura de " + nomeDaPagaFora
                        + ", que já foi paga fora do Flux: ela entra travada, e só a descrição, a categoria e a observação vão mudar. "
                        + "Para corrigir um engano, desmarque a fatura.");
        long esquecida = novaCompra(usuario, cartao, diaUmAtras(3), "Esquecida", "40", 1);
        assertThat(texto(fatura(usuario, cartao, pagaFora))).contains("\"situacao\":\"PAGA\"").contains("\"total\":140.00")
                .contains("\"pendente\":0.00");
        String trava = "A compra está na fatura de " + nomeDaPagaFora + ", que já foi paga: só a descrição, a categoria e a observação mudam.";
        assertThat(api.get(usuario, "/api/compras/{id}", esquecida)).bodyJson().extractingPath("travaDaFaturaPaga").isEqualTo(trava);
        assertThat(api.delete(usuario, "/api/compras/{id}", esquecida)).hasStatus(422);
        // Para corrigir o engano, desmarcar: a fatura volta a ter o pendente, e a compra sai.
        assertThat(api.delete(usuario, "/api/cartoes/{id}/faturas/{mes}/paga-fora", cartao, pagaFora)).hasStatus(200);
        assertThat(api.delete(usuario, "/api/compras/{id}", esquecida)).hasStatus(204);

        // Com pagamento: o pago a mais que cobre a compra nova deixa a fatura paga, e a compra entra travada.
        String paga = mesAtras(2);
        String nomeDaPaga = FaturaService.nomeDoMes(YearMonth.parse(paga), hoje());
        novaCompra(usuario, cartao, diaUmAtras(2), "Livraria", "100", 1);
        pagarHoje(usuario, cartao, paga, conta, "150");
        String comPagamento = "A compra vai para a fatura de " + nomeDaPaga + ", que já foi paga: ela entra travada, e só a descrição, a "
                + "categoria e a observação vão mudar. Para corrigir um engano, exclua ou edite o pagamento dela.";
        assertThat(api.post(usuario, "/api/compras/previa", compra(cartao, diaUmAtras(2), "x", "30", 1, 1, null)))
                .bodyJson().extractingPath("avisoDaFaturaPaga").isEqualTo(comPagamento);
        assertThat(api.post(usuario, "/api/compras/previa", "{\"cartaoId\":" + cartao + ",\"data\":\"" + diaUmAtras(2)
                + "\",\"valorTotal\":10,\"parcelas\":1,\"tipo\":\"ESTORNO\"}")).bodyJson().extractingPath("avisoDaFaturaPaga")
                .isEqualTo("O estorno vai para a fatura de " + nomeDaPaga + ", que já foi paga: ele entra travado, e só a descrição, a "
                        + "categoria e a observação vão mudar. Para corrigir um engano, exclua ou edite o pagamento dela.");
        long coberta = novaCompra(usuario, cartao, diaUmAtras(2), "Coberta", "30", 1);
        assertThat(texto(fatura(usuario, cartao, paga))).contains("\"situacao\":\"PAGA\"").contains("\"pendente\":0.00")
                .contains("\"pagoAMais\":20.00");
        assertThat(api.delete(usuario, "/api/compras/{id}", coberta)).hasStatus(422);

        // O pago a mais que não cobre: a prévia não avisa, a fatura volta a ter pendente, e a compra nova não trava.
        assertThat(api.post(usuario, "/api/compras/previa", compra(cartao, diaUmAtras(2), "x", "80", 1, 1, null)))
                .bodyJson().extractingPath("avisoDaFaturaPaga").isNull();
        long passa = novaCompra(usuario, cartao, diaUmAtras(2), "Passa do pago", "80", 1);
        assertThat(texto(fatura(usuario, cartao, paga))).contains("\"situacao\":\"FECHADA\"").contains("\"pendente\":60.00")
                .contains("\"pagoAMais\":0.00");
        assertThat(api.get(usuario, "/api/compras/{id}", passa)).bodyJson().extractingPath("travaDaFaturaPaga").isNull();
        assertThat(api.delete(usuario, "/api/compras/{id}", passa)).hasStatus(204);
    }

    @Test
    void aPreviaAvisaQuandoOLancamentoDeixaAFaturaPagaTambemNaEdicao() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long conta = novaConta(usuario, "Banco Horizonte");
        long cartao = cartaoQueFecha5(usuario, "Cartão Horizonte", conta);

        // O estorno que fecha a conta: R$ 100,00 de compra e R$ 90,00 pagos; o estorno de R$ 10,00 deixa a fatura paga.
        String fechada = mesAtras(2);
        String nome = FaturaService.nomeDoMes(YearMonth.parse(fechada), hoje());
        novaCompra(usuario, cartao, diaUmAtras(2), "Livraria", "100", 1);
        pagarHoje(usuario, cartao, fechada, conta, "90");
        assertThat(api.post(usuario, "/api/compras/previa", previaDoEstorno(cartao, diaUmAtras(2), "5"))).hasStatus(200)
                .bodyJson().extractingPath("avisoDaFaturaPaga").isNull();
        assertThat(api.post(usuario, "/api/compras/previa", previaDoEstorno(cartao, diaUmAtras(2), "10")))
                .bodyJson().extractingPath("avisoDaFaturaPaga").isEqualTo("O estorno vai para a fatura de " + nome + ", que fica paga com "
                        + "ele: ele entra travado, e só a descrição, a categoria e a observação vão mudar. Para corrigir um engano, exclua ou "
                        + "edite o pagamento dela.");
        long devolucao = api.idCriado(api.post(usuario, "/api/estornos", estorno(cartao, diaUmAtras(2), "Estorno Livraria", "10", null)));
        assertThat(fatura(usuario, cartao, fechada)).bodyJson().extractingPath("situacao").isEqualTo("PAGA");
        assertThat(api.delete(usuario, "/api/estornos/{id}", devolucao)).hasStatus(422);

        // A edição que diminui o valor: R$ 100,00 e R$ 80,00 de compras e R$ 150,00 pagos; a de R$ 80,00 por R$ 40,00 deixa a
        // fatura paga. Com o compraId, a prévia desconta as parcelas atuais.
        String outra = mesAtras(3);
        novaCompra(usuario, cartao, diaUmAtras(3), "Mercado", "100", 1);
        long padaria = novaCompra(usuario, cartao, diaUmAtras(3), "Padaria", "80", 1);
        pagarHoje(usuario, cartao, outra, conta, "150");
        String quarenta = compra(cartao, diaUmAtras(3), "Padaria", "40", 1, 1, null);
        assertThat(api.post(usuario, "/api/compras/previa", comCompraId(quarenta, padaria))).bodyJson().extractingPath("avisoDaFaturaPaga")
                .isEqualTo("A compra vai ficar na fatura de " + FaturaService.nomeDoMes(YearMonth.parse(outra), hoje()) + ", que fica paga "
                        + "com ela: ela fica travada, e só a descrição, a categoria e a observação vão mudar. Para corrigir um engano, exclua "
                        + "ou edite o pagamento dela.");
        assertThat(api.post(usuario, "/api/compras/previa", quarenta)).bodyJson().extractingPath("avisoDaFaturaPaga").isNull();
        assertThat(api.post(usuario, "/api/compras/previa", comCompraId(compra(cartao, diaUmAtras(3), "Padaria", "60", 1, 1, null), padaria)))
                .bodyJson().extractingPath("avisoDaFaturaPaga").isNull();
        assertThat(api.put(usuario, "/api/compras/{id}", quarenta, padaria)).hasStatus(200);
        assertThat(fatura(usuario, cartao, outra)).bodyJson().extractingPath("situacao").isEqualTo("PAGA");
        assertThat(api.delete(usuario, "/api/compras/{id}", padaria)).hasStatus(422);

        // A edição que levaria a compra livre para a fatura paga: a prévia não avisa, e o PUT recusa com a frase da edição.
        long livre = novaCompra(usuario, cartao, diaUmAtras(1), "Livre", "20", 1);
        String paraAPaga = compra(cartao, diaUmAtras(2), "Livre", "20", 1, 1, null);
        assertThat(api.post(usuario, "/api/compras/previa", comCompraId(paraAPaga, livre))).bodyJson().extractingPath("avisoDaFaturaPaga")
                .isNull();
        assertThat(api.put(usuario, "/api/compras/{id}", paraAPaga, livre)).hasStatus(422).bodyJson().extractingPath("detail")
                .isEqualTo("A edição levaria a compra para a fatura de " + nome + ", que já foi paga. Para lançá-la nessa fatura, exclua esta "
                        + "compra e lance de novo.");

        // A compra de outro usuário no compraId é a inexistente.
        UsuarioDeTeste outro = usuariosDeTeste.criar();
        long cartaoDoOutro = cartaoQueFecha5(outro, "Cartão Lua", novaConta(outro, "Banco Lua"));
        assertThat(api.post(outro, "/api/compras/previa", comCompraId(compra(cartaoDoOutro, diaUmAtras(1), "x", "20", 1, 1, null), livre)))
                .hasStatus(422).bodyJson().extractingPath("detail").isEqualTo("Compra não encontrada.");
    }

    /** Corpo de POST /api/compras/previa de um estorno. */
    private static String previaDoEstorno(long cartaoId, String data, String valor) {
        return "{\"cartaoId\":" + cartaoId + ",\"data\":\"" + data + "\",\"valorTotal\":" + valor + ",\"parcelas\":1,\"tipo\":\"ESTORNO\"}";
    }

    /** O corpo da compra com o {@code compraId} da edição, para a prévia. */
    private static String comCompraId(String corpo, long compraId) {
        return corpo.substring(0, corpo.length() - 1) + ",\"compraId\":" + compraId + "}";
    }

    @Test
    void aEdicaoNaoLevaACompraNemOEstornoLivreParaUmaFaturaPaga() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long conta = novaConta(usuario, "Banco Horizonte");
        long horizonte = cartaoQueFecha5(usuario, "Cartão Horizonte", conta);
        long lua = cartaoQueFecha5(usuario, "Cartão Lua", conta);
        // A fatura de três meses atrás do Horizonte está paga; a do mês passado do Lua, marcada como paga fora do Flux.
        String paga = mesAtras(3);
        String nomeDaPaga = FaturaService.nomeDoMes(YearMonth.parse(paga), hoje());
        long mercado = novaCompra(usuario, horizonte, diaUmAtras(3), "Mercado", "100", 1);
        pagarHoje(usuario, horizonte, paga, conta, "100");
        assertThat(api.put(usuario, "/api/cartoes/{id}/faturas/{mes}/paga-fora", "", lua, mesAtras(1))).hasStatus(200);
        long livraria = novaCompra(usuario, horizonte, diaUmAtras(1), "Livraria", "50", 1);
        long notebook = api.idCriado(api.post(usuario, "/api/compras", compra(horizonte, diaUmAtras(3), "Notebook", "300", 3, 2, null)));
        long devolucao = api.idCriado(api.post(usuario, "/api/estornos", estorno(horizonte, diaUmAtras(1), "Estorno Loja", "10", null)));
        List<String> antes = parcelas(livraria);

        // A data: a compra iria para a fatura paga.
        MvcTestResult pelaData = api.put(usuario, "/api/compras/{id}", compra(horizonte, diaUmAtras(3), "Livraria", "50", 1, 1, null), livraria);
        assertThat(pelaData).hasStatus(422).bodyJson().extractingPath("detail").isEqualTo("A edição levaria a compra para a fatura de "
                + nomeDaPaga + ", que já foi paga. Para lançá-la nessa fatura, exclua esta compra e lance de novo.");
        assertThat(pelaData).bodyJson().extractingPath("codigo").isEqualTo("FATURA_PAGA");
        assertThat(parcelas(livraria)).isEqualTo(antes);
        // O cartão: a mesma data no Lua cai na fatura marcada como paga fora do Flux.
        assertThat(api.put(usuario, "/api/compras/{id}", compra(lua, diaUmAtras(1), "Livraria", "50", 1, 1, null), livraria)).hasStatus(422)
                .bodyJson().extractingPath("detail").isEqualTo("A edição levaria a compra para a fatura de "
                        + FaturaService.nomeDoMes(YearMonth.parse(mesAtras(1)), hoje()) + ", que já foi paga. Para lançá-la nessa fatura, "
                        + "exclua esta compra e lance de novo.");
        assertThat(parcelas(livraria)).isEqualTo(antes);
        // A primeira parcela: a 1/3, de antes do Flux, iria para a fatura paga.
        List<String> doNotebook = parcelas(notebook);
        assertThat(api.put(usuario, "/api/compras/{id}", compra(horizonte, diaUmAtras(3), "Notebook", "300", 3, 1, null), notebook))
                .hasStatus(422).bodyJson().extractingPath("detail").isEqualTo("A edição levaria a parcela 1/3 para a fatura de " + nomeDaPaga
                        + ", que já foi paga. Para lançar a compra nessa fatura, exclua esta e lance de novo.");
        assertThat(parcelas(notebook)).isEqualTo(doNotebook);
        // O estorno também.
        assertThat(api.put(usuario, "/api/estornos/{id}", estorno(horizonte, diaUmAtras(3), "Estorno Loja", "10", null), devolucao))
                .hasStatus(422).bodyJson().extractingPath("detail").isEqualTo("A edição levaria o estorno para a fatura de " + nomeDaPaga
                        + ", que já foi paga. Para lançá-lo nessa fatura, exclua este estorno e lance de novo.");

        // A edição para uma fatura que não está paga continua valendo; a compra que já está numa paga tem a trava de sempre.
        assertThat(api.put(usuario, "/api/compras/{id}", compra(horizonte, diaUmAtras(2), "Livraria", "50", 1, 1, null), livraria))
                .hasStatus(200);
        assertThat(api.put(usuario, "/api/compras/{id}", compra(horizonte, diaUmAtras(2), "Mercado", "100", 1, 1, null), mercado))
                .hasStatus(422).bodyJson().extractingPath("detail").isEqualTo("A compra está na fatura de " + nomeDaPaga
                        + ", que já foi paga: só a descrição, a categoria e a observação mudam.");
    }

    @Test
    void aFaturaPagaNaoSeAjustaEOAjusteNaoMoveCompraDeUmaPaga() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long conta = novaConta(usuario, "Banco Horizonte");
        long cartao = cartaoQueFecha5(usuario, "Cartão Horizonte", conta);
        YearMonth anterior = YearMonth.from(hoje()).minusMonths(3);
        YearMonth paga = anterior.plusMonths(1);
        // A compra do dia 4 do mês anterior à paga está nela pela 1ª parcela, e a 2ª parcela está na paga.
        novaCompra(usuario, cartao, anterior.atDay(4).toString(), "Sapataria Passo Firme", "200", 2);
        novaCompra(usuario, cartao, paga.atDay(1).toString(), "Livraria", "50", 1);
        pagarHoje(usuario, cartao, paga.toString(), conta, "150");
        assertThat(fatura(usuario, cartao, paga.toString())).bodyJson().extractingPath("situacao").isEqualTo("PAGA");

        MvcTestResult daPaga = api.put(usuario, "/api/cartoes/{id}/faturas/{mes}/fechamento", "{\"data\":\"" + paga.atDay(6) + "\"}", cartao,
                paga.toString());
        assertThat(daPaga).hasStatus(422).bodyJson().extractingPath("detail")
                .isEqualTo("A fatura de " + FaturaService.nomeDoMes(paga, hoje()) + " já foi paga: o fechamento não muda.");
        assertThat(api.delete(usuario, "/api/cartoes/{id}/faturas/{mes}/fechamento", cartao, paga.toString())).hasStatus(422);

        // Fechar a anterior no dia 3 levaria a Sapataria (do dia 4) para a seguinte, que está paga: recusado, nada muda.
        MvcTestResult moveria = api.put(usuario, "/api/cartoes/{id}/faturas/{mes}/fechamento", "{\"data\":\"" + anterior.atDay(3) + "\"}",
                cartao, anterior.toString());
        assertThat(moveria).hasStatus(422).bodyJson().extractingPath("detail").isEqualTo("O ajuste moveria Sapataria Passo Firme, que tem "
                + "parcela na fatura de " + FaturaService.nomeDoMes(paga, hoje()) + ", já paga.");
        assertThat(moveria).bodyJson().extractingPath("codigo").isEqualTo("FATURA_PAGA");
        assertThat(faturas(cartao).getFirst()).isEqualTo(anterior + " " + anterior.atDay(5) + " " + anterior.atDay(15) + " false");

        // O ajuste que não move nada de uma paga continua valendo.
        assertThat(api.put(usuario, "/api/cartoes/{id}/faturas/{mes}/fechamento", "{\"data\":\"" + anterior.atDay(8) + "\"}", cartao,
                anterior.toString())).hasStatus(200).bodyJson().extractingPath("comprasMovidas").isEqualTo(0);
    }

    @Test
    void oCartaoComPagamentoOuPagaForaNaoEVazioNaTrocaDeDias() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long conta = novaConta(usuario, "Banco Horizonte");
        long comPagamento = cartaoQueFecha5(usuario, "Cartão Horizonte", conta);
        long comMarca = cartaoQueFecha5(usuario, "Cartão Lua", conta);
        String dias = "{\"diaFechamento\":20,\"diaVencimento\":28}";

        // Sem nada, os dias novos valem já (decisão 7, a exceção do cartão vazio).
        assertThat(api.post(usuario, "/api/cartoes/{id}/previa-dos-dias", dias, comPagamento)).bodyJson().extractingPath("valemJa").isEqualTo(true);

        // O pagamento de uma fatura vazia (pago a mais) deixa o cartão com pagamento: a troca segue a regra normal.
        long pagamento = pagarHoje(usuario, comPagamento, mesAtras(2), conta, "10");
        MvcTestResult previa = api.post(usuario, "/api/cartoes/{id}/previa-dos-dias", dias, comPagamento);
        assertThat(previa).bodyJson().extractingPath("valemJa").isEqualTo(false);
        assertThat(previa).bodyJson().extractingPath("troca").isNotNull();
        // Excluído o pagamento, o cartão volta a ser vazio.
        assertThat(api.delete(usuario, "/api/pagamentos/{id}", pagamento)).hasStatus(204);
        assertThat(api.post(usuario, "/api/cartoes/{id}/previa-dos-dias", dias, comPagamento)).bodyJson().extractingPath("valemJa").isEqualTo(true);

        // A marca de paga fora do Flux também: a troca não apaga a fatura marcada.
        assertThat(api.put(usuario, "/api/cartoes/{id}/faturas/{mes}/paga-fora", "", comMarca, mesAtras(2))).hasStatus(200);
        assertThat(api.post(usuario, "/api/cartoes/{id}/previa-dos-dias", dias, comMarca)).bodyJson().extractingPath("valemJa").isEqualTo(false);
        assertThat(api.put(usuario, "/api/cartoes/{id}", cartao("Cartão Lua", "1000", 20, 28, conta, "null"), comMarca)).hasStatus(200);
        assertThat(jdbc.queryForObject("select count(*) from faturas where cartao_id = ? and paga_fora_em is not null", Integer.class, comMarca))
                .isEqualTo(1);
        assertThat(api.delete(usuario, "/api/cartoes/{id}/faturas/{mes}/paga-fora", comMarca, mesAtras(2))).hasStatus(200);
    }

    @Test
    void aTrocaDeDiasNaoMexeNasFaturasPagasNemNosPagamentos() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long conta = novaConta(usuario, "Banco Horizonte");
        long cartao = cartaoQueFecha5(usuario, "Cartão Horizonte", conta);
        long livraria = novaCompra(usuario, cartao, diaUmAtras(2), "Livraria", "120", 3);
        pagarHoje(usuario, cartao, mesAtras(2), conta, "40");
        YearMonth paga = YearMonth.parse(mesAtras(2));
        LocalDate fechamento = paga.atDay(5);

        assertThat(api.put(usuario, "/api/cartoes/{id}", cartao("Cartão Horizonte", "1000", 20, 28, conta, "null"), cartao)).hasStatus(200);

        MvcTestResult depois = fatura(usuario, cartao, paga.toString());
        assertThat(depois).bodyJson().extractingPath("situacao").isEqualTo("PAGA");
        assertThat(depois).bodyJson().extractingPath("fechamento").isEqualTo(fechamento.toString());
        assertThat(texto(depois)).contains("\"pago\":40.00").contains("\"pendente\":0.00");
        assertThat(parcelas(livraria).getFirst()).isEqualTo("1 " + paga.atDay(15) + " -40.00 " + paga);
    }
}
