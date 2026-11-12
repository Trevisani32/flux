package com.flux.lancamentos.interno;

import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A conta e o usuário com transferências convertidas (Fatia 04c): o lado convertido é uso da conta, como
 * a transferência manual; depois de desfeita, a conta sai quando os lançamentos dela saem; e excluir o
 * usuário apaga tudo dele (ADR 0009), sem esbarrar no RESTRICT dos importados.
 */
class ExclusaoComConversaoIT extends ConversaoBase {

    @Test
    void contaComLadoConvertidoNaoSeExcluiEDepoisDeDesfazerSai() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novaConta(usuario, "Banco Horizonte");
        long conjunta = novaConta(usuario, "Conta conjunta");
        long ted = importado(usuario, horizonte, "2026-11-02", "-1000.00", "TED");
        long recebida = importado(usuario, conjunta, "2026-11-03", "1000.00", "RECEBIDA");
        long id = converterComPar(usuario, ted, recebida, null);

        assertThat(api.delete(usuario, "/api/contas/{id}", conjunta)).hasStatus(422).bodyJson().extractingPath("uso")
                .isEqualTo("1 transferência");
        assertThat(api.acao(usuario, "/api/transferencias/{id}/desfazer", id)).hasStatusOk();
        assertThat(api.delete(usuario, "/api/contas/{id}", conjunta)).hasStatus(422).bodyJson().extractingPath("uso")
                .isEqualTo("1 lançamento");
        assertThat(api.delete(usuario, "/api/lancamentos/{id}", recebida)).hasStatus(204);

        assertThat(api.delete(usuario, "/api/contas/{id}", conjunta)).hasStatus(204);
        assertThat(jdbc.queryForObject("select count(*) from lancamentos where conta_id = ?", Integer.class, conjunta)).isZero();
        assertThat(lancamentoNoBanco(ted)).isEqualTo("DESPESA -1000.00 2026-11-02 TED sem solto");
    }

    @Test
    void excluirOUsuarioApagaAsTransferenciasConvertidasDeleESoDele() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        UsuarioDeTeste outro = usuariosDeTeste.criar();
        long horizonte = novaConta(usuario, "Banco Horizonte");
        long conjunta = novaConta(usuario, "Conta conjunta");
        long carteira = novaConta(usuario, "Carteira");
        converterComPar(usuario, importado(usuario, horizonte, "2026-11-02", "-1000.00", "TED"),
                importado(usuario, conjunta, "2026-11-03", "1000.00", "RECEBIDA"), null);
        api.idCriado(converter(usuario, importado(usuario, horizonte, "2026-11-07", "-200.00", "SAQUE"), null, carteira, null));
        long contaDoOutro = novaConta(outro, "Do outro");
        long poupancaDoOutro = novaConta(outro, "Poupança");
        converterComPar(outro, importado(outro, contaDoOutro, "2026-11-02", "-10.00", "TED DO OUTRO"),
                manual(outro, poupancaDoOutro, "RECEITA", "10", "2026-11-02", "Do outro"), null);

        assertThat(mvc.delete().uri("/api/usuarios/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + usuario.tokenAcesso())
                .contentType(MediaType.APPLICATION_JSON).content("{\"senhaAtual\":\"" + usuario.senha() + "\"}")).hasStatus(204);

        for (String tabela : new String[] {"lancamentos", "transferencias", "importacoes", "contas"}) {
            assertThat(contar(tabela, usuario)).as(tabela).isZero();
        }
        assertThat(contar("lancamentos", outro)).isEqualTo(2);
        assertThat(contar("transferencias", outro)).isEqualTo(1);
    }
}