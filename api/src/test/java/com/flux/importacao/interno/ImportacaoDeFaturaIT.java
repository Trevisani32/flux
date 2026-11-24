package com.flux.importacao.interno;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;

import static com.flux.suporte.ApiDeTeste.assertCampo;
import static com.flux.suporte.ApiDeTeste.texto;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fatia 05c (decisões 39 a 43): a fatura do cartão pela API. O CSV do Nubank pelo perfil do cartão, com o modelo pronto; a
 * fatura sugerida pela compra mais recente que não é parcela, com a anterior e a seguinte; todas as linhas na fatura
 * escolhida; "Parcela 3/10" cria a compra com as parcelas 3 a 10; o pagamento e a linha de total ficam de fora; reimportar
 * e o mês seguinte reconhecem as linhas.
 */
class ImportacaoDeFaturaIT extends FaturaBase {

    /**
     * A fatura de M: a parcela 3/10 (de uma compra de 4 meses antes), duas compras à vista, o estorno, o pagamento e o total.
     * O pagamento é a linha mais recente, e não sugere a fatura.
     */
    private byte[] faturaDeM() {
        YearMonth antes = m().minusMonths(1);
        return csvDoNubank(
                linha(dia(m().minusMonths(4), 20), "Curso Online Ficticio - Parcela 3/10", "120.00"),
                linha(dia(antes, 14), "Padaria Pao Quente", "22.50"),
                linha(dia(antes, 20), "IOF compra internacional", "4.37"),
                linha(dia(antes, 21), "Estorno de Loja Ficticia", "-15.00"),
                linha(dia(antes, 25), "Pagamento recebido", "-265.20"),
                linha(dia(antes, 22), "Total da fatura", "400.00"));
    }

    @Test
    void oCsvDoNubankPedePerfilComOModeloProntoESugereAFaturaPelaCompraMaisRecente() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novoCartao(usuario, "Cartão Horizonte", 3, 10, novaConta(usuario, "Banco Horizonte"));
        byte[] fatura = faturaDeM();

        assertThat(previaNoCartao(usuario, horizonte, null, "fatura.csv", fatura)).hasStatus(422).bodyJson().extractingPath("detail")
                .isEqualTo("Este cartão ainda não tem perfil de CSV.");
        MvcTestResult amostra = mvc.post().uri("/api/importacoes/amostra").header(HttpHeaders.AUTHORIZATION, "Bearer " + usuario.tokenAcesso())
                .multipart().file(new MockMultipartFile("arquivo", "fatura.csv", "text/csv", fatura))
                .param("cartao", "true").exchange();
        assertThat(amostra).hasStatusOk().bodyJson().extractingPath("modelo").isEqualTo("NUBANK_FATURA");
        assertThat(amostra).bodyJson().extractingPath("formato.comprasPositivas").isEqualTo(true);
        // A compra vem positiva no arquivo e sai negativa, como no Flux.
        assertThat(amostra).bodyJson().extractingPath("primeiraLinha.valor").isEqualTo(-120.0);
        perfilDoNubank(usuario, horizonte);
        assertThat(api.get(usuario, "/api/importacoes/perfis/cartao/{id}", horizonte)).bodyJson().extractingPath("comprasPositivas")
                .isEqualTo(true);

        MvcTestResult previa = previaNoCartao(usuario, horizonte, null, "fatura.csv", fatura);

        assertThat(previa).hasStatusOk();
        JsonNode corpo = api.json(previa);
        assertThat(corpo.get("cartaoNome").asString()).isEqualTo("Cartão Horizonte");
        assertThat(corpo.get("contaId").isNull()).isTrue();
        assertThat(corpo.get("fatura").get("mes").asString()).isEqualTo(m().toString());
        assertThat(corpo.get("fatura").get("fechamento").asString()).isEqualTo(dia(m(), 3));
        assertThat(corpo.get("fatura").get("vencimento").asString()).isEqualTo(dia(m(), 10));
        assertThat(corpo.get("faturas").valueStream().map(opcao -> opcao.get("mes").asString() + ":" + opcao.get("situacao").asString())
                .toList()).containsExactly(m().minusMonths(1) + ":FECHADA", m() + ":FECHADA", m().plusMonths(1) + ":FECHADA");
        assertThat(corpo.get("dataDaSugestao").asString()).isEqualTo(dia(m().minusMonths(1), 21));
        assertThat(corpo.get("linhas").valueStream().map(linha -> linha.get("linha").asInt() + ":" + linha.get("situacao").asString() + ":"
                + linha.get("motivo").asString("") + ":" + linha.get("marcada").asBoolean()).toList())
                .containsExactly("6:FORA:PAGAMENTO_DA_FATURA:false", "7:FORA:LINHA_DE_SALDO:false", "5:NOVA::true", "4:NOVA::true", "3:NOVA::true",
                        "2:NOVA::true");
        JsonNode parcela = corpo.get("linhas").get(5);
        assertThat(parcela.get("parcela").asInt()).isEqualTo(3);
        assertThat(parcela.get("parcelas").asInt()).isEqualTo(10);
        assertThat(parcela.get("descricaoDaCompra").asString()).isEqualTo("Curso Online Ficticio");
        assertThat(parcela.get("descricao").asString()).isEqualTo("Curso Online Ficticio - Parcela 3/10");
        assertThat(texto(previa)).contains("\"valor\":-120.00").contains("\"valor\":15.00").contains("\"valor\":265.20");
        assertThat(contar("importacoes", usuario)).isZero();
        assertThat(contar("compras", usuario)).isZero();

        // Decisão 40: a fatura pode ser trocada pela anterior ou pela seguinte; outra é recusada.
        assertThat(previaNoCartao(usuario, horizonte, m().plusMonths(1).toString(), "fatura.csv", fatura)).hasStatusOk().bodyJson()
                .extractingPath("fatura.mes").isEqualTo(m().plusMonths(1).toString());
        MvcTestResult longe = previaNoCartao(usuario, horizonte, m().plusMonths(2).toString(), "fatura.csv", fatura);
        assertThat(longe).hasStatus(422).bodyJson().extractingPath("codigo").isEqualTo("FATURA_INVALIDA");
        assertThat(longe).bodyJson().extractingPath("detail").isEqualTo("Escolha uma das faturas oferecidas.");
    }

    @Test
    void semCompraNoArquivoASugestaoDaFaturaNaoTemData() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novoCartao(usuario, "Cartão Horizonte", 3, 10, novaConta(usuario, "Banco Horizonte"));
        perfilDoNubank(usuario, horizonte);
        byte[] somenteParcelas = csvDoNubank(
                linha(dia(m().minusMonths(4), 20), "Curso Online Ficticio - Parcela 3/10", "120.00"),
                linha(dia(m().minusMonths(5), 20), "Fone Ficticio - Parcela 2/3", "33.33"));

        JsonNode previa = api.json(previaNoCartao(usuario, horizonte, null, "somente-parcelas.csv", somenteParcelas));

        assertThat(previa.get("dataDaSugestao").isNull()).isTrue();
    }

    @Test
    void resultadoDaFaturaInformaAsLinhasDesmarcadas() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novoCartao(usuario, "Cartão Horizonte", 3, 10, novaConta(usuario, "Banco Horizonte"));
        perfilDoNubank(usuario, horizonte);
        byte[] fatura = faturaDeM();
        String hash = api.json(previaNoCartao(usuario, horizonte, null, "fatura.csv", fatura)).get("hash").asString();

        JsonNode resultado = api.json(confirmarNoCartao(usuario, horizonte, m().toString(), "fatura.csv", fatura, hash, escolhas(2, 3)));

        assertThat(resultado.get("importadas").asInt()).isEqualTo(2);
        assertThat(resultado.get("desmarcadas").asInt()).isEqualTo(2);
        assertThat(jdbc.queryForObject("select desmarcadas from importacoes where usuario_id = ?", Integer.class, usuario.id())).isEqualTo(2);
    }

    @Test
    void confirmarGravaTudoNaFaturaEscolhidaEAParcelaCriaAsSeguintes() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novoCartao(usuario, "Cartão Horizonte", 3, 10, novaConta(usuario, "Banco Horizonte"));
        perfilDoNubank(usuario, horizonte);
        byte[] fatura = faturaDeM();
        String hash = api.json(previaNoCartao(usuario, horizonte, null, "fatura.csv", fatura)).get("hash").asString();

        MvcTestResult resultado = confirmarNoCartao(usuario, horizonte, m().toString(), "fatura.csv", fatura, hash,
                "[{\"linha\":2,\"categoriaId\":null},{\"linha\":3,\"categoriaId\":null},{\"linha\":4,\"categoriaId\":null},"
                        + "{\"linha\":5,\"categoriaId\":null},{\"linha\":6,\"categoriaId\":null}]");

        assertThat(resultado).hasStatus(201);
        JsonNode corpo = api.json(resultado);
        assertThat(corpo.get("importadas").asInt()).isEqualTo(4);
        assertThat(corpo.get("lancamentos").asInt()).isEqualTo(11);
        assertThat(corpo.get("deFora").asInt()).isEqualTo(2);
        assertThat(corpo.get("cartaoNome").asString()).isEqualTo("Cartão Horizonte");
        assertThat(corpo.get("fatura").asString()).isEqualTo(m().toString());
        assertThat(corpo.get("saldoDeHoje").isNull()).isTrue();
        assertThat(corpo.get("parceladas").valueStream().map(parcelada -> parcelada.get("descricao").asString() + " "
                + parcelada.get("parcelaInicial").asInt() + "/" + parcelada.get("parcelas").asInt()).toList())
                .containsExactly("Curso Online Ficticio 3/10");
        // Decisão 41: a compra em 10x, com as parcelas 3 a 10, a 3ª na fatura escolhida e as seguintes nas faturas depois.
        long curso = jdbc.queryForObject("select id from compras where usuario_id = ? and parcelas = 10", Long.class, usuario.id());
        assertThat(jdbc.queryForObject("select data_compra || ' ' || valor_total || ' ' || parcela_inicial || ' ' || (importacao_id is not null) "
                + "from compras where id = ?", String.class, curso)).isEqualTo(dia(m().minusMonths(4), 20) + " 1200.00 3 true");
        List<String> parcelas = parcelas(curso);
        assertThat(parcelas).hasSize(8);
        assertThat(parcelas.getFirst()).isEqualTo("3 " + dia(m(), 10) + " -120.00 " + m());
        assertThat(parcelas.getLast()).isEqualTo("10 " + dia(m().plusMonths(7), 10) + " -120.00 " + m().plusMonths(7));
        assertThat(jdbc.queryForList("select chave_importacao from lancamentos where compra_id = ? order by numero_parcela", String.class, curso))
                .allMatch(chave -> chave.startsWith("CPAR:" + horizonte + ":")).last().asString().endsWith(":10");
        assertThat(jdbc.queryForList("select descricao || '|' || descricao_banco || '|' || tipo || '|' || valor || '|' || to_char(f.mes_referencia, "
                + "'YYYY-MM') from lancamentos l join faturas f on f.id = l.fatura_id where l.usuario_id = ? and l.numero_parcela = 1 "
                + "order by l.descricao", String.class, usuario.id()))
                .containsExactly("Estorno de Loja Ficticia|Estorno de Loja Ficticia|RECEITA|15.00|" + m(),
                        "IOF compra internacional|IOF compra internacional|DESPESA|-4.37|" + m(),
                        "Padaria Pao Quente|Padaria Pao Quente|DESPESA|-22.50|" + m());
        assertThat(jdbc.queryForObject("select descricao || '|' || descricao_banco from lancamentos where compra_id = ? and numero_parcela = 3",
                String.class, curso)).isEqualTo("Curso Online Ficticio|Curso Online Ficticio");
        assertThat(jdbc.queryForObject("select count(*) from importacoes where usuario_id = ? and cartao_id = ? and conta_id is null "
                + "and lidas = 6 and importadas = 4 and fora = 2", Integer.class, usuario.id(), horizonte)).isEqualTo(1);

        // O histórico diz o cartão e a fatura.
        JsonNode item = api.json(api.get(usuario, "/api/importacoes")).get(0);
        assertThat(item.get("cartaoNome").asString()).isEqualTo("Cartão Horizonte");
        assertThat(item.get("faturaMes").asString()).isEqualTo(m().toString());
        assertThat(item.get("contaNome").isNull()).isTrue();
        assertThat(item.get("importadas").asInt()).isEqualTo(4);
        assertThat(item.get("excluidos").asInt()).isZero();

        // Reimportar o mesmo arquivo: tudo "já importada", menos o que fica de fora, e o aviso do arquivo repetido.
        JsonNode denovo = api.json(previaNoCartao(usuario, horizonte, null, "fatura.csv", fatura));
        assertThat(denovo.get("linhas").valueStream().map(linha -> linha.get("situacao").asString()).toList())
                .containsExactly("FORA", "FORA", "JA_IMPORTADA", "JA_IMPORTADA", "JA_IMPORTADA", "JA_IMPORTADA");
        assertThat(denovo.get("arquivoRepetido").get("contaNome").asString()).isEqualTo("Cartão Horizonte");
        assertThat(denovo.get("arquivoRepetido").get("mesmaConta").asBoolean()).isTrue();
        assertThat(denovo.get("origens").get(0).get("nomeArquivo").asString()).isEqualTo("fatura.csv");

        // O mês seguinte: a "Parcela 4/10" nasceu com a chave na importação de M.
        byte[] seguinte = csvDoNubank(linha(dia(m().minusMonths(4), 20), "Curso Online Ficticio - Parcela 4/10", "120.00"),
                linha(dia(m(), 15), "Mercado Fictício", "80.00"));
        JsonNode doMesSeguinte = api.json(previaNoCartao(usuario, horizonte, null, "fatura-seguinte.csv", seguinte));
        assertThat(doMesSeguinte.get("fatura").get("mes").asString()).isEqualTo(m().plusMonths(1).toString());
        assertThat(doMesSeguinte.get("linhas").valueStream().map(linha -> linha.get("linha").asInt() + ":" + linha.get("situacao").asString())
                .toList()).containsExactly("3:NOVA", "2:JA_IMPORTADA");
        assertThat(doMesSeguinte.get("linhas").get(1).get("parecido").get("descricao").asString()).isEqualTo("Curso Online Ficticio");
    }

    @Test
    void aProvavelDuplicadaEACompraOuAParcelaLancadaAMaoNaMesmaFatura() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novoCartao(usuario, "Cartão Horizonte", 3, 10, novaConta(usuario, "Banco Horizonte"));
        perfilDoNubank(usuario, horizonte);
        YearMonth antes = m().minusMonths(1);
        novaCompra(usuario, horizonte, dia(antes, 4), "Mercado Bom Preço", "312.45", 1);
        novaCompra(usuario, horizonte, dia(antes, 5), "Sapatos de corrida", "450", 3);
        byte[] fatura = csvDoNubank(linha(dia(antes, 4), "Mercado Bom Preco", "312.45"),
                linha(dia(antes, 5), "Sapataria Passo Firme - Parcela 1/3", "150.00"),
                linha(dia(antes, 6), "Sapataria Passo Firme - Parcela 2/3", "150.00"));

        JsonNode corpo = api.json(previaNoCartao(usuario, horizonte, null, "fatura.csv", fatura));

        assertThat(corpo.get("linhas").valueStream().map(linha -> linha.get("linha").asInt() + ":" + linha.get("situacao").asString() + ":"
                + linha.get("motivo").asString("") + ":" + linha.get("marcada").asBoolean()).toList())
                .containsExactly("4:NOVA::true", "3:PROVAVEL::false", "2:PROVAVEL::false");
        JsonNode mercado = corpo.get("linhas").get(2).get("parecido");
        assertThat(mercado.get("descricao").asString()).isEqualTo("Mercado Bom Preço");
        assertThat(mercado.get("manual").asBoolean()).isTrue();
        assertThat(mercado.get("data").asString()).isEqualTo(dia(antes, 4));
        assertThat(corpo.get("linhas").get(1).get("parecido").get("descricao").asString()).isEqualTo("Sapatos de corrida");
    }

    @Test
    void aParcelaDeUmaCompraQueJaEstaNoFluxEAOutraParcelaDoArquivoFicamDeFora() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novoCartao(usuario, "Cartão Horizonte", 3, 10, novaConta(usuario, "Banco Horizonte"));
        perfilDoNubank(usuario, horizonte);
        String data = dia(m().minusMonths(4), 20);
        importarNoCartao(usuario, horizonte, "fatura.csv", csvDoNubank(linha(dia(m().minusMonths(1), 9), "Padaria Pao Quente", "22.50"),
                linha(data, "Curso Online Ficticio - Parcela 3/10", "120.00")), 2, 3);
        // A fatura do mês anterior, importada depois: a parcela 2/10 da mesma compra, que já está no Flux a partir da 3ª. No
        // mesmo arquivo, a 5/10 e a 6/10 de outra compra: a 6/10 entra com a 5/10.
        byte[] anterior = csvDoNubank(linha(dia(m().minusMonths(2), 9), "Farmacia Vida", "85.30"),
                linha(data, "Curso Online Ficticio - Parcela 2/10", "120.00"),
                linha(dia(m().minusMonths(6), 2), "Geladeira Fria Mais - Parcela 5/10", "249.00"),
                linha(dia(m().minusMonths(6), 2), "Geladeira Fria Mais - Parcela 6/10", "249.00"));

        JsonNode corpo = api.json(previaNoCartao(usuario, horizonte, null, "fatura-anterior.csv", anterior));

        assertThat(corpo.get("fatura").get("mes").asString()).isEqualTo(m().minusMonths(1).toString());
        assertThat(corpo.get("linhas").valueStream().map(linha -> linha.get("linha").asInt() + ":" + linha.get("situacao").asString() + ":"
                + linha.get("motivo").asString("") + ":" + linha.get("parcelaQueCobre").asInt(0)).toList())
                .containsExactly("2:NOVA::0", "3:FORA:COMPRA_JA_NO_FLUX:3", "5:FORA:PARCELA_DO_ARQUIVO:5", "4:NOVA::0");
    }

    @Test
    void oOfxDeCartaoEOCsvDeOutroBancoComComprasPositivas() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novoCartao(usuario, "Cartão Horizonte", 3, 10, novaConta(usuario, "Banco Horizonte"));
        YearMonth antes = m().minusMonths(1);

        // O OFX de cartão não pede perfil; a compra vem negativa, como no Flux.
        MvcTestResult doOfx = previaNoCartao(usuario, horizonte, null, "fatura.ofx",
                ofxDeCartao(linha(dia(antes, 12), "Streaming Ficticio", "-39.90"), linha(dia(antes, 13), "Estorno Streaming", "39.90")));
        assertThat(doOfx).hasStatusOk().bodyJson().extractingPath("formato").isEqualTo("OFX");
        assertThat(doOfx).bodyJson().extractingPath("perfil").isNull();
        assertThat(doOfx).bodyJson().extractingPath("linhas[*].valor").asArray().containsExactly(39.90, -39.90);

        // O CSV de outro banco: ponto e vírgula, vírgula decimal e as compras positivas.
        String outro = "{\"separador\":\"PONTO_E_VIRGULA\",\"linhasDeCabecalho\":1,\"colunaData\":1,\"formatoData\":\"DD_MM_AAAA\","
                + "\"colunaDescricao\":2,\"colunaValor\":3,\"colunaSaidas\":null,\"colunaIdentificador\":null,\"separadorDecimal\":\"VIRGULA\","
                + "\"comprasPositivas\":%s}";
        String csv = "Data;Lançamento;Valor\n" + antes.atDay(12).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                + ";Posto Ficticio;\"150,00\"\n";
        assertThat(api.put(usuario, "/api/importacoes/perfis/cartao/{id}", outro.formatted("true"), horizonte)).hasStatusOk();
        assertThat(previaNoCartao(usuario, horizonte, null, "outro-banco.csv", csv.getBytes(StandardCharsets.UTF_8)))
                .bodyJson().extractingPath("linhas[0].valor").isEqualTo(-150.0);
        assertThat(api.put(usuario, "/api/importacoes/perfis/cartao/{id}", outro.formatted("false"), horizonte)).hasStatusOk();
        assertThat(previaNoCartao(usuario, horizonte, null, "outro-banco.csv", csv.getBytes(StandardCharsets.UTF_8)))
                .bodyJson().extractingPath("linhas[0].valor").isEqualTo(150.0);
    }

    @Test
    void oQueNaoServeNoCartaoENaContaERecusadoComOMotivo() throws Exception {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long conta = novaConta(usuario, "Banco Horizonte");
        long horizonte = novoCartao(usuario, "Cartão Horizonte", 3, 10, conta);
        long lua = novoCartao(usuario, "Cartão Lua", 25, 5, conta);
        api.acao(usuario, "/api/cartoes/{id}/arquivar", lua);
        byte[] contaNubank = "Data,Valor,Identificador,Descrição\n05/11/2026,-6.00,x1,CAFE\n".getBytes(StandardCharsets.UTF_8);

        assertRecusa(previaNoCartao(usuario, horizonte, null, "extrato.ofx", ImportacaoIT.fixture("nubank-conta.ofx")), "EXTRATO_DE_CONTA",
                "Este arquivo é o extrato de uma conta: escolha uma conta no lugar do cartão.");
        assertRecusa(previaNoCartao(usuario, horizonte, null, "extrato.csv", contaNubank), "EXTRATO_DE_CONTA",
                "Este arquivo é o extrato de uma conta: escolha uma conta no lugar do cartão.");
        assertRecusa(mvc.post().uri("/api/importacoes/amostra").header(HttpHeaders.AUTHORIZATION, "Bearer " + usuario.tokenAcesso()).multipart()
                .file(new MockMultipartFile("arquivo", "extrato.csv", "text/csv", contaNubank))
                .param("cartao", "true").exchange(), "EXTRATO_DE_CONTA", "Este arquivo é o extrato de uma conta: escolha uma conta no lugar do cartão.");
        assertRecusa(mvc.post().uri("/api/importacoes/amostra").header(HttpHeaders.AUTHORIZATION, "Bearer " + usuario.tokenAcesso()).multipart()
                .file(new MockMultipartFile("arquivo", "fatura.csv", "text/csv", faturaDeM())).exchange(),
                "EXTRATO_DE_CARTAO", "Este arquivo é a fatura de um cartão: escolha um cartão no lugar da conta.");
        assertRecusa(previaNoCartao(usuario, horizonte, null, "fatura.pdf", "%PDF-1.7 fictício".getBytes(StandardCharsets.UTF_8)),
                "ARQUIVO_INVALIDO", "Não reconhecemos este arquivo. Envie a fatura do cartão em OFX ou CSV.");
        assertRecusa(previaNoCartao(usuario, lua, null, "fatura.ofx", ofxDeCartao(linha(dia(m(), 1), "Loja", "-1.00"))), "CARTAO_ARQUIVADO",
                "Cartão Lua está arquivado. Reative o cartão para importar nele.");
        assertThat(previaNoCartao(usuario, 999_999_999L, null, "fatura.ofx", ofxDeCartao(linha(dia(m(), 1), "Loja", "-1.00"))))
                .hasStatus(422).bodyJson().extractingPath("detail").isEqualTo("Cartão não encontrado.");
        assertCampo(mvc.post().uri("/api/importacoes/previa").header(HttpHeaders.AUTHORIZATION, "Bearer " + usuario.tokenAcesso()).multipart()
                .file(new MockMultipartFile("arquivo", "fatura.ofx", "text/plain", ofxDeCartao(
                        linha(dia(m(), 1), "Loja", "-1.00")))).param("contaId", String.valueOf(conta))
                .param("cartaoId", String.valueOf(horizonte)).exchange(), "contaId", "Escolha uma conta ou um cartão.");
        assertCampo(confirmarNoCartao(usuario, horizonte, "2026-1", "fatura.ofx", ofxDeCartao(linha(dia(m(), 1), "Loja", "-1.00")), "x",
                escolhas(1)), "fatura", "Mês inválido.");
    }

    private static void assertRecusa(MvcTestResult resposta, String codigo, String mensagem) {
        assertThat(resposta).hasStatus(422).bodyJson().extractingPath("codigo").isEqualTo(codigo);
        assertThat(resposta).bodyJson().extractingPath("detail").isEqualTo(mensagem);
    }

    @Test
    void osCentavosDaDivisaoNaoDuplicamACompraParcelada() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novoCartao(usuario, "Cartão Horizonte", 3, 10, novaConta(usuario, "Banco Horizonte"));
        perfilDoNubank(usuario, horizonte);
        String data = dia(m().minusMonths(2), 20);
        // R$ 100,00 em 3x, com o resto na 1ª: 33,34 na fatura de M (com a padaria, que sugere a fatura) e 33,33 na de M+1.
        byte[] deM = csvDoNubank(linha(data, "Loja Ficticia - Parcela 1/3", "33.34"), linha(dia(m().minusMonths(1), 25), "Padaria Pao Quente",
                "9.90"));
        assertThat(importarNoCartao(usuario, horizonte, "fatura-m.csv", deM, 2, 3)).isPositive();
        assertThat(contar("compras", usuario)).isEqualTo(2);
        byte[] deM1 = csvDoNubank(linha(data, "Loja Ficticia - Parcela 2/3", "33.33"), linha(dia(m(), 25), "Mercado Ficticio", "15.00"));

        JsonNode seguinte = api.json(previaNoCartao(usuario, horizonte, null, "fatura-m1.csv", deM1));

        assertThat(seguinte.get("fatura").get("mes").asString()).isEqualTo(m().plusMonths(1).toString());
        assertThat(seguinte.get("linhas").valueStream().map(linha -> linha.get("linha").asInt() + ":" + linha.get("situacao").asString())
                .toList()).containsExactly("3:NOVA", "2:JA_IMPORTADA");
        assertThat(importarNoCartao(usuario, horizonte, "fatura-m1.csv", deM1, 2, 3)).isPositive();
        assertThat(contar("compras", usuario)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from lancamentos where usuario_id = ? and descricao = 'Loja Ficticia'", Integer.class,
                usuario.id())).isEqualTo(3);
    }

    @Test
    void aParcelaComOutrosCentavosFicaComOValorDoBanco() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long conta = novaConta(usuario, "Banco Horizonte");
        long horizonte = novoCartao(usuario, "Cartão Horizonte", 3, 10, conta);
        perfilDoNubank(usuario, horizonte);
        String data = dia(m().minusMonths(2), 20);
        // R$ 100,00 em 3x, com o resto na 1ª: a compra entra pela 1/3 com 3 × 33,34 (R$ 100,02).
        importarNoCartao(usuario, horizonte, "fatura-m.csv", csvDoNubank(linha(data, "Loja Ficticia - Parcela 1/3", "33.34"),
                linha(dia(m().minusMonths(1), 25), "Padaria Pao Quente", "9.90")), 2, 3);
        long compra = jdbc.queryForObject("select id from compras where usuario_id = ? and parcelas = 3", Long.class, usuario.id());
        byte[] deM1 = csvDoNubank(linha(data, "Loja Ficticia - Parcela 2/3", "33.33"), linha(dia(m(), 25), "Mercado Ficticio", "15.00"));
        String mes = m().plusMonths(1).toString();

        JsonNode previa = api.json(previaNoCartao(usuario, horizonte, null, "fatura-m1.csv", deM1));

        JsonNode loja = previa.get("linhas").valueStream().filter(linha -> linha.get("linha").asInt() == 2).findFirst().orElseThrow();
        assertThat(loja.get("situacao").asString()).isEqualTo("JA_IMPORTADA");
        assertThat(loja.get("valorNoFlux").decimalValue()).isEqualByComparingTo("-33.34");
        assertThat(loja.get("valor").decimalValue()).isEqualByComparingTo("-33.33");
        // Só a correção, com o mercado desmarcado: nada é importado, e nada fica no histórico.
        MvcTestResult confirmado = confirmarNoCartao(usuario, horizonte, mes, "fatura-m1.csv", deM1, previa.get("hash").asString(),
                escolhas(2));
        assertThat(confirmado).hasStatus(201);
        JsonNode resultado = api.json(confirmado);
        assertThat(resultado.get("importacaoId").isNull()).isTrue();
        assertThat(resultado.get("importadas").asInt()).isZero();
        assertThat(resultado.get("corrigidas").asInt()).isEqualTo(1);
        assertThat(resultado.get("entraramPorOutra").asInt()).isZero();
        assertThat(resultado.get("jaImportadas").asInt()).isZero();
        assertThat(contar("importacoes", usuario)).isEqualTo(1);
        assertThat(parcelas(compra)).containsExactly("1 " + m().atDay(10) + " -33.34 " + m(), "2 " + m().plusMonths(1).atDay(10) + " -33.33 "
                + m().plusMonths(1), "3 " + m().plusMonths(2).atDay(10) + " -33.34 " + m().plusMonths(2));
        assertThat(jdbc.queryForObject("select valor_total from compras where id = ?", BigDecimal.class, compra)).isEqualByComparingTo("100.01");
        // A parcela corrigida não conta como editada no histórico.
        assertThat(api.json(api.get(usuario, "/api/importacoes")).get(0).get("editados").asInt()).isZero();

        // A fatura fica igual à do banco e vira paga com o valor dele.
        assertThat(api.json(api.get(usuario, "/api/cartoes/{id}/faturas/{mes}", horizonte, mes)).get("total").decimalValue())
                .isEqualByComparingTo("33.33");
        assertThat(api.post(usuario, "/api/cartoes/{id}/faturas/{mes}/pagamentos", "{\"contaId\":" + conta + ",\"data\":\"" + hoje()
                + "\",\"valor\":33.33,\"observacao\":null}", horizonte, mes)).hasStatus(201);
        assertThat(api.json(api.get(usuario, "/api/cartoes/{id}/faturas/{mes}", horizonte, mes)).get("situacao").asString()).isEqualTo("PAGA");
        // O mesmo arquivo de novo: nada mais a corrigir.
        assertThat(api.json(previaNoCartao(usuario, horizonte, null, "fatura-m1.csv", deM1)).get("linhas").valueStream()
                .filter(linha -> linha.get("linha").asInt() == 2).findFirst().orElseThrow().get("valorNoFlux").isNull()).isTrue();
    }

    @Test
    void aParcelaNaFaturaPagaNaoMuda() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long conta = novaConta(usuario, "Banco Horizonte");
        long horizonte = novoCartao(usuario, "Cartão Horizonte", 3, 10, conta);
        perfilDoNubank(usuario, horizonte);
        String data = dia(m().minusMonths(2), 20);
        importarNoCartao(usuario, horizonte, "fatura-m.csv", csvDoNubank(linha(data, "Loja Ficticia - Parcela 1/3", "33.34"),
                linha(dia(m().minusMonths(1), 25), "Padaria Pao Quente", "9.90")), 2, 3);
        long compra = jdbc.queryForObject("select id from compras where usuario_id = ? and parcelas = 3", Long.class, usuario.id());
        String mes = m().plusMonths(1).toString();
        // A fatura seguinte foi paga no Flux com os 33,34 dela antes da importação.
        assertThat(api.post(usuario, "/api/cartoes/{id}/faturas/{mes}/pagamentos", "{\"contaId\":" + conta + ",\"data\":\"" + hoje()
                + "\",\"valor\":33.34,\"observacao\":null}", horizonte, mes)).hasStatus(201);
        byte[] deM1 = csvDoNubank(linha(data, "Loja Ficticia - Parcela 2/3", "33.33"), linha(dia(m(), 25), "Mercado Ficticio", "15.00"));

        JsonNode previa = api.json(previaNoCartao(usuario, horizonte, null, "fatura-m1.csv", deM1));

        JsonNode loja = previa.get("linhas").valueStream().filter(linha -> linha.get("linha").asInt() == 2).findFirst().orElseThrow();
        assertThat(loja.get("situacao").asString()).isEqualTo("JA_IMPORTADA");
        assertThat(loja.get("valorNoFlux").isNull()).isTrue();
        // Mesmo mandada na confirmação, a parcela da fatura paga não muda.
        JsonNode resultado = api.json(confirmarNoCartao(usuario, horizonte, mes, "fatura-m1.csv", deM1, previa.get("hash").asString(),
                escolhas(2)));
        assertThat(resultado.get("corrigidas").asInt()).isZero();
        assertThat(parcelas(compra)).contains("2 " + m().plusMonths(1).atDay(10) + " -33.34 " + m().plusMonths(1));
        assertThat(jdbc.queryForObject("select valor_total from compras where id = ?", BigDecimal.class, compra)).isEqualByComparingTo("100.02");
        assertThat(api.json(api.get(usuario, "/api/cartoes/{id}/faturas/{mes}", horizonte, mes)).get("situacao").asString()).isEqualTo("PAGA");
    }

    @Test
    void oArquivoJaImportadoEmOutroLugarSoAvisa() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long conta = novaConta(usuario, "Banco Horizonte");
        long horizonte = novoCartao(usuario, "Cartão Horizonte", 3, 10, conta);
        String perfil = "{\"separador\":\"PONTO_E_VIRGULA\",\"linhasDeCabecalho\":1,\"colunaData\":1,\"formatoData\":\"DD_MM_AAAA\","
                + "\"colunaDescricao\":2,\"colunaValor\":3,\"colunaSaidas\":null,\"colunaIdentificador\":null,\"separadorDecimal\":\"VIRGULA\"}";
        assertThat(api.put(usuario, "/api/importacoes/perfis/cartao/{id}", perfil, horizonte)).hasStatusOk();
        assertThat(api.put(usuario, "/api/importacoes/perfis/{id}", perfil, conta)).hasStatusOk();
        byte[] csv = ("Data;Descrição;Valor\n" + m().atDay(5).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ";Livraria Pagina;-59,90\n")
                .getBytes(StandardCharsets.UTF_8);
        importarNoCartao(usuario, horizonte, "extrato.csv", csv, 2);

        // O mesmo arquivo, agora numa conta: a revisão não quebra e diz que ele entrou no cartão.
        MvcTestResult naConta = mvc.post().uri("/api/importacoes/previa").header(HttpHeaders.AUTHORIZATION, "Bearer " + usuario.tokenAcesso())
                .multipart().file(new MockMultipartFile("arquivo", "extrato.csv", "text/csv", csv)).param("contaId", String.valueOf(conta))
                .exchange();
        assertThat(naConta).hasStatusOk();
        JsonNode repetido = api.json(naConta).get("arquivoRepetido");
        assertThat(repetido.get("contaId").isNull()).isTrue();
        assertThat(repetido.get("cartaoId").asLong()).isEqualTo(horizonte);
        assertThat(repetido.get("contaNome").asString()).isEqualTo("Cartão Horizonte");
        assertThat(repetido.get("mesmaConta").asBoolean()).isFalse();
        String hash = api.json(naConta).get("hash").asString();
        String pedido = "{\"contaId\":" + conta + ",\"hash\":\"" + hash + "\",\"linhas\":[{\"linha\":2,\"categoriaId\":null}]}";
        assertThat(mvc.post().uri("/api/importacoes").header(HttpHeaders.AUTHORIZATION, "Bearer " + usuario.tokenAcesso()).multipart()
                .file(new MockMultipartFile("arquivo", "extrato.csv", "text/csv", csv))
                .file(new MockMultipartFile("pedido", "", MediaType.APPLICATION_JSON_VALUE, pedido.getBytes(StandardCharsets.UTF_8)))
                .exchange()).hasStatus(201);

        // E o contrário: a importação mais recente é a da conta, e a revisão no cartão diz a conta.
        JsonNode noCartao = api.json(previaNoCartao(usuario, horizonte, null, "extrato.csv", csv)).get("arquivoRepetido");
        assertThat(noCartao.get("contaId").asLong()).isEqualTo(conta);
        assertThat(noCartao.get("cartaoId").isNull()).isTrue();
        assertThat(noCartao.get("contaNome").asString()).isEqualTo("Banco Horizonte");
        assertThat(noCartao.get("mesmaConta").asBoolean()).isFalse();
    }

    @Test
    void oExportParcialEDepoisOCompletoNaoDuplicamAsLinhasIguais() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novoCartao(usuario, "Cartão Horizonte", 3, 10, novaConta(usuario, "Banco Horizonte"));
        perfilDoNubank(usuario, horizonte);
        String dia = dia(m().minusMonths(1), 20);
        // O export da fatura ainda aberta: dois cafés iguais no mesmo dia.
        byte[] parcial = csvDoNubank(linha(dia, "Cafe da Esquina", "6.00"), linha(dia, "Cafe da Esquina", "6.00"));
        importarNoCartao(usuario, horizonte, "parcial.csv", parcial, 2, 3);
        // O export da fatura fechada: os dois de antes, um terceiro café igual e uma compra nova.
        byte[] completo = csvDoNubank(linha(dia, "Cafe da Esquina", "6.00"), linha(dia, "Cafe da Esquina", "6.00"),
                linha(dia, "Cafe da Esquina", "6.00"), linha(dia(m().minusMonths(1), 22), "Livraria Pagina", "59.90"));

        JsonNode previa = api.json(previaNoCartao(usuario, horizonte, null, "completo.csv", completo));

        assertThat(previa.get("linhas").valueStream().map(linha -> linha.get("linha").asInt() + ":" + linha.get("situacao").asString())
                .sorted().toList()).containsExactly("2:JA_IMPORTADA", "3:JA_IMPORTADA", "4:NOVA", "5:NOVA");
    }
}
