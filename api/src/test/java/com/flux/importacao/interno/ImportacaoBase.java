package com.flux.importacao.interno;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.stream.Collectors;

import com.flux.suporte.IntegracaoBase;
import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static com.flux.suporte.ApiDeTeste.conta;
import static org.assertj.core.api.Assertions.assertThat;

/** Apoio das integrações da importação: os pedidos multipart e um OFX fictício montado na hora. */
abstract class ImportacaoBase extends IntegracaoBase {

    @Autowired
    protected JdbcTemplate jdbc;

    /** Uma transação do OFX de teste; {@code valor} como no arquivo ("-6.00"). */
    record Transacao(String fitid, LocalDate data, String valor, String memo) {
    }

    protected static Transacao transacao(String fitid, String data, String valor, String memo) {
        return new Transacao(fitid, LocalDate.parse(data), valor, memo);
    }

    /** OFX 1.x no estilo do Nubank, com BANKID e ACCTID fictícios. */
    protected static byte[] ofx(String bancoId, String contaDoBanco, Transacao... transacoes) {
        String corpo = Arrays.stream(transacoes)
                .map(t -> "<STMTTRN><TRNTYPE>OTHER<DTPOSTED>" + t.data().format(DateTimeFormatter.BASIC_ISO_DATE)
                        + "000000[-3:BRT]<TRNAMT>" + t.valor() + "<FITID>" + t.fitid() + "<MEMO>" + t.memo() + "</STMTTRN>\n")
                .collect(Collectors.joining());
        return ("OFXHEADER:100\nDATA:OFXSGML\nVERSION:102\nCHARSET:1252\n\n<OFX><BANKMSGSRSV1><STMTTRNRS><STMTRS><CURDEF>BRL\n"
                + "<BANKACCTFROM><BANKID>" + bancoId + "<ACCTID>" + contaDoBanco + "<ACCTTYPE>CHECKING</BANKACCTFROM>\n<BANKTRANLIST>\n"
                + corpo + "</BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>\n").getBytes(StandardCharsets.UTF_8);
    }

    protected long novaConta(UsuarioDeTeste usuario, String nome, String dataSaldoInicial) {
        return api.idCriado(api.post(usuario, "/api/contas", conta(nome, "CORRENTE", "1000", dataSaldoInicial, "null")));
    }

    protected long idDaCategoria(UsuarioDeTeste usuario, String nome) {
        return jdbc.queryForObject("select id from categorias where usuario_id = ? and nome = ?", Long.class, usuario.id(), nome);
    }

    protected MvcTestResult previa(UsuarioDeTeste usuario, long contaId, String nome, byte[] arquivo) {
        return mvc.post().uri("/api/importacoes/previa").header(HttpHeaders.AUTHORIZATION, "Bearer " + usuario.tokenAcesso())
                .multipart().file(new MockMultipartFile("arquivo", nome, MediaType.APPLICATION_OCTET_STREAM_VALUE, arquivo))
                .param("contaId", String.valueOf(contaId)).exchange();
    }

    /** {@code linhas}: o JSON das escolhas, ex.: {@code [{"linha":2,"categoriaId":null}]}. */
    protected MvcTestResult confirmar(UsuarioDeTeste usuario, long contaId, String nome, byte[] arquivo, String hash, String linhas) {
        String pedido = "{\"contaId\":" + contaId + ",\"hash\":\"" + hash + "\",\"linhas\":" + linhas + "}";
        return mvc.post().uri("/api/importacoes").header(HttpHeaders.AUTHORIZATION, "Bearer " + usuario.tokenAcesso())
                .multipart().file(new MockMultipartFile("arquivo", nome, MediaType.APPLICATION_OCTET_STREAM_VALUE, arquivo))
                .file(new MockMultipartFile("pedido", "", MediaType.APPLICATION_JSON_VALUE, pedido.getBytes(StandardCharsets.UTF_8)))
                .exchange();
    }

    /** Revisa e confirma as linhas dadas, sem categoria; devolve o id da importação. */
    protected long importar(UsuarioDeTeste usuario, long contaId, String nome, byte[] arquivo, int... linhas) {
        String hash = api.json(previa(usuario, contaId, nome, arquivo)).get("hash").asString();
        MvcTestResult resultado = confirmar(usuario, contaId, nome, arquivo, hash, escolhas(linhas));
        assertThat(resultado).hasStatus(201);
        return api.json(resultado).get("importacaoId").asLong();
    }

    protected static String escolhas(int... linhas) {
        return Arrays.stream(linhas).mapToObj(linha -> "{\"linha\":" + linha + ",\"categoriaId\":null}")
                .collect(Collectors.joining(",", "[", "]"));
    }

    protected long lancamentoDaChave(UsuarioDeTeste usuario, String chave) {
        return jdbc.queryForObject("select id from lancamentos where usuario_id = ? and chave_importacao = ?", Long.class,
                usuario.id(), chave);
    }
}
