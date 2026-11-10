package com.flux.lancamentos.interno;

import java.util.List;

import com.flux.suporte.IntegracaoBase;
import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static com.flux.suporte.ApiDeTeste.assertCampo;
import static com.flux.suporte.ApiDeTeste.conta;
import static com.flux.suporte.ApiDeTeste.texto;
import static com.flux.suporte.ApiDeTeste.transferencia;
import static org.assertj.core.api.Assertions.assertThat;

class TransferenciasIT extends IntegracaoBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void criaOsDoisLadosComADescricaoPadraoESemMexerNoSaldoTotal() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = api.idCriado(api.post(usuario, "/api/contas", conta("Banco Horizonte", "CORRENTE", "1000", "2026-10-01", "null")));
        long poupanca = api.idCriado(api.post(usuario, "/api/contas", conta("Poupança", "POUPANCA", "0", "2026-10-01", "null")));

        MvcTestResult criada = api.post(usuario, "/api/transferencias", transferencia("500", "2026-10-05", horizonte, poupanca, "  "));
        long id = api.idCriado(criada);
        assertThat(criada).hasHeader("Location", "/api/transferencias/" + id);
        assertThat(texto(criada)).contains("\"tipo\":\"TRANSFERENCIA\"", "\"descricao\":\"Transferência\"", "\"valor\":500.00",
                "\"contaNome\":\"Banco Horizonte\"", "\"contaDestinoNome\":\"Poupança\"", "\"categoriaId\":null", "\"direcao\":null");
        assertThat(lados(id)).containsExactly("Banco Horizonte -500.00 2026-10-05 Transferência", "Poupança 500.00 2026-10-05 Transferência");
        assertThat(texto(api.get(usuario, "/api/contas"))).contains("\"saldoAtual\":500.00",
                "\"totais\":{\"geral\":1000.00,\"disponivel\":1000.00,\"investimentos\":0.00}");
        assertThat(texto(api.get(usuario, "/api/transferencias/{id}", id))).contains("\"contaDestinoNome\":\"Poupança\"");
    }

    @Test
    void recusaAMesmaContaEDataAntesDoSaldoInicialDeUmaDelas() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = api.idCriado(api.post(usuario, "/api/contas", conta("Banco Horizonte", "CORRENTE", "0", "2026-10-01", "null")));
        long carteira = api.idCriado(api.post(usuario, "/api/contas", conta("Carteira", "CARTEIRA", "0", "2026-10-20", "null")));

        assertCampo(api.post(usuario, "/api/transferencias", transferencia("10", "2026-10-21", horizonte, horizonte, null)),
                "contaDestinoId", "Escolha uma conta diferente da de origem.");
        assertCampo(api.post(usuario, "/api/transferencias", transferencia("10", "2026-10-10", horizonte, carteira, null)),
                "data", "A data precisa ser 20/10/2026 ou depois, a data do saldo inicial de Carteira.");
        assertCampo(api.post(usuario, "/api/transferencias", transferencia("10", "2026-10-10", carteira, horizonte, null)),
                "data", "A data precisa ser 20/10/2026 ou depois, a data do saldo inicial de Carteira.");
        assertCampo(api.post(usuario, "/api/transferencias", transferencia("0", "2026-10-21", horizonte, carteira, null)),
                "valor", "O valor precisa ser maior que zero.");
        assertCampo(api.post(usuario, "/api/transferencias", "{\"valor\":10,\"data\":\"2026-10-21\",\"contaOrigemId\":null,"
                + "\"contaDestinoId\":" + carteira + ",\"descricao\":null,\"observacao\":null}"), "contaOrigemId", "Escolha a conta de origem.");
        assertCampo(api.post(usuario, "/api/transferencias", transferencia("10", "2026-10-21", horizonte, carteira, "x".repeat(256))),
                "descricao", "Use no máximo 255 caracteres.");
        assertThat(jdbc.queryForObject("select count(*) from transferencias where usuario_id = ?", Integer.class, usuario.id())).isZero();
    }

    @Test
    void editaTrocandoAsContasOValorEADataNosDoisLados() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = api.idCriado(api.post(usuario, "/api/contas", conta("Banco Horizonte", "CORRENTE", "1000", "2026-10-01", "null")));
        long poupanca = api.idCriado(api.post(usuario, "/api/contas", conta("Poupança", "POUPANCA", "0", "2026-10-01", "null")));
        long carteira = api.idCriado(api.post(usuario, "/api/contas", conta("Carteira", "CARTEIRA", "0", "2026-10-01", "null")));
        long id = api.idCriado(api.post(usuario, "/api/transferencias", transferencia("500", "2026-10-05", horizonte, poupanca, "Reserva")));

        MvcTestResult editada = api.put(usuario, "/api/transferencias/{id}", transferencia("120.5", "2026-10-21", poupanca, carteira, "Ajuste"), id);
        assertThat(editada).hasStatusOk();
        assertThat(texto(editada)).contains("\"valor\":120.50", "\"contaNome\":\"Poupança\"", "\"contaDestinoNome\":\"Carteira\"",
                "\"descricao\":\"Ajuste\"", "\"data\":\"2026-10-21\"");
        assertThat(lados(id)).containsExactly("Poupança -120.50 2026-10-21 Ajuste", "Carteira 120.50 2026-10-21 Ajuste");
        assertThat(texto(api.get(usuario, "/api/contas/{id}", horizonte))).contains("\"saldoAtual\":1000.00");
        assertThat(texto(api.get(usuario, "/api/contas/{id}", poupanca))).contains("\"saldoAtual\":-120.50");
        assertThat(texto(api.get(usuario, "/api/contas/{id}", carteira))).contains("\"saldoAtual\":120.50");
    }

    @Test
    void excluiOsDoisLadosEOVinculo() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = api.idCriado(api.post(usuario, "/api/contas", conta("Banco Horizonte", "CORRENTE", "1000", "2026-10-01", "null")));
        long poupanca = api.idCriado(api.post(usuario, "/api/contas", conta("Poupança", "POUPANCA", "0", "2026-10-01", "null")));
        long id = api.idCriado(api.post(usuario, "/api/transferencias", transferencia("500", "2026-10-05", horizonte, poupanca, null)));

        assertThat(api.delete(usuario, "/api/transferencias/{id}", id)).hasStatus(204);
        assertThat(jdbc.queryForObject("select count(*) from lancamentos where usuario_id = ?", Integer.class, usuario.id())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from transferencias where usuario_id = ?", Integer.class, usuario.id())).isZero();
        assertThat(api.get(usuario, "/api/transferencias/{id}", id)).hasStatus(404).bodyJson().extractingPath("detail")
                .isEqualTo("Transferência não encontrada.");
        assertThat(texto(api.get(usuario, "/api/contas/{id}", horizonte))).contains("\"saldoAtual\":1000.00");
    }

    @Test
    void contaArquivadaNumLadoDeixaATransferenciaSoLeitura() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = api.idCriado(api.post(usuario, "/api/contas", conta("Banco Horizonte", "CORRENTE", "0", "2026-10-01", "null")));
        long salario = api.idCriado(api.post(usuario, "/api/contas", conta("Conta salário", "CORRENTE", "0", "2026-10-01", "null")));
        long id = api.idCriado(api.post(usuario, "/api/transferencias", transferencia("2400", "2026-10-03", salario, horizonte, "Salário de outubro")));
        api.acao(usuario, "/api/contas/{id}/arquivar", salario);

        assertThat(texto(api.get(usuario, "/api/transferencias/{id}", id))).contains("\"somenteLeitura\":true");
        String mensagem = "Conta salário está arquivada. Reative a conta para editar ou excluir esta transferência.";
        MvcTestResult edicao = api.put(usuario, "/api/transferencias/{id}", transferencia("1", "2026-10-03", salario, horizonte, null), id);
        assertThat(edicao).hasStatus(422).bodyJson().extractingPath("codigo").isEqualTo("CONTA_ARQUIVADA");
        assertThat(edicao).bodyJson().extractingPath("detail").isEqualTo(mensagem);
        assertThat(api.delete(usuario, "/api/transferencias/{id}", id)).hasStatus(422).bodyJson().extractingPath("detail").isEqualTo(mensagem);
        assertThat(api.post(usuario, "/api/transferencias", transferencia("1", "2026-10-04", horizonte, salario, null)))
                .hasStatus(422).bodyJson().extractingPath("detail").isEqualTo("Conta salário está arquivada. Reative a conta para lançar nela.");
        assertThat(lados(id)).hasSize(2);
    }

    /** "conta valor data descrição" de cada lado, a saída primeiro. */
    private List<String> lados(long transferenciaId) {
        return jdbc.queryForList("select c.nome || ' ' || l.valor || ' ' || l.data || ' ' || l.descricao from lancamentos l "
                + "join contas c on c.id = l.conta_id where l.transferencia_id = ? order by l.valor", String.class, transferenciaId);
    }
}
