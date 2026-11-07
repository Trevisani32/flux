package com.flux.acesso.interno;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import com.flux.suporte.CaixaDeEntrada;
import com.flux.suporte.IntegracaoBase;
import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

class CadastroIT extends IntegracaoBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void cadastroCriaContaNaoConfirmadaComTermosETokenEEnviaEmail() {
        String email = emailUnico();

        assertThat(cadastrar("Ana", email, "senha-de-teste-123", true)).hasStatus(202);

        assertThat(jdbc.queryForObject("select email_confirmado_em is null from usuarios where email = ?", Boolean.class, email)).isTrue();
        assertThat(jdbc.queryForObject("select termos_versao from usuarios where email = ?", String.class, email))
                .isEqualTo(Termos.VERSAO);
        assertThat(jdbc.queryForObject("select termos_aceitos_em is not null from usuarios where email = ?", Boolean.class, email)).isTrue();
        var recebido = caixaDeEntrada.mensagensPara(email, "Confirme seu e-mail no Flux");
        assertThat(recebido).hasSize(1);
        assertThat(recebido.getFirst().nomeDoRemetente()).isEqualTo("Flux");
        assertThat(recebido.getFirst().remetente()).isEqualTo("nao-responda@teste.local");
        String token = CaixaDeEntrada.token(recebido.getFirst());
        assertThat(recebido.getFirst().texto()).contains("http://localhost:4200/confirmar-email#token=" + token).doesNotContain("senha-de-teste-123");
        assertThat(recebido.getFirst().html()).contains("Olá, Ana.").doesNotContain("senha-de-teste-123");
        Long tokenId = jdbc.queryForObject("select id from tokens_email where usuario_id = (select id from usuarios where email = ?) "
                + "and finalidade = 'CONFIRMACAO'", Long.class, email);
        assertThat(jdbc.queryForObject("select token_hash from tokens_email where id = ?", String.class, tokenId))
                .isEqualTo(Tokens.hash(token));
        assertThat(jdbc.queryForObject("select expira_em = criado_em + interval '24 hours' from tokens_email where id = ?", Boolean.class, tokenId)).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from event_publication where serialized_event = ?", Integer.class,
                "{\"tokenId\":" + tokenId + "}")).isZero();
    }

    @Test
    void senhaCertaAntesDaConfirmacaoDaEmailNaoConfirmado() {
        String email = emailUnico();
        cadastrar("Ana", email, "senha-de-teste-123", true);

        assertThat(entrar(email, "senha-de-teste-123")).hasStatus(403)
                .bodyJson().extractingPath("title").isEqualTo("E-mail não confirmado");
        assertThat(entrar(email, "senha-de-teste-123")).bodyJson().extractingPath("detail")
                .isEqualTo("Confirme seu e-mail para entrar.");
        assertThat(entrar(email, "senha-de-teste-123")).bodyJson().extractingPath("codigo")
                .isEqualTo("EMAIL_NAO_CONFIRMADO");
    }

    @Test
    void senhaErradaAntesDaConfirmacaoDa401() {
        String email = emailUnico();
        cadastrar("Ana", email, "senha-de-teste-123", true);

        assertThat(entrar(email, "senha-errada-123")).hasStatus(401);
    }

    @Test
    void confirmarCadastroPermiteEntrarEInvalidaOMesmoToken() {
        String email = emailUnico();
        cadastrar("Ana", email, "senha-de-teste-123", true);
        String token = tokenDeConfirmacao(email);

        MvcTestResult confirmacao = confirmar(token);
        assertThat(confirmacao).hasStatusOk().bodyJson().extractingPath("tipo").isEqualTo("CADASTRO");
        assertThat(confirmacao).bodyJson().extractingPath("email").isEqualTo(email);
        assertThat(confirmar(token)).hasStatus(422).bodyJson().extractingPath("codigo").isEqualTo("LINK_INVALIDO");
        assertThat(entrar(email, "senha-de-teste-123")).hasStatusOk();
    }

    @Test
    void normalizaEmailDoCadastro() {
        String email = "ana-" + UUID.randomUUID() + "@teste.local";

        assertThat(cadastrar("Ana", "  " + email.toUpperCase() + " ", "senha-de-teste-123", true)).hasStatus(202);

        assertThat(jdbc.queryForObject("select count(*) from usuarios where email = ?", Integer.class, email)).isEqualTo(1);
    }

    @Test
    void cadastroComEmailConfirmadoEnviaAvisoSemMudarSenhaOuCriarToken() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        int tokensAntes = jdbc.queryForObject("select count(*) from tokens_email where usuario_id = ?", Integer.class, usuario.id());

        assertThat(cadastrar("Outra Pessoa", usuario.email(), "outra-senha-qualquer-789", true)).hasStatus(202);

        assertThat(caixaDeEntrada.mensagensPara(usuario.email(), "Tentativa de cadastro com o seu e-mail")).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from tokens_email where usuario_id = ?", Integer.class, usuario.id()))
                .isEqualTo(tokensAntes);
        assertThat(entrar(usuario.email(), usuario.senha())).hasStatusOk();
    }

    @Test
    void recadastroNaoConfirmadoMantemDadosOriginaisESubstituiLink() {
        String email = emailUnico();
        cadastrar("Primeira Pessoa", email, "senha-original-123", true);
        Long id = jdbc.queryForObject("select id from usuarios where email = ?", Long.class, email);
        String primeiro = tokenDeConfirmacao(email);

        assertThat(cadastrar("Outra Pessoa", email, "outra-senha-qualquer-789", true)).hasStatus(202);
        var emails = caixaDeEntrada.mensagensPara(email, "Confirme seu e-mail no Flux");
        assertThat(emails).hasSize(2);
        String segundo = CaixaDeEntrada.token(emails.getFirst());
        assertThat(jdbc.queryForObject("select id from usuarios where email = ?", Long.class, email)).isEqualTo(id);
        assertThat(jdbc.queryForObject("select nome from usuarios where email = ?", String.class, email)).isEqualTo("Primeira Pessoa");
        assertThat(confirmar(primeiro)).hasStatus(422).bodyJson().extractingPath("codigo").isEqualTo("LINK_INVALIDO");
        assertThat(confirmar(segundo)).hasStatusOk();
        assertThat(entrar(email, "senha-original-123")).hasStatusOk();
        assertThat(entrar(email, "outra-senha-qualquer-789")).hasStatus(401);
    }

    @Test
    void validaCamposObrigatoriosETamanhosDoCadastro() {
        assertThat(cadastrar("", emailUnico(), "senha-de-teste-123", true)).hasStatus(400)
                .bodyJson().extractingPath("campos[0].mensagem").isEqualTo("Informe o nome.");
        assertThat(cadastrar("a".repeat(121), emailUnico(), "senha-de-teste-123", true)).hasStatus(400);
        assertThat(cadastrar("Ana", "invalido", "senha-de-teste-123", true)).hasStatus(400)
                .bodyJson().extractingPath("campos[0].mensagem").isEqualTo("Informe um e-mail válido.");
        assertThat(cadastrar("Ana", emailUnico(), "123456789", true)).hasStatus(400)
                .bodyJson().extractingPath("campos[0].mensagem").isEqualTo("A senha precisa ter entre 10 e 72 caracteres.");
        MvcTestResult semAceite = cadastrar("Ana", emailUnico(), "senha-de-teste-123", false);
        assertThat(semAceite).hasStatus(400).bodyJson().extractingPath("campos.length()").isEqualTo(1);
        assertThat(semAceite).bodyJson().extractingPath("campos[0].mensagem")
                .isEqualTo("Aceite os termos de uso e a política de privacidade para continuar.");
        MvcTestResult aceiteAusente = cadastrarSemAceite("Ana", emailUnico(), "senha-de-teste-123");
        assertThat(aceiteAusente).hasStatus(400).bodyJson().extractingPath("campos.length()").isEqualTo(1);
        assertThat(aceiteAusente).bodyJson().extractingPath("campos[0].mensagem")
                .isEqualTo("Aceite os termos de uso e a política de privacidade para continuar.");
    }

    @Test
    void rejeitaSenhaComMaisDeSetentaEDoisBytes() {
        assertThat(cadastrar("Ana", emailUnico(), "ç".repeat(40), true)).hasStatus(422)
                .bodyJson().extractingPath("detail").isEqualTo("A senha é longa demais.");
    }

    @Test
    void doisCadastrosSimultaneosDoMesmoEmailDeixamUmUsuario() throws Exception {
        String email = emailUnico();
        CountDownLatch inicio = new CountDownLatch(1);
        AtomicReference<MvcTestResult> primeiro = new AtomicReference<>();
        AtomicReference<MvcTestResult> segundo = new AtomicReference<>();
        Thread t1 = Thread.ofVirtual().start(() -> primeiro.set(cadastrarDepoisDaBarreira(inicio, email)));
        Thread t2 = Thread.ofVirtual().start(() -> segundo.set(cadastrarDepoisDaBarreira(inicio, email)));
        inicio.countDown();
        t1.join();
        t2.join();

        assertThat(primeiro.get()).hasStatus(202);
        assertThat(segundo.get()).hasStatus(202);
        assertThat(jdbc.queryForObject("select count(*) from usuarios where email = ?", Integer.class, email)).isEqualTo(1);
    }

    private MvcTestResult cadastrarDepoisDaBarreira(CountDownLatch inicio, String email) {
        try {
            inicio.await();
            return cadastrar("Ana", email, "senha-de-teste-123", true);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError(ex);
        }
    }

    private MvcTestResult cadastrar(String nome, String email, String senha, boolean aceite) {
        return mvc.post().uri("/api/auth/cadastrar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"" + nome + "\",\"email\":\"" + email + "\",\"senha\":\"" + senha
                        + "\",\"aceiteDosTermos\":" + aceite + "}").exchange();
    }

    private MvcTestResult cadastrarSemAceite(String nome, String email, String senha) {
        return mvc.post().uri("/api/auth/cadastrar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"" + nome + "\",\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}").exchange();
    }

    private MvcTestResult entrar(String email, String senha) {
        return mvc.post().uri("/api/auth/entrar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}").exchange();
    }

    private MvcTestResult confirmar(String token) {
        return mvc.post().uri("/api/auth/confirmar-email").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}").exchange();
    }

    private String tokenDeConfirmacao(String email) {
        return CaixaDeEntrada.token(caixaDeEntrada.mensagensPara(email, "Confirme seu e-mail no Flux").getFirst());
    }

    private static String emailUnico() {
        return "cadastro-" + UUID.randomUUID() + "@teste.local";
    }
}
