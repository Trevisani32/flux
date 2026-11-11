package com.flux.importacao.interno;

import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;

/** ADR 0009: excluir o usuário apaga importações, perfis, importados e ignorados, com RESTRICT e CASCADE no caminho. */
class ExclusaoDoUsuarioComImportacoesIT extends ImportacaoBase {

    private static final String PERFIL = "{\"separador\":\"VIRGULA\",\"linhasDeCabecalho\":1,\"colunaData\":1,\"formatoData\":"
            + "\"DD_MM_AAAA\",\"colunaDescricao\":4,\"colunaValor\":2,\"colunaSaidas\":null,\"colunaIdentificador\":3,"
            + "\"separadorDecimal\":\"PONTO\"}";

    @Test
    void excluirOUsuarioApagaAImportacaoDeleESoDele() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        UsuarioDeTeste outro = usuariosDeTeste.criar();
        long horizonte = novaConta(usuario, "Banco Horizonte", "2026-10-01");
        long desfeita = importar(usuario, horizonte, "teste.ofx", ofx("0260", "1", transacao("d1", "2026-11-04", "-1.00", "TESTE")), 1);
        assertThat(api.acao(usuario, "/api/importacoes/{id}/desfazer", desfeita)).hasStatusOk();
        importar(usuario, horizonte, "extrato.ofx", ofx("0260", "1",
                transacao("e1", "2026-11-05", "-6.00", "CAFE"), transacao("e2", "2026-11-06", "-9.90", "TARIFA")), 1, 2);
        assertThat(api.delete(usuario, "/api/lancamentos/{id}", lancamentoDaChave(usuario, "ID:" + horizonte + ":e2"))).hasStatus(204);
        assertThat(api.put(usuario, "/api/importacoes/perfis/{conta}", PERFIL, horizonte)).hasStatusOk();
        long contaDoOutro = novaConta(outro, "Do outro", "2026-10-01");
        importar(outro, contaDoOutro, "extrato.ofx", ofx("0260", "1", transacao("e1", "2026-11-05", "-6.00", "CAFE")), 1);

        assertThat(mvc.delete().uri("/api/usuarios/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + usuario.tokenAcesso())
                .contentType(MediaType.APPLICATION_JSON).content("{\"senhaAtual\":\"" + usuario.senha() + "\"}")).hasStatus(204);

        for (String tabela : new String[] {"lancamentos", "importacoes", "perfis_csv", "contas"}) {
            assertThat(jdbc.queryForObject("select count(*) from " + tabela + " where usuario_id = ?", Integer.class, usuario.id()))
                    .as(tabela).isZero();
        }
        assertThat(jdbc.queryForObject("select count(*) from lancamentos where usuario_id = ?", Integer.class, outro.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from importacoes where usuario_id = ?", Integer.class, outro.id())).isEqualTo(1);
    }
}
