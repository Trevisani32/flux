package com.flux.importacao.interno;

import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** ADR 0007: B não importa na conta de A, não vê nem desfaz a importação de A e não mexe no perfil nem nos excluídos de A. */
class IsolamentoDaImportacaoIT extends ImportacaoBase {

    private static final byte[] EXTRATO = ofx("0260", "1", transacao("a1", "2026-11-05", "-6.00", "CAFE"));
    private static final String PERFIL = "{\"separador\":\"VIRGULA\",\"linhasDeCabecalho\":1,\"colunaData\":1,\"formatoData\":"
            + "\"DD_MM_AAAA\",\"colunaDescricao\":4,\"colunaValor\":2,\"colunaSaidas\":null,\"colunaIdentificador\":3,"
            + "\"separadorDecimal\":\"PONTO\"}";

    @Test
    void usuarioBNaoAlcancaNadaDaImportacaoDeA() {
        UsuarioDeTeste a = usuariosDeTeste.criar();
        UsuarioDeTeste b = usuariosDeTeste.criar();
        long contaDeA = novaConta(a, "Banco Horizonte", "2026-10-01");
        long importacaoDeA = importar(a, contaDeA, "extrato.ofx", EXTRATO, 1);
        long lancamentoDeA = lancamentoDaChave(a, "ID:" + contaDeA + ":a1");
        assertThat(api.put(a, "/api/importacoes/perfis/{conta}", PERFIL, contaDeA)).hasStatusOk();
        assertThat(api.delete(a, "/api/lancamentos/{id}", lancamentoDeA)).hasStatus(204);

        assertThat(previa(b, contaDeA, "extrato.ofx", EXTRATO)).hasStatus(422).bodyJson().extractingPath("detail")
                .isEqualTo("Conta não encontrada.");
        // Com o hash certo (B consegue calcular o do arquivo), a confirmação chega à conta e para nela.
        assertThat(confirmar(b, contaDeA, "extrato.ofx", EXTRATO, Chaves.sha256(EXTRATO), escolhas(1))).hasStatus(422).bodyJson()
                .extractingPath("detail").isEqualTo("Conta não encontrada.");
        assertThat(jdbc.queryForObject("select count(*) from importacoes where usuario_id = ?", Integer.class, b.id())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from lancamentos where usuario_id = ?", Integer.class, b.id())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from lancamentos where conta_id = ?", Integer.class, contaDeA)).isEqualTo(1);
        assertThat(api.get(b, "/api/importacoes")).bodyJson().extractingPath("$.length()").isEqualTo(0);
        assertThat(api.get(b, "/api/importacoes/{id}/excluidos", importacaoDeA)).hasStatus(404).bodyJson().extractingPath("detail")
                .isEqualTo("Importação não encontrada.");
        assertThat(api.acao(b, "/api/importacoes/{id}/desfazer", importacaoDeA)).hasStatus(404);
        assertThat(api.get(b, "/api/importacoes/perfis/{conta}", contaDeA)).hasStatus(404).bodyJson().extractingPath("detail")
                .isEqualTo("Conta não encontrada.");
        assertThat(api.put(b, "/api/importacoes/perfis/{conta}", PERFIL, contaDeA)).hasStatus(404);
        assertThat(api.post(b, "/api/lancamentos/{id}/restaurar", "{}", lancamentoDeA)).hasStatus(404);

        assertThat(jdbc.queryForObject("select desfeita_em is null from importacoes where id = ?", Boolean.class, importacaoDeA)).isTrue();
        assertThat(jdbc.queryForObject("select ignorado_em is not null from lancamentos where id = ?", Boolean.class, lancamentoDeA))
                .isTrue();
    }
}
