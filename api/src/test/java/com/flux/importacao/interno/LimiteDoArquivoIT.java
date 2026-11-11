package com.flux.importacao.interno;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import com.flux.suporte.InfraestruturaDeTeste;
import com.flux.suporte.UsuariosDeTeste;
import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Decisão 35, com o Tomcat de verdade (o MockMvc não aplica o limite do multipart): acima de 5 MB,
 * 413 com a mensagem em português; pedido que não é multipart, 400; nunca 500.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(InfraestruturaDeTeste.class)
class LimiteDoArquivoIT {

    private static final String FRONTEIRA = "----flux-teste";

    @LocalServerPort
    private int porta;

    @Autowired
    private UsuariosDeTeste usuariosDeTeste;

    private final HttpClient cliente = HttpClient.newHttpClient();

    @Test
    void arquivoAcimaDe5MbRecebe413ComAMensagem() throws Exception {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();

        HttpResponse<String> grande = enviar(usuario, multipart(5 * 1024 * 1024 + 1));
        HttpResponse<String> noLimite = enviar(usuario, multipart(5 * 1024 * 1024));

        assertThat(grande.statusCode()).isEqualTo(413);
        assertThat(grande.body()).contains("\"detail\":\"O arquivo pode ter no máximo 5 MB.\"");
        // No limite, o arquivo passa e chega à revisão (a conta 1 não é do usuário: 422).
        assertThat(noLimite.statusCode()).isEqualTo(422);
    }

    @Test
    void pedidoQueNaoEMultipartRecebe400() throws Exception {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        HttpRequest pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + "/api/importacoes/previa"))
                .header("Authorization", "Bearer " + usuario.tokenAcesso()).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}")).build();

        HttpResponse<String> resposta = cliente.send(pedido, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertThat(resposta.statusCode()).isEqualTo(400);
        assertThat(resposta.body()).contains("\"detail\":\"Envie o arquivo do extrato.\"");
    }

    private HttpResponse<String> enviar(UsuarioDeTeste usuario, byte[] corpo) throws IOException, InterruptedException {
        HttpRequest pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + "/api/importacoes/previa"))
                .header("Authorization", "Bearer " + usuario.tokenAcesso())
                .header("Content-Type", "multipart/form-data; boundary=" + FRONTEIRA)
                .POST(HttpRequest.BodyPublishers.ofByteArray(corpo)).build();
        return cliente.send(pedido, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /** O campo contaId e um arquivo fictício de {@code tamanho} bytes (só letras "x"). */
    private static byte[] multipart(int tamanho) throws IOException {
        ByteArrayOutputStream corpo = new ByteArrayOutputStream();
        corpo.writeBytes(("--" + FRONTEIRA + "\r\nContent-Disposition: form-data; name=\"contaId\"\r\n\r\n1\r\n--" + FRONTEIRA
                + "\r\nContent-Disposition: form-data; name=\"arquivo\"; filename=\"grande.csv\"\r\nContent-Type: text/csv\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        byte[] arquivo = new byte[tamanho];
        Arrays.fill(arquivo, (byte) 'x');
        corpo.writeBytes(arquivo);
        corpo.writeBytes(("\r\n--" + FRONTEIRA + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return corpo.toByteArray();
    }
}
