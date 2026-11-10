package com.flux.suporte;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** Pedidos autenticados à API nos testes de integração, e leitura do corpo da resposta. */
public class ApiDeTeste {

    private final MockMvcTester mvc;
    private final JsonMapper jsonMapper;

    public ApiDeTeste(MockMvcTester mvc, JsonMapper jsonMapper) {
        this.mvc = mvc;
        this.jsonMapper = jsonMapper;
    }

    public MvcTestResult get(UsuarioDeTeste usuario, String uri, Object... variaveis) {
        return mvc.get().uri(uri, variaveis).header(HttpHeaders.AUTHORIZATION, bearer(usuario)).exchange();
    }

    public MvcTestResult post(UsuarioDeTeste usuario, String uri, String corpo, Object... variaveis) {
        return mvc.post().uri(uri, variaveis).header(HttpHeaders.AUTHORIZATION, bearer(usuario))
                .contentType(MediaType.APPLICATION_JSON).content(corpo).exchange();
    }

    /** POST sem corpo (arquivar, reativar). */
    public MvcTestResult acao(UsuarioDeTeste usuario, String uri, Object... variaveis) {
        return mvc.post().uri(uri, variaveis).header(HttpHeaders.AUTHORIZATION, bearer(usuario)).exchange();
    }

    public MvcTestResult put(UsuarioDeTeste usuario, String uri, String corpo, Object... variaveis) {
        return mvc.put().uri(uri, variaveis).header(HttpHeaders.AUTHORIZATION, bearer(usuario))
                .contentType(MediaType.APPLICATION_JSON).content(corpo).exchange();
    }

    public MvcTestResult delete(UsuarioDeTeste usuario, String uri, Object... variaveis) {
        return mvc.delete().uri(uri, variaveis).header(HttpHeaders.AUTHORIZATION, bearer(usuario)).exchange();
    }

    /** O corpo como veio (para conferir a escala dos valores, ex.: "4218.40"). */
    public static String texto(MvcTestResult resposta) {
        try {
            return resposta.getResponse().getContentAsString(StandardCharsets.UTF_8);
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public JsonNode json(MvcTestResult resposta) {
        return jsonMapper.readTree(texto(resposta));
    }

    /** Confere o 201 e devolve o id criado. */
    public long idCriado(MvcTestResult resposta) {
        assertThat(resposta).hasStatus(201);
        return json(resposta).get("id").asLong();
    }

    /** Corpo de POST/PUT /api/contas. {@code saldo} e {@code cor} vão como JSON cru ("4218.40", "\"AZUL\"", "null"). */
    public static String conta(String nome, String tipo, String saldo, String data, String cor) {
        return "{\"nome\":\"" + nome + "\",\"tipo\":\"" + tipo + "\",\"saldoInicial\":" + saldo
                + ",\"dataSaldoInicial\":\"" + data + "\",\"cor\":" + cor + "}";
    }

    /** Corpo de POST /api/categorias ({@code maeId} nulo = principal). */
    public static String categoria(String nome, String natureza, Long maeId) {
        return "{\"nome\":\"" + nome + "\",\"natureza\":\"" + natureza + "\",\"categoriaMaeId\":" + maeId + "}";
    }

    /** Corpo de PUT /api/categorias/{id}. */
    public static String edicaoDeCategoria(String nome, Long maeId) {
        return "{\"nome\":\"" + nome + "\",\"categoriaMaeId\":" + maeId + "}";
    }

    /** Confere um 400 de validação com um campo só. */
    public static void assertCampo(MvcTestResult resposta, String campo, String mensagem) {
        assertThat(resposta).hasStatus(400).bodyJson().extractingPath("campos.length()").isEqualTo(1);
        assertThat(resposta).bodyJson().extractingPath("campos[0].campo").isEqualTo(campo);
        assertThat(resposta).bodyJson().extractingPath("campos[0].mensagem").isEqualTo(mensagem);
    }

    private static String bearer(UsuarioDeTeste usuario) {
        return "Bearer " + usuario.tokenAcesso();
    }
}
