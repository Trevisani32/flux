package com.flux.importacao.interno;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.stream.Collectors;

import com.flux.suporte.CartoesBase;
import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

/** Apoio das integrações da importação de fatura, sempre com dados fictícios em memória. */
abstract class FaturaBase extends CartoesBase {

    protected static final String NUBANK_FATURA = "{\"separador\":\"VIRGULA\",\"linhasDeCabecalho\":1,\"colunaData\":1,"
            + "\"formatoData\":\"AAAA_MM_DD\",\"colunaDescricao\":2,\"colunaValor\":3,\"colunaSaidas\":null,\"colunaIdentificador\":null,"
            + "\"separadorDecimal\":\"PONTO\",\"comprasPositivas\":true}";

    protected LocalDate hoje() {
        return LocalDate.now(relogioDeTeste);
    }

    protected YearMonth m() {
        return YearMonth.from(hoje()).minusMonths(2);
    }

    protected static String dia(YearMonth mes, int dia) {
        return mes.atDay(dia).toString();
    }

    protected record LinhaDaFatura(String data, String titulo, String valor) {
    }

    protected static LinhaDaFatura linha(String data, String titulo, String valor) {
        return new LinhaDaFatura(data, titulo, valor);
    }

    protected static byte[] csvDoNubank(LinhaDaFatura... linhas) {
        return Arrays.stream(linhas).map(linha -> linha.data() + "," + linha.titulo() + "," + linha.valor() + "\n")
                .collect(Collectors.joining("", "date,title,amount\n", "")).getBytes(StandardCharsets.UTF_8);
    }

    protected static byte[] ofxDeCartao(LinhaDaFatura... linhas) {
        String corpo = Arrays.stream(linhas)
                .map(linha -> "<STMTTRN><TRNTYPE>DEBIT<DTPOSTED>" + linha.data().replace("-", "") + "<TRNAMT>" + linha.valor()
                        + "<MEMO>" + linha.titulo() + "</STMTTRN>\n")
                .collect(Collectors.joining());
        return ("OFXHEADER:100\nDATA:OFXSGML\nVERSION:102\n\n<OFX><CREDITCARDMSGSRSV1><CCSTMTTRNRS><CCSTMTRS><CURDEF>BRL\n"
                + "<CCACCTFROM><ACCTID>0000</CCACCTFROM>\n<BANKTRANLIST>\n" + corpo
                + "</BANKTRANLIST></CCSTMTRS></CCSTMTTRNRS></CREDITCARDMSGSRSV1></OFX>\n").getBytes(StandardCharsets.UTF_8);
    }

    protected void perfilDoNubank(UsuarioDeTeste usuario, long cartaoId) {
        assertThat(api.put(usuario, "/api/importacoes/perfis/cartao/{id}", NUBANK_FATURA, cartaoId)).hasStatusOk();
    }

    protected MvcTestResult previaNoCartao(UsuarioDeTeste usuario, long cartaoId, String fatura, String nome, byte[] arquivo) {
        var pedido = mvc.post().uri("/api/importacoes/previa").header(HttpHeaders.AUTHORIZATION, "Bearer " + usuario.tokenAcesso())
                .multipart().file(new MockMultipartFile("arquivo", nome, MediaType.APPLICATION_OCTET_STREAM_VALUE, arquivo))
                .param("cartaoId", String.valueOf(cartaoId));
        return fatura == null ? pedido.exchange() : pedido.param("fatura", fatura).exchange();
    }

    protected MvcTestResult confirmarNoCartao(UsuarioDeTeste usuario, long cartaoId, String fatura, String nome, byte[] arquivo, String hash,
                                              String linhas) {
        String pedido = "{\"cartaoId\":" + cartaoId + ",\"fatura\":\"" + fatura + "\",\"hash\":\"" + hash + "\",\"linhas\":" + linhas + "}";
        return mvc.post().uri("/api/importacoes").header(HttpHeaders.AUTHORIZATION, "Bearer " + usuario.tokenAcesso())
                .multipart().file(new MockMultipartFile("arquivo", nome, MediaType.APPLICATION_OCTET_STREAM_VALUE, arquivo))
                .file(new MockMultipartFile("pedido", "", MediaType.APPLICATION_JSON_VALUE, pedido.getBytes(StandardCharsets.UTF_8)))
                .exchange();
    }

    protected long importarNoCartao(UsuarioDeTeste usuario, long cartaoId, String nome, byte[] arquivo, int... linhas) {
        var previa = api.json(previaNoCartao(usuario, cartaoId, null, nome, arquivo));
        MvcTestResult resultado = confirmarNoCartao(usuario, cartaoId, previa.get("fatura").get("mes").asString(), nome, arquivo,
                previa.get("hash").asString(), escolhas(linhas));
        assertThat(resultado).hasStatus(201);
        return api.json(resultado).get("importacaoId").asLong();
    }

    protected static String escolhas(int... linhas) {
        return Arrays.stream(linhas).mapToObj(linha -> "{\"linha\":" + linha + ",\"categoriaId\":null}")
                .collect(Collectors.joining(",", "[", "]"));
    }
}
