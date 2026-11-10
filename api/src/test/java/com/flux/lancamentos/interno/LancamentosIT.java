package com.flux.lancamentos.interno;

import com.flux.suporte.IntegracaoBase;
import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static com.flux.suporte.ApiDeTeste.assertCampo;
import static com.flux.suporte.ApiDeTeste.conta;
import static com.flux.suporte.ApiDeTeste.lancamento;
import static com.flux.suporte.ApiDeTeste.texto;
import static com.flux.suporte.ApiDeTeste.transferencia;
import static org.assertj.core.api.Assertions.assertThat;

class LancamentosIT extends IntegracaoBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void criaDespesaNegativaEReceitaPositivaComOsNomesNaResposta() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = api.idCriado(api.post(usuario, "/api/contas", conta("Banco Horizonte", "CORRENTE", "1000", "2026-10-01", "null")));

        MvcTestResult despesa = api.post(usuario, "/api/lancamentos",
                lancamento("DESPESA", "287.43", "2026-10-06", "  Mercado   Bom Preço ", horizonte, categoria(usuario, "Mercado")));
        long id = api.idCriado(despesa);
        assertThat(despesa).hasHeader("Location", "/api/lancamentos/" + id);
        assertThat(texto(despesa)).contains("\"tipo\":\"DESPESA\"", "\"data\":\"2026-10-06\"", "\"descricao\":\"Mercado Bom Preço\"",
                "\"valor\":-287.43", "\"observacao\":null", "\"contaNome\":\"Banco Horizonte\"", "\"categoriaNome\":\"Mercado\"",
                "\"categoriaMaeNome\":\"Alimentação\"", "\"categoriaArquivada\":false", "\"contaDestinoId\":null",
                "\"direcao\":null", "\"futuro\":false", "\"somenteLeitura\":false");
        MvcTestResult receita = api.post(usuario, "/api/lancamentos",
                lancamento("RECEITA", "7200", "2026-10-05", "Salário", horizonte, categoria(usuario, "Salário")));
        assertThat(texto(receita)).contains("\"tipo\":\"RECEITA\"", "\"valor\":7200.00", "\"categoriaMaeNome\":null");

        assertThat(jdbc.queryForObject("select valor || ' ' || origem || ' ' || descricao_chave from lancamentos where id = ?",
                String.class, id)).isEqualTo("-287.43 MANUAL mercado bom preco");
        assertThat(texto(api.get(usuario, "/api/lancamentos/{id}", id))).contains("\"valor\":-287.43");
        assertThat(texto(api.get(usuario, "/api/contas/{id}", horizonte))).contains("\"saldoAtual\":7912.57");
    }

    @Test
    void validaOsCampos() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = api.idCriado(api.post(usuario, "/api/contas", conta("Banco Horizonte", "CORRENTE", "0", "2026-10-01", "null")));

        assertCampo(api.post(usuario, "/api/lancamentos", lancamento("DESPESA", "0", "2026-10-06", "Zero", horizonte, null)),
                "valor", "O valor precisa ser maior que zero.");
        assertCampo(api.post(usuario, "/api/lancamentos", lancamento("DESPESA", "-5", "2026-10-06", "Negativo", horizonte, null)),
                "valor", "O valor precisa ser maior que zero.");
        assertCampo(api.post(usuario, "/api/lancamentos", lancamento("DESPESA", "1.005", "2026-10-06", "Casas", horizonte, null)),
                "valor", "Use no máximo 2 casas decimais.");
        assertCampo(api.post(usuario, "/api/lancamentos", lancamento("DESPESA", "10000000000000", "2026-10-06", "Grande", horizonte, null)),
                "valor", "Use no máximo 13 dígitos antes da vírgula.");
        assertCampo(api.post(usuario, "/api/lancamentos", lancamento("DESPESA", "10", "2026-10-06", "   ", horizonte, null)),
                "descricao", "Informe a descrição.");
        assertCampo(api.post(usuario, "/api/lancamentos", lancamento("DESPESA", "10", "2026-10-06", "x".repeat(256), horizonte, null)),
                "descricao", "Use no máximo 255 caracteres.");
        assertCampo(api.post(usuario, "/api/lancamentos", lancamento("TRANSFERENCIA", "10", "2026-10-06", "Tipo", horizonte, null)),
                "tipo", "Escolha receita ou despesa.");
        assertCampo(api.post(usuario, "/api/lancamentos", "{\"tipo\":\"DESPESA\",\"valor\":10,\"data\":\"2026-10-06\","
                + "\"descricao\":\"Sem conta\",\"contaId\":null,\"categoriaId\":null,\"observacao\":null}"), "contaId", "Escolha a conta.");
        assertCampo(api.post(usuario, "/api/lancamentos", "{\"tipo\":\"DESPESA\",\"valor\":10,\"data\":\"2026-10-06\",\"descricao\":"
                + "\"Nota\",\"contaId\":" + horizonte + ",\"categoriaId\":null,\"observacao\":\"" + "x".repeat(1001) + "\"}"),
                "observacao", "Use no máximo 1.000 caracteres.");
        assertThat(jdbc.queryForObject("select count(*) from lancamentos where usuario_id = ?", Integer.class, usuario.id())).isZero();
    }

    @Test
    void categoriaPrecisaSerDaNaturezaEAtivaEAPrincipalTambemRecebe() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = api.idCriado(api.post(usuario, "/api/contas", conta("Banco Horizonte", "CORRENTE", "0", "2026-10-01", "null")));
        api.acao(usuario, "/api/categorias/{id}/arquivar", categoria(usuario, "Lazer"));

        assertRecusa(api.post(usuario, "/api/lancamentos", lancamento("DESPESA", "10", "2026-10-06", "X", horizonte, categoria(usuario, "Salário"))),
                "CATEGORIA_INVALIDA", "Escolha uma categoria de despesa.");
        assertRecusa(api.post(usuario, "/api/lancamentos", lancamento("RECEITA", "10", "2026-10-06", "X", horizonte, categoria(usuario, "Mercado"))),
                "CATEGORIA_INVALIDA", "Escolha uma categoria de receita.");
        assertRecusa(api.post(usuario, "/api/lancamentos", lancamento("DESPESA", "10", "2026-10-06", "X", horizonte, categoria(usuario, "Lazer"))),
                "CATEGORIA_INVALIDA", "Escolha uma categoria ativa.");
        assertRecusa(api.post(usuario, "/api/lancamentos", lancamento("DESPESA", "10", "2026-10-06", "X", horizonte, 999_999_999L)),
                "CATEGORIA_INVALIDA", "Categoria não encontrada.");
        assertThat(api.post(usuario, "/api/lancamentos",
                lancamento("DESPESA", "42", "2026-10-07", "Almoço", horizonte, categoria(usuario, "Alimentação")))).hasStatus(201);
    }

    @Test
    void contaArquivadaOuInexistenteNaoRecebeLancamento() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long reserva = api.idCriado(api.post(usuario, "/api/contas", conta("Reserva", "INVESTIMENTO", "0", "2026-10-01", "null")));
        api.acao(usuario, "/api/contas/{id}/arquivar", reserva);

        assertRecusa(api.post(usuario, "/api/lancamentos", lancamento("RECEITA", "10", "2026-10-06", "Rendimento", reserva, null)),
                "CONTA_ARQUIVADA", "Reserva está arquivada. Reative a conta para lançar nela.");
        MvcTestResult inexistente = api.post(usuario, "/api/lancamentos", lancamento("RECEITA", "10", "2026-10-06", "X", 999_999_999L, null));
        assertThat(inexistente).hasStatus(422).bodyJson().extractingPath("detail").isEqualTo("Conta não encontrada.");
        assertThat(texto(inexistente)).doesNotContain("codigo");
    }

    @Test
    void editaTrocandoTipoEContaEMantendoACategoriaArquivada() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = api.idCriado(api.post(usuario, "/api/contas", conta("Banco Horizonte", "CORRENTE", "0", "2026-10-01", "null")));
        long carteira = api.idCriado(api.post(usuario, "/api/contas", conta("Carteira", "CARTEIRA", "0", "2026-10-01", "null")));
        long educacao = categoria(usuario, "Educação");
        long id = api.idCriado(api.post(usuario, "/api/lancamentos",
                lancamento("DESPESA", "400", "2026-10-20", "Mensalidade", horizonte, educacao)));
        api.acao(usuario, "/api/categorias/{id}/arquivar", educacao);
        api.acao(usuario, "/api/categorias/{id}/arquivar", categoria(usuario, "Lazer"));

        MvcTestResult mantida = api.put(usuario, "/api/lancamentos/{id}",
                lancamento("DESPESA", "450", "2026-10-20", "Mensalidade do curso", horizonte, educacao), id);
        assertThat(mantida).hasStatusOk();
        assertThat(texto(mantida)).contains("\"valor\":-450.00", "\"categoriaNome\":\"Educação\"", "\"categoriaArquivada\":true");
        assertRecusa(api.put(usuario, "/api/lancamentos/{id}",
                lancamento("DESPESA", "450", "2026-10-20", "Mensalidade", horizonte, categoria(usuario, "Lazer")), id),
                "CATEGORIA_INVALIDA", "Escolha uma categoria ativa.");
        assertRecusa(api.put(usuario, "/api/lancamentos/{id}",
                lancamento("RECEITA", "450", "2026-10-20", "Mensalidade", horizonte, educacao), id),
                "CATEGORIA_INVALIDA", "Escolha uma categoria de receita.");
        MvcTestResult virouReceita = api.put(usuario, "/api/lancamentos/{id}",
                lancamento("RECEITA", "450", "2026-10-21", "Reembolso do curso", carteira, null), id);
        assertThat(virouReceita).hasStatusOk();
        assertThat(texto(virouReceita)).contains("\"tipo\":\"RECEITA\"", "\"valor\":450.00", "\"contaNome\":\"Carteira\"",
                "\"categoriaId\":null", "\"data\":\"2026-10-21\"");
        assertThat(jdbc.queryForObject("select valor from lancamentos where id = ?", String.class, id)).isEqualTo("450.00");
    }

    @Test
    void lancamentoDeContaArquivadaFicaSoLeitura() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long salario = api.idCriado(api.post(usuario, "/api/contas", conta("Conta salário", "CORRENTE", "0", "2026-10-01", "null")));
        long id = api.idCriado(api.post(usuario, "/api/lancamentos", lancamento("DESPESA", "29.90", "2026-10-05", "Tarifa", salario, null)));
        api.acao(usuario, "/api/contas/{id}/arquivar", salario);

        assertThat(texto(api.get(usuario, "/api/lancamentos/{id}", id))).contains("\"somenteLeitura\":true");
        String mensagem = "Conta salário está arquivada. Reative a conta para editar ou excluir este lançamento.";
        assertRecusa(api.put(usuario, "/api/lancamentos/{id}", lancamento("DESPESA", "30", "2026-10-05", "Tarifa", salario, null), id),
                "CONTA_ARQUIVADA", mensagem);
        assertRecusa(api.delete(usuario, "/api/lancamentos/{id}", id), "CONTA_ARQUIVADA", mensagem);

        api.acao(usuario, "/api/contas/{id}/reativar", salario);
        assertThat(api.delete(usuario, "/api/lancamentos/{id}", id)).hasStatus(204);
        assertThat(api.get(usuario, "/api/lancamentos/{id}", id)).hasStatus(404).bodyJson().extractingPath("detail")
                .isEqualTo("Lançamento não encontrado.");
    }

    @Test
    void ladoDeTransferenciaNaoEhEditadoComoLancamento() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = api.idCriado(api.post(usuario, "/api/contas", conta("Banco Horizonte", "CORRENTE", "0", "2026-10-01", "null")));
        long poupanca = api.idCriado(api.post(usuario, "/api/contas", conta("Poupança", "POUPANCA", "0", "2026-10-01", "null")));
        long transferenciaId = api.idCriado(api.post(usuario, "/api/transferencias", transferencia("500", "2026-10-05", horizonte, poupanca, null)));
        long lado = jdbc.queryForObject("select min(id) from lancamentos where transferencia_id = ?", Long.class, transferenciaId);

        assertThat(api.get(usuario, "/api/lancamentos/{id}", lado)).hasStatus(404);
        assertThat(api.put(usuario, "/api/lancamentos/{id}", lancamento("DESPESA", "1", "2026-10-05", "X", horizonte, null), lado)).hasStatus(404);
        assertThat(api.delete(usuario, "/api/lancamentos/{id}", lado)).hasStatus(404);
        assertThat(jdbc.queryForObject("select count(*) from lancamentos where transferencia_id = ?", Integer.class, transferenciaId)).isEqualTo(2);
    }

    private long categoria(UsuarioDeTeste usuario, String nome) {
        return jdbc.queryForObject("select id from categorias where usuario_id = ? and nome = ?", Long.class, usuario.id(), nome);
    }

    private static void assertRecusa(MvcTestResult resposta, String codigo, String mensagem) {
        assertThat(resposta).hasStatus(422).bodyJson().extractingPath("codigo").isEqualTo(codigo);
        assertThat(resposta).bodyJson().extractingPath("detail").isEqualTo(mensagem);
    }
}
