package com.flux.categorias.interno;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.flux.acesso.UsuarioCriado;
import com.flux.acesso.interno.PropriedadesAcesso;
import com.flux.acesso.interno.UsuarioInicialSeeder;
import com.flux.acesso.interno.UsuarioService;
import com.flux.suporte.IntegracaoBase;
import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Todo usuário novo recebe as 24 categorias padrão, pelos dois caminhos que gravam um usuário. */
class CategoriasPadraoIT extends IntegracaoBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private CategoriasPadrao categoriasPadrao;

    @Test
    void usuarioCriadoPeloUsuarioServiceRecebeAArvorePadrao() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();

        assertThat(arvoreNoBanco(usuario.id())).isEqualTo(arvoreDaLista());
        assertThat(mvc.get().uri("/api/categorias").header("Authorization", "Bearer " + usuario.tokenAcesso()))
                .hasStatusOk().bodyJson().extractingPath("despesas.ativas.length()").isEqualTo(10);
    }

    @Test
    void cadastroPublicoRecebeAsCategoriasAntesDeConfirmarOEmail() {
        String email = "cadastro-" + UUID.randomUUID() + "@teste.local";
        assertThat(mvc.post().uri("/api/auth/cadastrar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Ana\",\"email\":\"" + email + "\",\"senha\":\"senha-de-teste-123\",\"aceiteDosTermos\":true}"))
                .hasStatus(202);

        Long usuarioId = jdbc.queryForObject("select id from usuarios where email = ? and email_confirmado_em is null",
                Long.class, email);
        assertThat(arvoreNoBanco(usuarioId)).isEqualTo(arvoreDaLista());
    }

    @Test
    void cadastroRepetidoDoMesmoEmailNaoDuplicaAsCategorias() {
        String email = "repetido-" + UUID.randomUUID() + "@teste.local";
        String corpo = "{\"nome\":\"Ana\",\"email\":\"" + email + "\",\"senha\":\"senha-de-teste-123\",\"aceiteDosTermos\":true}";
        assertThat(mvc.post().uri("/api/auth/cadastrar").contentType(MediaType.APPLICATION_JSON).content(corpo)).hasStatus(202);
        assertThat(mvc.post().uri("/api/auth/cadastrar").contentType(MediaType.APPLICATION_JSON).content(corpo)).hasStatus(202);

        Long usuarioId = jdbc.queryForObject("select id from usuarios where email = ?", Long.class, email);
        assertThat(jdbc.queryForObject("select count(*) from categorias where usuario_id = ?", Integer.class, usuarioId))
                .isEqualTo(24);
    }

    @Test
    void usuarioInicialRecebeAsCategoriasUmaVezSo() throws Exception {
        String email = "inicial-" + UUID.randomUUID() + "@teste.local";
        UsuarioInicialSeeder seeder = new UsuarioInicialSeeder(new PropriedadesAcesso(
                new PropriedadesAcesso.PropriedadesJwt("segredo-de-teste-com-mais-de-trinta-e-dois-bytes", Duration.ofMinutes(15)),
                new PropriedadesAcesso.PropriedadesRenovacao(Duration.ofDays(30), true),
                new PropriedadesAcesso.PropriedadesUsuarioInicial(email, "senha-de-seed-123", "Inicial")), usuarioService);

        seeder.run(new DefaultApplicationArguments());
        seeder.run(new DefaultApplicationArguments());

        Long usuarioId = jdbc.queryForObject("select id from usuarios where email = ?", Long.class, email);
        assertThat(arvoreNoBanco(usuarioId)).isEqualTo(arvoreDaLista());
    }

    @Test
    void ouvinteExigeATransacaoDeQuemGravouOUsuario() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();

        assertThatThrownBy(() -> categoriasPadrao.aoCriarUsuario(new UsuarioCriado(usuario.id())))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(jdbc.queryForObject("select count(*) from categorias where usuario_id = ?", Integer.class, usuario.id()))
                .isEqualTo(24);
    }

    private List<String> arvoreNoBanco(Long usuarioId) {
        List<Map<String, Object>> linhas = jdbc.queryForList("select c.natureza, c.nome, c.nome_chave, m.nome as mae "
                + "from categorias c left join categorias m on m.id = c.categoria_mae_id "
                + "where c.usuario_id = ? and c.arquivada_em is null", usuarioId);
        return linhas.stream()
                .map(linha -> linha.get("natureza") + "/" + (linha.get("mae") == null ? "" : linha.get("mae") + "/")
                        + linha.get("nome") + "=" + linha.get("nome_chave"))
                .sorted().toList();
    }

    private static List<String> arvoreDaLista() {
        List<String> arvore = new ArrayList<>();
        for (CategoriasPadrao.Padrao padrao : CategoriasPadrao.LISTA) {
            arvore.add(padrao.natureza() + "/" + padrao.nome() + "=" + com.flux.compartilhado.texto.TextoComparavel.chave(padrao.nome()));
            padrao.subcategorias().forEach(sub -> arvore.add(padrao.natureza() + "/" + padrao.nome() + "/" + sub
                    + "=" + com.flux.compartilhado.texto.TextoComparavel.chave(sub)));
        }
        return arvore.stream().sorted().toList();
    }
}
