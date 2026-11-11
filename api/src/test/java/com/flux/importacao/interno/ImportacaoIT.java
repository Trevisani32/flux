package com.flux.importacao.interno;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;

import static com.flux.suporte.ApiDeTeste.assertCampo;
import static com.flux.suporte.ApiDeTeste.lancamento;
import static com.flux.suporte.ApiDeTeste.texto;
import static org.assertj.core.api.Assertions.assertThat;

/** O fluxo da importação pela API: revisão sem gravar, confirmação, reimportação, CSV com perfil e recusas. */
class ImportacaoIT extends ImportacaoBase {

    @Test
    void previaClassificaCadaLinhaENaoGravaNada() throws IOException {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novaConta(usuario, "Banco Horizonte", "2026-11-04");
        api.idCriado(api.post(usuario, "/api/lancamentos", lancamento("RECEITA", "7200", "2026-11-05", "Salário", horizonte, null)));

        MvcTestResult previa = previa(usuario, horizonte, "extrato-novembro.ofx", fixture("nubank-conta.ofx"));

        assertThat(previa).hasStatusOk();
        JsonNode corpo = api.json(previa);
        assertThat(corpo.get("hash").asString()).hasSize(64);
        assertThat(corpo.get("formato").asString()).isEqualTo("OFX");
        assertThat(corpo.get("nomeArquivo").asString()).isEqualTo("extrato-novembro.ofx");
        assertThat(corpo.get("dataInicio").asString()).isEqualTo("2026-11-03");
        assertThat(corpo.get("dataFim").asString()).isEqualTo("2026-11-06");
        assertThat(corpo.get("arquivoRepetido").isNull()).isTrue();
        assertThat(corpo.get("outraConta").isNull()).isTrue();
        JsonNode linhas = corpo.get("linhas");
        assertThat(linhas.valueStream().map(linha -> linha.get("linha").asInt() + ":" + linha.get("situacao").asString() + ":"
                + linha.get("marcada").asBoolean()).toList())
                .containsExactly("5:FORA:false", "4:PROVAVEL:false", "3:NOVA:true", "2:NOVA:true", "1:ANTES_DO_SALDO_INICIAL:false");
        assertThat(linhas.get(0).get("motivo").asString()).isEqualTo("VALOR_ZERO");
        assertThat(linhas.get(1).get("parecido").get("descricao").asString()).isEqualTo("Salário");
        assertThat(linhas.get(1).get("parecido").get("manual").asBoolean()).isTrue();
        assertThat(linhas.get(1).get("parecido").get("criadoEm").isNull()).isFalse();
        assertThat(linhas.get(1).get("parecido").get("excluidoEm").isNull()).isTrue();
        assertThat(corpo.get("perfil").isNull()).isTrue();
        assertThat(corpo.get("origens").size()).isZero();
        assertThat(texto(previa)).contains("\"valor\":-6.00");
        assertThat(jdbc.queryForObject("select count(*) from lancamentos where usuario_id = ? and origem = 'IMPORTADO'",
                Integer.class, usuario.id())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from importacoes where usuario_id = ?", Integer.class, usuario.id())).isZero();
    }

    @Test
    void confirmarGravaAsMarcadasComCategoriaERegistraAImportacao() throws IOException {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novaConta(usuario, "Banco Horizonte", "2026-10-01");
        long restaurante = idDaCategoria(usuario, "Restaurante");
        byte[] arquivo = fixture("nubank-conta.ofx");
        String hash = api.json(previa(usuario, horizonte, "extrato-novembro.ofx", arquivo)).get("hash").asString();

        MvcTestResult resultado = confirmar(usuario, horizonte, "extrato-novembro.ofx", arquivo, hash,
                "[{\"linha\":2,\"categoriaId\":" + restaurante + "},{\"linha\":3,\"categoriaId\":null},{\"linha\":4,\"categoriaId\":null}]");

        assertThat(resultado).hasStatus(201);
        assertThat(resultado).bodyJson().extractingPath("importadas").isEqualTo(3);
        assertThat(resultado).bodyJson().extractingPath("desmarcadas").isEqualTo(1);
        assertThat(resultado).bodyJson().extractingPath("jaImportadas").isEqualTo(0);
        assertThat(resultado).bodyJson().extractingPath("deFora").isEqualTo(1);
        assertThat(resultado).bodyJson().extractingPath("entraramPorOutra").isEqualTo(0);
        assertThat(resultado).bodyJson().extractingPath("contaNome").isEqualTo("Banco Horizonte");
        assertThat(resultado).bodyJson().extractingPath("mesDaUltimaLinha").isEqualTo("2026-11");
        assertThat(texto(resultado)).contains("\"saldoDeHoje\":8188.00");
        long importacaoId = api.json(resultado).get("importacaoId").asLong();
        assertThat(jdbc.queryForList("select lidas || '/' || importadas || '/' || duplicadas || '/' || anteriores || '/' || fora || '/' "
                + "|| desmarcadas || '/' || formato || '/' || extrato_final from importacoes where id = ?", String.class, importacaoId))
                .containsExactly("5/3/0/0/1/1/OFX/9999");
        long cafe = lancamentoDaChave(usuario, "ID:" + horizonte + ":0f1e2d3c-0000-4000-8000-000000000002");
        assertThat(jdbc.queryForList("select tipo || '/' || valor || '/' || origem || '/' || descricao_banco || '/' || categoria_id "
                + "from lancamentos where id = ?", String.class, cafe))
                .containsExactly("DESPESA/-6.00/IMPORTADO/Compra no débito - CAFE DA ESQUINA/" + restaurante);

        MvcTestResult lista = api.get(usuario, "/api/lancamentos?mes=2026-11&conta={conta}", horizonte);
        assertThat(lista).bodyJson().extractingPath("quantidadeLancamentos").isEqualTo(3);
        assertThat(lista).bodyJson().extractingPath("dias[0].itens[0].origem").isEqualTo("IMPORTADO");
        assertThat(lista).bodyJson().extractingPath("dias[0].itens[0].descricaoBanco").isEqualTo("Transferência recebida - EMPRESA FICTÍCIA LTDA");
    }

    @Test
    void reimportarOMesmoArquivoAvisaEReconheceTudo() throws IOException {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novaConta(usuario, "Banco Horizonte", "2026-10-01");
        byte[] arquivo = fixture("nubank-conta.ofx");
        importar(usuario, horizonte, "extrato-novembro.ofx", arquivo, 1, 2, 3, 4);

        MvcTestResult previa = previa(usuario, horizonte, "extrato-novembro.ofx", arquivo);

        JsonNode corpo = api.json(previa);
        assertThat(corpo.get("arquivoRepetido").get("mesmaConta").asBoolean()).isTrue();
        assertThat(corpo.get("arquivoRepetido").get("contaNome").asString()).isEqualTo("Banco Horizonte");
        assertThat(corpo.get("linhas").valueStream().map(linha -> linha.get("situacao").asString()).toList())
                .containsExactly("FORA", "JA_IMPORTADA", "JA_IMPORTADA", "JA_IMPORTADA", "JA_IMPORTADA");
        assertThat(corpo.get("origens").size()).isEqualTo(1);
        assertThat(corpo.get("origens").get(0).get("nomeArquivo").asString()).isEqualTo("extrato-novembro.ofx");
        assertThat(corpo.get("origens").get(0).get("importadaEm").isNull()).isFalse();

        MvcTestResult resultado = confirmar(usuario, horizonte, "extrato-novembro.ofx", arquivo, corpo.get("hash").asString(),
                escolhas(2));
        assertThat(resultado).hasStatus(201);
        assertThat(resultado).bodyJson().extractingPath("importadas").isEqualTo(0);
        assertThat(resultado).bodyJson().extractingPath("entraramPorOutra").isEqualTo(1);
        assertThat(api.json(resultado).get("importacaoId").isNull()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from importacoes where usuario_id = ?", Integer.class, usuario.id())).isEqualTo(1);
    }

    @Test
    void oArquivoDaConfirmacaoPrecisaSerODaRevisao() throws IOException {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novaConta(usuario, "Banco Horizonte", "2026-10-01");
        String hash = api.json(previa(usuario, horizonte, "extrato-novembro.ofx", fixture("nubank-conta.ofx"))).get("hash").asString();

        MvcTestResult resultado = confirmar(usuario, horizonte, "outro.ofx", fixture("extrato-2x.ofx"), hash, escolhas(1));

        assertThat(resultado).hasStatus(422).bodyJson().extractingPath("codigo").isEqualTo("ARQUIVO_DIFERENTE");
        assertThat(resultado).bodyJson().extractingPath("detail").isEqualTo("O arquivo mudou desde a revisão. Escolha o arquivo de novo.");
        assertThat(confirmar(usuario, horizonte, "extrato-novembro.ofx", fixture("nubank-conta.ofx"), hash, "[]"))
                .hasStatus(400).bodyJson().extractingPath("campos[0].mensagem").isEqualTo("Marque ao menos uma linha.");
    }

    @Test
    void csvSemPerfilPedeOPerfilEDepoisImporta() throws IOException {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long conjunta = novaConta(usuario, "Conta conjunta", "2026-09-01");
        byte[] csv = fixture("nubank-conta.csv");

        MvcTestResult semPerfil = previa(usuario, conjunta, "nubank-conta.csv", csv);
        assertThat(semPerfil).hasStatus(422).bodyJson().extractingPath("codigo").isEqualTo("PERFIL_NECESSARIO");
        assertThat(api.get(usuario, "/api/importacoes/perfis/{conta}", conjunta)).hasStatus(404);

        MvcTestResult amostra = mvc.post().uri("/api/importacoes/amostra").header(HttpHeaders.AUTHORIZATION, "Bearer " + usuario.tokenAcesso())
                .multipart().file(new MockMultipartFile("arquivo", "nubank-conta.csv", MediaType.TEXT_PLAIN_VALUE, csv)).exchange();
        assertThat(amostra).hasStatusOk();
        assertThat(amostra).bodyJson().extractingPath("modelo").isEqualTo("NUBANK_CONTA");
        assertThat(amostra).bodyJson().extractingPath("linhas[0][3]").isEqualTo("Descrição");
        assertThat(amostra).bodyJson().extractingPath("formato.colunaIdentificador").isEqualTo(3);
        assertThat(amostra).bodyJson().extractingPath("primeiraLinha.data").isEqualTo("2026-11-03");
        assertThat(amostra).bodyJson().extractingPath("primeiraLinha.descricao").isEqualTo("Compra no débito - PADARIA CENTRAL");
        assertThat(texto(amostra)).contains("\"valor\":-18.50");

        String nubank = "{\"separador\":\"VIRGULA\",\"linhasDeCabecalho\":1,\"colunaData\":1,\"formatoData\":\"DD_MM_AAAA\","
                + "\"colunaDescricao\":4,\"colunaValor\":2,\"colunaSaidas\":null,\"colunaIdentificador\":3,\"separadorDecimal\":\"PONTO\"}";
        assertCampo(api.put(usuario, "/api/importacoes/perfis/{conta}", nubank.replace("\"colunaValor\":2", "\"colunaValor\":4"),
                conjunta), "colunas", "Use uma coluna diferente para cada campo.");
        assertCampo(api.put(usuario, "/api/importacoes/perfis/{conta}", nubank.replace("\"colunaSaidas\":null", "\"colunaSaidas\":2"),
                conjunta), "colunaSaidas", "Use colunas diferentes para entradas e saídas.");
        assertThat(api.put(usuario, "/api/importacoes/perfis/{conta}", nubank, conjunta)).hasStatusOk();
        assertThat(api.get(usuario, "/api/importacoes/perfis/{conta}", conjunta)).bodyJson().extractingPath("colunaDescricao").isEqualTo(4);

        MvcTestResult previa = previa(usuario, conjunta, "nubank-conta.csv", csv);
        assertThat(previa).hasStatusOk();
        assertThat(previa).bodyJson().extractingPath("perfil.colunaDescricao").isEqualTo(4);
        assertThat(api.json(previa).get("linhas").valueStream().map(linha -> linha.get("situacao").asString()).toList())
                .containsExactly("NOVA", "NOVA", "NOVA", "NOVA");
        importar(usuario, conjunta, "nubank-conta.csv", csv, 2);
        assertThat(lancamentoDaChave(usuario, "ID:" + conjunta + ":6a1f0000-0000-4000-8000-0000000000a1")).isPositive();
    }

    @Test
    void arquivosQueNaoServemSaoRecusadosComOMotivo() throws IOException {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novaConta(usuario, "Banco Horizonte", "2026-10-01");
        long salario = novaConta(usuario, "Conta salário", "2026-10-01");
        api.acao(usuario, "/api/contas/{id}/arquivar", salario);
        String duasContas = "<OFX><BANKMSGSRSV1><STMTTRNRS><STMTRS><BANKTRANLIST><STMTTRN><DTPOSTED>20261103<TRNAMT>-1.00<FITID>a"
                + "<MEMO>A</STMTTRN></BANKTRANLIST></STMTRS></STMTTRNRS><STMTTRNRS><STMTRS><BANKTRANLIST><STMTTRN><DTPOSTED>20261103"
                + "<TRNAMT>-2.00<FITID>b<MEMO>B</STMTTRN></BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>";
        String muitas = IntStream.rangeClosed(1, 3_001)
                .mapToObj(i -> "<STMTTRN><DTPOSTED>20261103<TRNAMT>-1.00<FITID>m" + i + "<MEMO>LINHA " + i + "</STMTTRN>")
                .collect(Collectors.joining("", "<OFX><BANKMSGSRSV1><STMTTRNRS><STMTRS><BANKTRANLIST>",
                        "</BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>"));

        assertRecusa(previa(usuario, horizonte, "fatura.ofx", fixture("cartao.ofx")), "EXTRATO_DE_CARTAO",
                "Este arquivo é a fatura de um cartão de crédito. A importação de faturas chega junto com os cartões; por enquanto, "
                        + "importe o extrato da conta.");
        assertRecusa(previa(usuario, horizonte, "fatura.csv", "date,title,amount\n2026-11-03,Streaming,39.90\n".getBytes(StandardCharsets.UTF_8)),
                "EXTRATO_DE_CARTAO", "Este CSV parece ser a fatura do cartão Nubank. A importação de faturas chega junto com os cartões.");
        assertRecusa(previa(usuario, horizonte, "duas.ofx", duasContas.getBytes(StandardCharsets.UTF_8)), "VARIAS_CONTAS",
                "Este arquivo tem extratos de 2 contas. Exporte uma conta por arquivo.");
        assertRecusa(previa(usuario, horizonte, "extrato.pdf", "%PDF-1.7 fictício".getBytes(StandardCharsets.UTF_8)), "ARQUIVO_INVALIDO",
                "Não reconhecemos este arquivo. Envie o extrato da conta em OFX ou CSV.");
        assertRecusa(previa(usuario, horizonte, "vazio.ofx", new byte[0]), "ARQUIVO_VAZIO", "O arquivo não tem nenhuma movimentação.");
        assertRecusa(previa(usuario, horizonte, "muitas.ofx", muitas.getBytes(StandardCharsets.UTF_8)), "ARQUIVO_GRANDE",
                "O arquivo tem 3.001 movimentações. Importe no máximo 3.000 por vez: exporte um período menor.");
        assertRecusa(previa(usuario, salario, "extrato-novembro.ofx", fixture("nubank-conta.ofx")), "CONTA_ARQUIVADA",
                "Conta salário está arquivada. Reative a conta para importar nela.");
        assertThat(previa(usuario, 999_999_999L, "extrato-novembro.ofx", fixture("nubank-conta.ofx")))
                .hasStatus(422).bodyJson().extractingPath("detail").isEqualTo("Conta não encontrada.");
    }

    @Test
    void categoriaArquivadaDepoisDaRevisaoRecusaAImportacaoInteira() throws IOException {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novaConta(usuario, "Banco Horizonte", "2026-10-01");
        long restaurante = idDaCategoria(usuario, "Restaurante");
        byte[] arquivo = fixture("nubank-conta.ofx");
        String hash = api.json(previa(usuario, horizonte, "extrato-novembro.ofx", arquivo)).get("hash").asString();
        assertThat(api.acao(usuario, "/api/categorias/{id}/arquivar", restaurante)).hasStatusOk();

        MvcTestResult resultado = confirmar(usuario, horizonte, "extrato-novembro.ofx", arquivo, hash,
                "[{\"linha\":1,\"categoriaId\":null},{\"linha\":3,\"categoriaId\":" + restaurante + "}]");

        assertThat(resultado).hasStatus(422).bodyJson().extractingPath("codigo").isEqualTo("CATEGORIA_INVALIDA");
        assertThat(resultado).bodyJson().extractingPath("linha").isEqualTo(3);
        assertThat(resultado).bodyJson().extractingPath("detail")
                .isEqualTo("Uma das categorias escolhidas foi arquivada ou excluída. Revise as linhas marcadas.");
        assertThat(jdbc.queryForObject("select count(*) from lancamentos where usuario_id = ?", Integer.class, usuario.id())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from importacoes where usuario_id = ?", Integer.class, usuario.id())).isZero();
    }

    @Test
    void oMesmoExtratoEmOutraContaAvisa() throws IOException {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novaConta(usuario, "Banco Horizonte", "2026-10-01");
        long poupanca = novaConta(usuario, "Poupança", "2026-10-01");
        importar(usuario, horizonte, "extrato-novembro.ofx", fixture("nubank-conta.ofx"), 2);

        JsonNode corpo = api.json(previa(usuario, poupanca, "extrato-novembro.ofx", fixture("nubank-conta.ofx")));

        assertThat(corpo.get("outraConta").get("contaId").asLong()).isEqualTo(horizonte);
        assertThat(corpo.get("outraConta").get("contaNome").asString()).isEqualTo("Banco Horizonte");
        assertThat(corpo.get("outraConta").get("finalDaConta").asString()).isEqualTo("9999");
        assertThat(corpo.get("arquivoRepetido").get("mesmaConta").asBoolean()).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from importacoes where extrato_hash like '%99999999%'", Integer.class)).isZero();
        // HMAC com a chave do servidor (ADR 0014): não é o SHA-256 puro de BANKID e ACCTID, que a força bruta reverteria.
        assertThat(jdbc.queryForObject("select extrato_hash from importacoes where conta_id = ?", String.class, horizonte))
                .hasSize(64).isNotEqualTo(Chaves.sha256("0260|99999999-9"));

        // Com a outra conta arquivada, o aviso some: "Importar em" levaria a uma conta que não recebe importação.
        assertThat(api.acao(usuario, "/api/contas/{id}/arquivar", horizonte)).hasStatusOk();
        JsonNode depois = api.json(previa(usuario, poupanca, "extrato-novembro.ofx", fixture("nubank-conta.ofx")));
        assertThat(depois.get("outraConta").isNull()).isTrue();
        assertThat(depois.get("arquivoRepetido").get("contaNome").asString()).isEqualTo("Banco Horizonte");
    }

    private static void assertRecusa(MvcTestResult resposta, String codigo, String mensagem) {
        assertThat(resposta).hasStatus(422).bodyJson().extractingPath("codigo").isEqualTo(codigo);
        assertThat(resposta).bodyJson().extractingPath("detail").isEqualTo(mensagem);
    }

    static byte[] fixture(String nome) throws IOException {
        try (InputStream entrada = ImportacaoIT.class.getResourceAsStream("/fixtures/" + nome)) {
            return entrada.readAllBytes();
        }
    }
}
