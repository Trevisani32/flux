package com.flux.importacao.interno;

import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Decisão 34: importação e perfil não são uso da conta; os ignorados saem antes dela (LimpezaDaConta). */
class LimpezaDaContaIT extends ImportacaoBase {

    private static final byte[] EXTRATO = ofx("0341", "1234567890", transacao("t1", "2026-11-05", "-9.90", "TARIFA"));
    private static final String PERFIL = "{\"separador\":\"PONTO_E_VIRGULA\",\"linhasDeCabecalho\":1,\"colunaData\":1,"
            + "\"formatoData\":\"DD_MM_AAAA\",\"colunaDescricao\":2,\"colunaValor\":3,\"colunaSaidas\":null,"
            + "\"colunaIdentificador\":null,\"separadorDecimal\":\"VIRGULA\"}";

    @Test
    void contaSoComIgnoradosImportacaoEPerfilSaiInteira() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long itau = novaConta(usuario, "Conta Itaú", "2026-10-01");
        importar(usuario, itau, "itau.ofx", EXTRATO, 1);
        assertThat(api.put(usuario, "/api/importacoes/perfis/{conta}", PERFIL, itau)).hasStatusOk();
        assertThat(api.delete(usuario, "/api/lancamentos/{id}", lancamentoDaChave(usuario, "ID:" + itau + ":t1"))).hasStatus(204);

        assertThat(api.delete(usuario, "/api/contas/{id}", itau)).hasStatus(204);

        for (String tabela : new String[] {"lancamentos", "importacoes", "perfis_csv", "contas"}) {
            assertThat(jdbc.queryForObject("select count(*) from " + tabela + " where usuario_id = ?", Integer.class, usuario.id()))
                    .as(tabela).isZero();
        }
    }

    @Test
    void contaComImportadoQueNaoFoiExcluidoContinuaBloqueada() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long itau = novaConta(usuario, "Conta Itaú", "2026-10-01");
        importar(usuario, itau, "itau.ofx", EXTRATO, 1);

        assertThat(api.delete(usuario, "/api/contas/{id}", itau)).hasStatus(422).bodyJson().extractingPath("uso").isEqualTo("1 lançamento");
        assertThat(jdbc.queryForObject("select count(*) from importacoes where usuario_id = ?", Integer.class, usuario.id())).isEqualTo(1);
    }

    @Test
    void contaSoComImportacaoDesfeitaSaiComORegistro() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long itau = novaConta(usuario, "Conta Itaú", "2026-10-01");
        long importacao = importar(usuario, itau, "itau.ofx", EXTRATO, 1);
        assertThat(api.acao(usuario, "/api/importacoes/{id}/desfazer", importacao)).hasStatusOk();

        assertThat(api.delete(usuario, "/api/contas/{id}", itau)).hasStatus(204);
        assertThat(jdbc.queryForObject("select count(*) from importacoes where usuario_id = ?", Integer.class, usuario.id())).isZero();
    }
}
