package com.flux.importacao.interno;

import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

/** Decisão 24: os limites valem na confirmação, no tudo ou nada, e os ignorados contam. */
class LimitesDaImportacaoIT extends ImportacaoBase {

    @Test
    void mesCheioRecusaAImportacaoInteiraEOIgnoradoConta() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novaConta(usuario, "Banco Horizonte", "2026-09-01");
        inserir(usuario, horizonte, 998, "2026-10-01", 30);
        importar(usuario, horizonte, "um.ofx", ofx("0260", "1", transacao("u1", "2026-10-20", "-1.00", "UM")), 1);
        assertThat(api.delete(usuario, "/api/lancamentos/{id}", lancamentoDaChave(usuario, "ID:" + horizonte + ":u1"))).hasStatus(204);
        byte[] arquivo = ofx("0260", "1",
                transacao("o1", "2026-10-21", "-1.00", "OUTUBRO 1"),
                transacao("o2", "2026-10-22", "-1.00", "OUTUBRO 2"),
                transacao("n1", "2026-11-02", "-1.00", "NOVEMBRO"));
        String hash = api.json(previa(usuario, horizonte, "tres.ofx", arquivo)).get("hash").asString();

        MvcTestResult recusa = confirmar(usuario, horizonte, "tres.ofx", arquivo, hash, escolhas(1, 2, 3));

        assertThat(recusa).hasStatus(422).bodyJson().extractingPath("codigo").isEqualTo("LIMITE_DE_LANCAMENTOS");
        assertThat(recusa).bodyJson().extractingPath("detail").isEqualTo("Esta importação passaria do limite de 1.000 lançamentos em "
                + "outubro de 2026. Desmarque linhas desse mês ou importe um período menor.");
        assertThat(jdbc.queryForObject("select count(*) from importacoes where usuario_id = ?", Integer.class, usuario.id())).isEqualTo(1);
        assertThat(confirmar(usuario, horizonte, "tres.ofx", arquivo, hash, escolhas(1, 3))).hasStatus(201);
    }

    @Test
    void usuarioPertoDosCemMilNaoImportaAlemDoLimite() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novaConta(usuario, "Banco Horizonte", "2016-01-01");
        inserir(usuario, horizonte, 99_999, "2016-01-01", 3_650);
        byte[] arquivo = ofx("0260", "1", transacao("a", "2026-10-10", "-1.00", "A"), transacao("b", "2026-10-11", "-1.00", "B"));
        String hash = api.json(previa(usuario, horizonte, "dois.ofx", arquivo)).get("hash").asString();

        assertThat(confirmar(usuario, horizonte, "dois.ofx", arquivo, hash, escolhas(1, 2))).hasStatus(422).bodyJson()
                .extractingPath("detail").isEqualTo("Esta importação passaria do limite de 100.000 lançamentos.");
        assertThat(confirmar(usuario, horizonte, "dois.ofx", arquivo, hash, escolhas(1))).hasStatus(201);
    }

    /** {@code quantidade} despesas de R$ 1,00 pelo SQL, a partir de {@code inicio}, espalhadas por {@code dias} dias. */
    private void inserir(UsuarioDeTeste usuario, long contaId, int quantidade, String inicio, int dias) {
        jdbc.update("insert into lancamentos (usuario_id, conta_id, data, descricao, descricao_chave, valor, tipo, origem) "
                + "select ?, ?, cast(? as date) + (g % ?), 'Carga', 'carga', -1, 'DESPESA', 'MANUAL' from generate_series(1, ?) g",
                usuario.id(), contaId, inicio, dias, quantidade);
    }
}
