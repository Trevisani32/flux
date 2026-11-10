package com.flux.lancamentos.interno;

import com.flux.suporte.IntegracaoBase;
import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import static com.flux.suporte.ApiDeTeste.conta;
import static com.flux.suporte.ApiDeTeste.lancamento;
import static com.flux.suporte.ApiDeTeste.transferencia;
import static org.assertj.core.api.Assertions.assertThat;

/** ADR 0009: excluir a conta de usuário apaga tudo dele, com as FKs RESTRICT de lançamentos no caminho. */
class ExclusaoDoUsuarioComLancamentosIT extends IntegracaoBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void excluirOUsuarioApagaLancamentosETransferenciasDeleESoDele() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        UsuarioDeTeste outro = usuariosDeTeste.criar();
        long horizonte = api.idCriado(api.post(usuario, "/api/contas", conta("Banco Horizonte", "CORRENTE", "0", "2026-10-01", "null")));
        long poupanca = api.idCriado(api.post(usuario, "/api/contas", conta("Poupança", "POUPANCA", "0", "2026-10-01", "null")));
        long mercado = jdbc.queryForObject("select id from categorias where usuario_id = ? and nome = 'Mercado'", Long.class, usuario.id());
        api.idCriado(api.post(usuario, "/api/lancamentos", lancamento("DESPESA", "10", "2026-10-06", "Mercado", horizonte, mercado)));
        api.idCriado(api.post(usuario, "/api/transferencias", transferencia("5", "2026-10-06", horizonte, poupanca, null)));
        long contaDoOutro = api.idCriado(api.post(outro, "/api/contas", conta("Do outro", "CORRENTE", "0", "2026-10-01", "null")));
        api.idCriado(api.post(outro, "/api/lancamentos", lancamento("DESPESA", "1", "2026-10-06", "Do outro", contaDoOutro, null)));

        assertThat(mvc.delete().uri("/api/usuarios/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + usuario.tokenAcesso())
                .contentType(MediaType.APPLICATION_JSON).content("{\"senhaAtual\":\"" + usuario.senha() + "\"}")).hasStatus(204);

        for (String tabela : new String[] {"lancamentos", "transferencias", "contas", "categorias"}) {
            assertThat(jdbc.queryForObject("select count(*) from " + tabela + " where usuario_id = ?", Integer.class, usuario.id()))
                    .as(tabela).isZero();
        }
        assertThat(jdbc.queryForObject("select count(*) from lancamentos where usuario_id = ?", Integer.class, outro.id())).isEqualTo(1);
    }
}
