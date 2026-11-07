package com.flux.acesso.interno;

import java.time.Duration;
import java.util.UUID;

import com.flux.suporte.IntegracaoBase;
import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

class LimiteDeTentativasIT extends IntegracaoBase {

    @Test
    void bloqueiaLoginMesmoComSenhaCertaEAposQuinzeMinutosLibera() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        for (int tentativa = 0; tentativa < 10; tentativa++) {
            assertThat(entrar(usuario.email(), "senha-errada-123")).hasStatus(401);
        }

        MvcTestResult bloqueado = entrar(usuario.email(), usuario.senha());
        assertThat(bloqueado).hasStatus(429).containsHeader("Retry-After");
        assertThat(Integer.parseInt(bloqueado.getResponse().getHeader("Retry-After"))).isBetween(1, 900);
        assertThat(bloqueado).bodyJson().extractingPath("detail").isEqualTo("Muitas tentativas. Tente de novo em alguns minutos.");
        relogioDeTeste.avancar(Duration.ofMinutes(15));
        assertThat(entrar(usuario.email(), usuario.senha())).hasStatusOk();
    }

    @Test
    void loginsCertosNaoAcumulamNoLimite() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        for (int tentativa = 0; tentativa < 12; tentativa++) {
            assertThat(entrar(usuario.email(), usuario.senha())).hasStatusOk();
        }
    }

    @Test
    void limitaFalhasPorIpESegregaOutroIp() {
        for (int tentativa = 0; tentativa < 50; tentativa++) {
            assertThat(entrarComIp("email-" + tentativa + "-" + UUID.randomUUID() + "@teste.local", "senha", "203.0.113.7"))
                    .hasStatus(401);
        }
        assertThat(entrarComIp("ultimo-" + UUID.randomUUID() + "@teste.local", "senha", "203.0.113.7")).hasStatus(429);
        assertThat(entrarComIp("outro-" + UUID.randomUUID() + "@teste.local", "senha", "203.0.113.8")).hasStatus(401);
    }

    @Test
    void limitaCadastroPorEmail() {
        String email = emailUnico();
        for (int tentativa = 0; tentativa < 3; tentativa++) {
            assertThat(cadastrar(email)).hasStatus(202);
        }
        assertThat(cadastrar(email)).hasStatus(429);
    }

    @Test
    void limitaCadastroPorIp() {
        String ip = "203.0.113.20";
        for (int tentativa = 0; tentativa < 10; tentativa++) {
            assertThat(cadastrarComIp(emailUnico(), ip)).hasStatus(202);
        }
        assertThat(cadastrarComIp(emailUnico(), ip)).hasStatus(429);
    }

    @Test
    void cadastroBarradoPorEmailNaoContaNoLimiteDoIp() {
        String ip = "203.0.113.21";
        String email = emailUnico();
        for (int tentativa = 0; tentativa < 3; tentativa++) {
            assertThat(cadastrarComIp(email, ip)).hasStatus(202);
        }
        assertThat(cadastrarComIp(email, ip)).hasStatus(429);
        for (int tentativa = 0; tentativa < 7; tentativa++) {
            assertThat(cadastrarComIp(emailUnico(), ip)).hasStatus(202);
        }
        assertThat(cadastrarComIp(emailUnico(), ip)).hasStatus(429);
    }

    @Test
    void limitaReenvioEEsqueciPorEmail() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        for (int tentativa = 0; tentativa < 3; tentativa++) {
            assertThat(pedido("/api/auth/reenviar-confirmacao", usuario.email())).hasStatus(202);
        }
        assertThat(pedido("/api/auth/reenviar-confirmacao", usuario.email())).hasStatus(429);
        String ausente = emailUnico();
        for (int tentativa = 0; tentativa < 3; tentativa++) {
            assertThat(pedido("/api/auth/esqueci-a-senha", ausente)).hasStatus(202);
        }
        assertThat(pedido("/api/auth/esqueci-a-senha", ausente)).hasStatus(429);
    }

    @Test
    void limitaRedefinicaoPorIp() {
        for (int tentativa = 0; tentativa < 10; tentativa++) {
            assertThat(redefinir(Tokens.novoValor())).hasStatus(422);
        }
        assertThat(redefinir(Tokens.novoValor())).hasStatus(429);
    }

    @Test
    void limiteDaSenhaAtualTambemBloqueiaTrocaEExclusao() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        for (int tentativa = 0; tentativa < 10; tentativa++) {
            assertThat(mvc.put().uri("/api/usuarios/me/senha").header(HttpHeaders.AUTHORIZATION, bearer(usuario))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"senhaAtual\":\"errada\",\"novaSenha\":\"senha-nova-de-teste-456\"}"))
                    .hasStatus(422);
        }
        assertThat(mvc.put().uri("/api/usuarios/me/senha").header(HttpHeaders.AUTHORIZATION, bearer(usuario))
                .contentType(MediaType.APPLICATION_JSON).content("{\"senhaAtual\":\"" + usuario.senha() + "\",\"novaSenha\":\"senha-nova-de-teste-456\"}"))
                .hasStatus(429);
        assertThat(mvc.post().uri("/api/usuarios/me/email").header(HttpHeaders.AUTHORIZATION, bearer(usuario))
                .contentType(MediaType.APPLICATION_JSON).content("{\"novoEmail\":\"" + emailUnico() + "\",\"senhaAtual\":\"" + usuario.senha() + "\"}"))
                .hasStatus(429);
        assertThat(mvc.delete().uri("/api/usuarios/me").header(HttpHeaders.AUTHORIZATION, bearer(usuario))
                .contentType(MediaType.APPLICATION_JSON).content("{\"senhaAtual\":\"" + usuario.senha() + "\"}"))
                .hasStatus(429).bodyJson().extractingPath("title").isEqualTo("Muitas tentativas");
    }

    private MvcTestResult entrar(String email, String senha) {
        return entrarComIp(email, senha, "127.0.0.1");
    }

    private MvcTestResult entrarComIp(String email, String senha, String ip) {
        return mvc.post().uri("/api/auth/entrar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}")
                .with(requisicao -> { requisicao.setRemoteAddr(ip); return requisicao; }).exchange();
    }

    private MvcTestResult cadastrar(String email) {
        return cadastrarComIp(email, "127.0.0.1");
    }

    private MvcTestResult cadastrarComIp(String email, String ip) {
        return mvc.post().uri("/api/auth/cadastrar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Ana\",\"email\":\"" + email + "\",\"senha\":\"senha-de-teste-123\",\"aceiteDosTermos\":true}")
                .with(requisicao -> { requisicao.setRemoteAddr(ip); return requisicao; }).exchange();
    }

    private MvcTestResult pedido(String rota, String email) {
        return mvc.post().uri(rota).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + email + "\"}").exchange();
    }

    private MvcTestResult redefinir(String token) {
        return mvc.post().uri("/api/auth/redefinir-senha").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"novaSenha\":\"senha-nova-de-teste-456\"}").exchange();
    }

    private static String bearer(UsuarioDeTeste usuario) { return "Bearer " + usuario.tokenAcesso(); }
    private static String emailUnico() { return "limite-" + UUID.randomUUID() + "@teste.local"; }
}
