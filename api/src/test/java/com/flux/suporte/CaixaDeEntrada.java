package com.flux.suporte;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.awaitility.Awaitility;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Lê os e-mails que chegaram ao Mailpit dos testes. Os envios rodam no executor
 * assíncrono do Spring; por isso toda leitura espera a fila esvaziar antes.
 */
public class CaixaDeEntrada {

    private static final Pattern TOKEN = Pattern.compile("#token=([A-Za-z0-9_-]{43})");

    private final HttpClient http = HttpClient.newHttpClient();
    private final ContainerMailpit mailpit;
    private final JsonMapper jsonMapper;
    private final ThreadPoolTaskExecutor executor;

    public CaixaDeEntrada(ContainerMailpit mailpit, JsonMapper jsonMapper, ThreadPoolTaskExecutor executor) {
        this.mailpit = mailpit;
        this.jsonMapper = jsonMapper;
        this.executor = executor;
    }

    /** Espera até 15 s o executor assíncrono ficar sem tarefas em andamento ou na fila. */
    public void aguardarEnvios() {
        Awaitility.await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(50))
                .until(() -> executor.getActiveCount() == 0 && executor.getThreadPoolExecutor().getQueue().isEmpty());
    }

    /** E-mails para o endereço exato, do mais novo para o mais antigo. */
    public List<EmailRecebido> mensagensPara(String email) {
        aguardarEnvios();
        String consulta = URLEncoder.encode("to:\"" + email + "\"", UTF_8);
        JsonNode busca = get("/api/v1/search?query=" + consulta);
        List<EmailRecebido> emails = new ArrayList<>();
        for (JsonNode resumo : busca.get("messages")) {
            boolean paraEsteEndereco = false;
            for (JsonNode destinatario : resumo.get("To")) {
                paraEsteEndereco |= destinatario.get("Address").asString().equalsIgnoreCase(email);
            }
            if (paraEsteEndereco) {
                JsonNode mensagem = get("/api/v1/message/" + resumo.get("ID").asString());
                emails.add(new EmailRecebido(mensagem.get("Subject").asString(),
                        mensagem.get("From").get("Name").asString(), mensagem.get("From").get("Address").asString(),
                        mensagem.get("Text").asString(), mensagem.get("HTML").asString()));
            }
        }
        return emails;
    }

    public List<EmailRecebido> mensagensPara(String email, String assunto) {
        return mensagensPara(email).stream().filter(recebido -> recebido.assunto().equals(assunto)).toList();
    }

    /** O token do primeiro link "#token=..." do texto simples. */
    public static String token(EmailRecebido email) {
        Matcher encontrado = TOKEN.matcher(email.texto());
        if (!encontrado.find()) {
            throw new AssertionError("O e-mail \"" + email.assunto() + "\" não tem link com token");
        }
        return encontrado.group(1);
    }

    private JsonNode get(String caminho) {
        try {
            HttpResponse<String> resposta = http.send(
                    HttpRequest.newBuilder(URI.create(mailpit.urlDaApi() + caminho)).GET().build(),
                    HttpResponse.BodyHandlers.ofString(UTF_8));
            if (resposta.statusCode() != 200) {
                throw new IllegalStateException("Mailpit respondeu " + resposta.statusCode() + " em " + caminho);
            }
            return jsonMapper.readTree(resposta.body());
        } catch (IOException ex) {
            throw new IllegalStateException("Não foi possível ler o Mailpit", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Leitura do Mailpit interrompida", ex);
        }
    }

    public record EmailRecebido(String assunto, String nomeDoRemetente, String remetente, String texto,
                                String html) {
    }
}
