package com.flux.importacao.interno;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fatia 05c: desfazer a importação de fatura apaga as compras, os estornos e as parcelas dela (as futuras inclusive) e
 * libera as chaves; é recusado com o cartão arquivado e com uma parcela numa fatura paga (a trava da 05b). O cartão com
 * importação de fatura, mesmo desfeita, não é vazio na troca de dias (pedido do dono).
 */
class DesfazerImportacaoDeFaturaIT extends FaturaBase {

    private byte[] fatura() {
        return csvDoNubank(linha(dia(m().minusMonths(1), 9), "Padaria Pao Quente", "22.50"),
                linha(dia(m().minusMonths(4), 20), "Curso Online Ficticio - Parcela 3/10", "120.00"),
                linha(dia(m().minusMonths(1), 12), "Estorno de Loja Ficticia", "-15.00"));
    }

    @Test
    void desfazerApagaAsComprasEAsParcelasEDeixaImportarDeNovo() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novoCartao(usuario, "Cartão Horizonte", 3, 10, novaConta(usuario, "Banco Horizonte"));
        perfilDoNubank(usuario, horizonte);
        long importacao = importarNoCartao(usuario, horizonte, "fatura.csv", fatura(), 2, 3, 4);
        assertThat(contar("compras", usuario)).isEqualTo(3);
        assertThat(contar("lancamentos", usuario)).isEqualTo(10);

        MvcTestResult desfeita = api.acao(usuario, "/api/importacoes/{id}/desfazer", importacao);

        assertThat(desfeita).hasStatusOk().bodyJson().extractingPath("desfeitaEm").isNotNull();
        assertThat(desfeita).bodyJson().extractingPath("cartaoNome").isEqualTo("Cartão Horizonte");
        assertThat(desfeita).bodyJson().extractingPath("faturaMes").isEqualTo(m().toString());
        assertThat(contar("compras", usuario)).isZero();
        assertThat(contar("lancamentos", usuario)).isZero();
        // A fatura escolhida fica: o registro aponta para ela.
        assertThat(jdbc.queryForObject("select count(*) from importacoes i join faturas f on f.id = i.fatura_id where i.id = ?",
                Integer.class, importacao)).isEqualTo(1);
        JsonNode denovo = api.json(previaNoCartao(usuario, horizonte, null, "fatura.csv", fatura()));
        assertThat(denovo.get("linhas").valueStream().map(linha -> linha.get("situacao").asString()).toList())
                .containsExactly("NOVA", "NOVA", "NOVA");
        assertThat(denovo.get("arquivoRepetido").isNull()).isTrue();
        assertThat(api.acao(usuario, "/api/importacoes/{id}/desfazer", importacao)).hasStatus(422).bodyJson().extractingPath("codigo")
                .isEqualTo("IMPORTACAO_DESFEITA");
    }

    @Test
    void desfazerERecusadoComUmaParcelaNumaFaturaPagaOuComOCartaoArquivado() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novoCartao(usuario, "Cartão Horizonte", 3, 10, novaConta(usuario, "Banco Horizonte"));
        perfilDoNubank(usuario, horizonte);
        long importacao = importarNoCartao(usuario, horizonte, "fatura.csv", fatura(), 2, 3);
        String paga = "/api/cartoes/" + horizonte + "/faturas/" + m() + "/paga-fora";
        assertThat(api.put(usuario, paga, "")).hasStatusOk();

        MvcTestResult comAPaga = api.acao(usuario, "/api/importacoes/{id}/desfazer", importacao);

        assertThat(comAPaga).hasStatus(422).bodyJson().extractingPath("codigo").isEqualTo("FATURA_PAGA");
        assertThat(comAPaga).bodyJson().extractingPath("detail").isEqualTo("Esta importação tem lançamento na fatura de "
                + nomeDoMes(m()) + ", que já foi paga. Para desfazê-la, exclua o pagamento dessa fatura ou desmarque a fatura paga fora do Flux.");
        assertThat(contar("lancamentos", usuario)).isEqualTo(9);

        assertThat(api.delete(usuario, paga)).hasStatusOk();
        api.acao(usuario, "/api/cartoes/{id}/arquivar", horizonte);
        MvcTestResult arquivado = api.acao(usuario, "/api/importacoes/{id}/desfazer", importacao);
        assertThat(arquivado).hasStatus(422).bodyJson().extractingPath("codigo").isEqualTo("CARTAO_ARQUIVADO");
        assertThat(arquivado).bodyJson().extractingPath("detail")
                .isEqualTo("Cartão Horizonte está arquivado. Reative o cartão para desfazer esta importação.");
        assertThat(api.json(api.get(usuario, "/api/importacoes")).get(0).get("cartaoArquivado").asBoolean()).isTrue();

        api.acao(usuario, "/api/cartoes/{id}/reativar", horizonte);
        assertThat(api.acao(usuario, "/api/importacoes/{id}/desfazer", importacao)).hasStatusOk();
        assertThat(contar("lancamentos", usuario)).isZero();
    }

    @Test
    void oCartaoComImportacaoDeFaturaNaoEVazioNaTrocaDeDias() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long conta = novaConta(usuario, "Banco Horizonte");
        long horizonte = novoCartao(usuario, "Cartão Horizonte", 3, 10, conta);
        perfilDoNubank(usuario, horizonte);
        long importacao = importarNoCartao(usuario, horizonte, "fatura.csv", fatura(), 2);
        assertThat(api.acao(usuario, "/api/importacoes/{id}/desfazer", importacao)).hasStatusOk();
        assertThat(contar("lancamentos", usuario)).isZero();

        // Sem lançamento nem ajuste, mas com a importação (desfeita): a troca segue a regra normal, e não "valem já".
        MvcTestResult previa = api.post(usuario, "/api/cartoes/{id}/previa-dos-dias", "{\"diaFechamento\":25,\"diaVencimento\":5}",
                horizonte);
        assertThat(previa).hasStatusOk().bodyJson().extractingPath("valemJa").isEqualTo(false);
        assertThat(previa).bodyJson().extractingPath("troca").isNotNull();
        assertThat(api.put(usuario, "/api/cartoes/{id}", cartao("Cartão Horizonte", "5000", 25, 5, conta, "null"), horizonte)).hasStatusOk();
        assertThat(jdbc.queryForObject("select to_char(f.mes_referencia, 'YYYY-MM') from importacoes i join faturas f on f.id = i.fatura_id "
                + "where i.id = ?", String.class, importacao)).isEqualTo(m().toString());
    }

    /** "setembro" no ano de hoje; "setembro de 2025" em outro ano, como a mensagem da API. */
    private String nomeDoMes(YearMonth mes) {
        String nome = mes.atDay(1).format(DateTimeFormatter.ofPattern("MMMM", Locale.forLanguageTag("pt-BR")));
        return mes.getYear() == hoje().getYear() ? nome : nome + " de " + mes.getYear();
    }
}
