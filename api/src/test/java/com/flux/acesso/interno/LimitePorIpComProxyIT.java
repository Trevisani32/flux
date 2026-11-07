package com.flux.acesso.interno;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import com.flux.suporte.InfraestruturaDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pelo Tomcat de verdade (RemoteIpValve ligada por server.forward-headers-strategy=native):
 * a requisição vem de 127.0.0.1, um proxy interno confiável, então o IP do cliente é o do
 * X-Forwarded-For. O MockMvc não passa pela válvula.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(InfraestruturaDeTeste.class)
class LimitePorIpComProxyIT {

    @LocalServerPort
    private int porta;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void contaPorIpDoXForwardedForQuandoOProxyEConfiavel() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertThat(esqueci("203.0.113.10")).isEqualTo(202);
        }
        assertThat(esqueci("203.0.113.10")).isEqualTo(429);
        assertThat(esqueci("203.0.113.11")).isEqualTo(202);
    }

    private int esqueci(String ipDoCliente) throws IOException, InterruptedException {
        String corpo = "{\"email\":\"ip-" + UUID.randomUUID() + "@teste.local\"}";
        HttpRequest requisicao = HttpRequest.newBuilder(URI.create("http://localhost:" + porta + "/api/auth/esqueci-a-senha"))
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", ipDoCliente)
                .POST(HttpRequest.BodyPublishers.ofString(corpo))
                .build();
        return http.send(requisicao, HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
