package com.flux.lancamentos.interno;

import java.util.List;

import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;

import static com.flux.suporte.ApiDeTeste.assertCampo;
import static com.flux.suporte.ApiDeTeste.lancamento;
import static com.flux.suporte.ApiDeTeste.texto;
import static com.flux.suporte.ApiDeTeste.transferencia;
import static org.assertj.core.api.Assertions.assertThat;

/** Decisões 26 a 33 e 36 da 04c: os pares, converter com par e com lado novo, editar, desfazer e o que a convertida recusa. */
class ConversaoIT extends ConversaoBase {

    private static final String PAR_QUE_NAO_SERVE =
            "O outro lado precisa ter o mesmo valor, o sinal oposto e estar em outra conta, com até 5 dias de diferença.";

    @Test
    void paresSaoOsQueServemComOMaisPertoPrimeiro() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novaConta(usuario, "Banco Horizonte");
        long conjunta = novaConta(usuario, "Conta conjunta");
        long poupanca = novaConta(usuario, "Poupança");
        long antiga = novaConta(usuario, "Poupança antiga");
        long ted = importado(usuario, horizonte, "2026-11-02", "-1000.00", "TED 341.0001DEV FICTICIO");
        long recebida = importado(usuario, conjunta, "2026-11-03", "1000.00", "Transferência recebida - DEV FICTICIO");
        long reserva = manual(usuario, poupanca, "RECEITA", "1000", "2026-11-06", "Reserva");
        long antes = manual(usuario, poupanca, "RECEITA", "1000", "2026-10-28", "Cinco dias antes");
        manual(usuario, poupanca, "RECEITA", "1000", "2026-11-08", "Seis dias depois");
        manual(usuario, conjunta, "DESPESA", "1000", "2026-11-02", "Mesmo sinal");
        manual(usuario, horizonte, "RECEITA", "1000", "2026-11-02", "Mesma conta");
        manual(usuario, conjunta, "RECEITA", "999.99", "2026-11-02", "Outro valor");
        manual(usuario, antiga, "RECEITA", "1000", "2026-11-02", "Na conta arquivada");
        long excluido = importado(usuario, conjunta, "2026-11-02", "1000.00", "EXCLUIDO");
        assertThat(api.delete(usuario, "/api/lancamentos/{id}", excluido)).hasStatus(204);
        assertThat(api.acao(usuario, "/api/contas/{id}/arquivar", antiga)).hasStatusOk();

        MvcTestResult pares = api.get(usuario, "/api/lancamentos/{id}/pares", ted);

        assertThat(pares).hasStatusOk();
        JsonNode corpo = api.json(pares);
        assertThat(corpo.get("lancamento").get("descricao").asString()).isEqualTo("TED 341.0001DEV FICTICIO");
        assertThat(corpo.get("inicio").asString()).isEqualTo("2026-10-28");
        assertThat(corpo.get("fim").asString()).isEqualTo("2026-11-07");
        assertThat(corpo.get("pares").valueStream().map(par -> par.get("id").asLong()).toList()).containsExactly(recebida, reserva, antes);
        assertThat(texto(pares)).contains("{\"id\":" + recebida + ",\"data\":\"2026-11-03\",\"descricao\":\"Transferência recebida - DEV "
                + "FICTICIO\",\"valor\":1000.00,\"contaId\":" + conjunta + ",\"contaNome\":\"Conta conjunta\",\"origem\":\"IMPORTADO\"}",
                "\"contaNome\":\"Poupança\",\"origem\":\"MANUAL\"");

        assertThat(api.get(usuario, "/api/lancamentos/{id}/pares", reserva)).hasStatus(422).bodyJson().extractingPath("codigo")
                .isEqualTo("SO_IMPORTADO");
        assertThat(api.get(usuario, "/api/lancamentos/{id}/pares", excluido)).hasStatus(404).bodyJson().extractingPath("detail")
                .isEqualTo("Lançamento não encontrado.");
        assertThat(api.acao(usuario, "/api/contas/{id}/arquivar", horizonte)).hasStatusOk();
        assertThat(api.get(usuario, "/api/lancamentos/{id}/pares", ted)).hasStatus(422).bodyJson().extractingPath("detail")
                .isEqualTo("Banco Horizonte está arquivada. Reative a conta para converter este lançamento.");
    }

    @Test
    void converterComUmParTiraACategoriaEMantemDataValorContaObservacaoEChave() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novaConta(usuario, "Banco Horizonte");
        long conjunta = novaConta(usuario, "Conta conjunta");
        long outras = idDaCategoria(usuario, "Outras receitas");
        long ted = importado(usuario, horizonte, "2026-11-02", "-1000.00", "TED 341.0001DEV FICTICIO");
        long recebida = importado(usuario, conjunta, "2026-11-03", "1000.00", "Transferência recebida - DEV FICTICIO");
        assertThat(api.put(usuario, "/api/lancamentos/{id}", lancamento("RECEITA", "1000", "2026-11-03", "Recebida", conjunta, outras),
                recebida)).hasStatusOk();
        jdbc.update("update lancamentos set observacao = 'Reserva da viagem' where id = ?", ted);
        List<String> chaves = jdbc.queryForList("select chave_importacao || '|' || descricao_banco from lancamentos where id in (?, ?) "
                + "order by id", String.class, ted, recebida);
        String saldos = texto(api.get(usuario, "/api/contas"));

        MvcTestResult convertida = converter(usuario, ted, recebida, null, "  Reserva   para a viagem ");

        long id = api.idCriado(convertida);
        assertThat(convertida).hasHeader("Location", "/api/transferencias/" + id);
        assertThat(texto(convertida)).contains("\"tipo\":\"TRANSFERENCIA\"", "\"descricao\":\"Reserva para a viagem\"", "\"valor\":1000.00",
                "\"contaNome\":\"Banco Horizonte\"", "\"contaDestinoNome\":\"Conta conjunta\"", "\"data\":\"2026-11-02\"",
                "\"lados\":[{\"contaId\":" + horizonte + ",\"contaNome\":\"Banco Horizonte\",\"data\":\"2026-11-02\",\"origem\":\"IMPORTADO\","
                        + "\"descricaoBanco\":\"TED 341.0001DEV FICTICIO\",\"observacao\":\"Reserva da viagem\"},{\"contaId\":" + conjunta
                        + ",\"contaNome\":\"Conta conjunta\",\"data\":\"2026-11-03\",\"origem\":\"IMPORTADO\",\"descricaoBanco\":"
                        + "\"Transferência recebida - DEV FICTICIO\",\"observacao\":null}]");
        assertThat(lados(id)).containsExactly("Banco Horizonte TRANSFERENCIA -1000.00 2026-11-02 Reserva para a viagem sem",
                "Conta conjunta TRANSFERENCIA 1000.00 2026-11-03 Reserva para a viagem sem");
        assertThat(jdbc.queryForList("select chave_importacao || '|' || descricao_banco from lancamentos where id in (?, ?) order by id",
                String.class, ted, recebida)).isEqualTo(chaves);
        assertThat(texto(api.get(usuario, "/api/contas"))).isEqualTo(saldos);
        MvcTestResult doMes = api.get(usuario, "/api/lancamentos?mes=2026-11");
        assertThat(doMes).bodyJson().extractingPath("quantidadeTransferencias").isEqualTo(1);
        assertThat(doMes).bodyJson().extractingPath("totais.quantidadeEntradas").isEqualTo(0);
        MvcTestResult daConjunta = api.get(usuario, "/api/lancamentos?mes=2026-11&conta={conta}", conjunta);
        assertThat(daConjunta).bodyJson().extractingPath("dias[0].data").isEqualTo("2026-11-03");
        assertThat(daConjunta).bodyJson().extractingPath("dias[0].itens[0].direcao").isEqualTo("ENTRADA");

        // Um lado não se alcança por /api/lancamentos, não se converte de novo e as regras não o tocam (decisão 36).
        assertThat(api.get(usuario, "/api/lancamentos/{id}", ted)).hasStatus(404);
        assertThat(api.delete(usuario, "/api/lancamentos/{id}", ted)).hasStatus(404);
        assertThat(converter(usuario, ted, null, conjunta, null)).hasStatus(422).bodyJson().extractingPath("codigo")
                .isEqualTo("JA_EM_TRANSFERENCIA");
        assertThat(api.post(usuario, "/api/regras", "{\"condicao\":\"CONTEM\",\"texto\":\"recebida\",\"contaId\":null,\"categoriaId\":"
                + outras + ",\"descricao\":null}")).hasStatus(201);
        assertThat(api.get(usuario, "/api/regras/aplicacao")).bodyJson().extractingPath("total").isEqualTo(0);
    }

    @Test
    void paresTrazNoMaximoVinteOsMaisPertoPrimeiro() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novaConta(usuario, "Banco Horizonte");
        long conjunta = novaConta(usuario, "Conta conjunta");
        long ted = importado(usuario, horizonte, "2026-11-10", "-50.00", "TED");
        // O mais antigo de todos (o menor id) está a 5 dias; os outros 20, no mesmo dia: ele é o 21º e fica de fora.
        long longe = manual(usuario, conjunta, "RECEITA", "50", "2026-11-15", "Longe");
        jdbc.update("insert into lancamentos (usuario_id, conta_id, data, descricao, descricao_chave, valor, tipo, origem) "
                + "select ?, ?, date '2026-11-10', 'Perto ' || g, 'perto ' || g, 50, 'RECEITA', 'MANUAL' from generate_series(1, 20) g",
                usuario.id(), conjunta);

        MvcTestResult pares = api.get(usuario, "/api/lancamentos/{id}/pares", ted);

        List<Long> ids = api.json(pares).get("pares").valueStream().map(par -> par.get("id").asLong()).toList();
        assertThat(ids).hasSize(20).doesNotContain(longe).isSorted();
        assertThat(api.json(pares).get("pares").valueStream().map(par -> par.get("data").asString()).distinct().toList())
                .containsExactly("2026-11-10");
    }

    @Test
    void semParLancaOOutroLadoNaContaEscolhida() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novaConta(usuario, "Banco Horizonte");
        long carteira = novaConta(usuario, "Carteira");
        long tarde = novaConta(usuario, "Conta tardia", "2026-11-10");
        long arquivada = novaConta(usuario, "Arquivada");
        assertThat(api.acao(usuario, "/api/contas/{id}/arquivar", arquivada)).hasStatusOk();
        long saque = importado(usuario, horizonte, "2026-11-07", "-200.00", "SAQUE 24H 07/11 12:30");

        assertCampo(converter(usuario, saque, null, null, null), "parId", "Escolha o outro lado da transferência.");
        assertCampo(converter(usuario, saque, saque, carteira, null), "parId", "Escolha um lançamento ou uma conta, não os dois.");
        assertCampo(converter(usuario, saque, null, horizonte, null), "contaId", "Escolha uma conta diferente da deste lançamento.");
        assertCampo(converter(usuario, saque, null, carteira, "x".repeat(256)), "descricao", "Use no máximo 255 caracteres.");
        // Os 422 da conta do lado novo dizem o campo: a tela põe a mensagem no select da conta.
        MvcTestResult inexistente = converter(usuario, saque, null, 999_999_999L, null);
        assertThat(inexistente).hasStatus(422).bodyJson().extractingPath("detail").isEqualTo("Conta não encontrada.");
        assertThat(inexistente).bodyJson().extractingPath("campo").isEqualTo("contaId");
        MvcTestResult naArquivada = converter(usuario, saque, null, arquivada, null);
        assertThat(naArquivada).hasStatus(422).bodyJson().extractingPath("detail")
                .isEqualTo("Arquivada está arquivada. Reative a conta para lançar nela.");
        assertThat(naArquivada).bodyJson().extractingPath("campo").isEqualTo("contaId");
        MvcTestResult antesDoSaldo = converter(usuario, saque, null, tarde, null);
        assertThat(antesDoSaldo).hasStatus(422).bodyJson().extractingPath("codigo").isEqualTo("DATA_ANTES_DO_SALDO_INICIAL");
        assertThat(antesDoSaldo).bodyJson().extractingPath("detail")
                .isEqualTo("A data deste lançamento, 07/11/2026, é anterior ao saldo inicial de Conta tardia (10/11/2026). Escolha outra conta.");
        assertThat(antesDoSaldo).bodyJson().extractingPath("campo").isEqualTo("contaId");
        assertThat(contar("transferencias", usuario)).isZero();
        assertThat(contar("lancamentos", usuario)).isEqualTo(1);

        MvcTestResult convertida = converter(usuario, saque, null, carteira, "");

        long id = api.idCriado(convertida);
        assertThat(texto(convertida)).contains("\"descricao\":\"Transferência\"", "\"contaDestinoNome\":\"Carteira\"",
                "\"data\":\"2026-11-07\",\"origem\":\"MANUAL\",\"descricaoBanco\":null");
        assertThat(lados(id)).containsExactly("Banco Horizonte TRANSFERENCIA -200.00 2026-11-07 Transferência sem",
                "Carteira TRANSFERENCIA 200.00 2026-11-07 Transferência sem");
        assertThat(contar("lancamentos", usuario)).isEqualTo(2);
        assertThat(api.get(usuario, "/api/contas/{id}", carteira)).bodyJson().extractingPath("saldoAtual").isEqualTo(200.0);
    }

    @Test
    void soOLadoNovoConfereOLimiteDoMes() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novaConta(usuario, "Banco Horizonte");
        long conjunta = novaConta(usuario, "Conta conjunta");
        long carteira = novaConta(usuario, "Carteira");
        long ted = importado(usuario, horizonte, "2026-10-10", "-100.00", "TED SAIDA");
        long recebida = importado(usuario, conjunta, "2026-10-11", "100.00", "TED ENTRADA");
        jdbc.update("insert into lancamentos (usuario_id, conta_id, data, descricao, descricao_chave, valor, tipo, origem) "
                + "select ?, ?, date '2026-10-01' + (g % 28), 'Carga', 'carga', -1, 'DESPESA', 'MANUAL' from generate_series(1, 998) g",
                usuario.id(), horizonte);

        MvcTestResult noLimite = converter(usuario, ted, null, carteira, null);
        assertThat(noLimite).hasStatus(422).bodyJson().extractingPath("detail")
                .isEqualTo("Você chegou ao limite de 1.000 lançamentos em outubro de 2026.");
        assertThat(noLimite).bodyJson().extractingPath("campo").isEqualTo("contaId");
        assertThat(converter(usuario, ted, recebida, null, null)).hasStatus(201);
        assertThat(contar("lancamentos", usuario)).isEqualTo(1000);
    }

    @Test
    void editarAConvertidaSoMudaDescricaoEObservacaoEElaNaoSeExclui() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novaConta(usuario, "Banco Horizonte");
        long conjunta = novaConta(usuario, "Conta conjunta");
        long poupanca = novaConta(usuario, "Poupança");
        long ted = importado(usuario, horizonte, "2026-11-02", "-1000.00", "TED 341.0001DEV FICTICIO");
        long recebida = importado(usuario, conjunta, "2026-11-03", "1000.00", "Transferência recebida - DEV FICTICIO");
        long id = converterComPar(usuario, ted, recebida, null);

        MvcTestResult editada = api.put(usuario, "/api/transferencias/{id}", "{\"valor\":1000,\"data\":\"2026-11-02\",\"contaOrigemId\":"
                + horizonte + ",\"contaDestinoId\":" + conjunta + ",\"descricao\":\"Viagem\",\"observacao\":\"Passagens\"}", id);

        assertThat(editada).hasStatusOk();
        assertThat(lados(id)).containsExactly("Banco Horizonte TRANSFERENCIA -1000.00 2026-11-02 Viagem sem",
                "Conta conjunta TRANSFERENCIA 1000.00 2026-11-03 Viagem sem");
        assertThat(jdbc.queryForList("select observacao from lancamentos where transferencia_id = ?", String.class, id))
                .containsExactly("Passagens", "Passagens");
        String travado = "Contas, datas e valor de uma transferência convertida não mudam.";
        for (String mudanca : new String[] {
                transferencia("999", "2026-11-02", horizonte, conjunta, "Viagem"),
                transferencia("1000", "2026-11-03", horizonte, conjunta, "Viagem"),
                transferencia("1000", "2026-11-02", conjunta, horizonte, "Viagem"),
                transferencia("1000", "2026-11-02", horizonte, poupanca, "Viagem")}) {
            MvcTestResult recusada = api.put(usuario, "/api/transferencias/{id}", mudanca, id);
            assertThat(recusada).hasStatus(422).bodyJson().extractingPath("codigo").isEqualTo("IMPORTADO_TRAVADO");
            assertThat(recusada).bodyJson().extractingPath("detail").isEqualTo(travado);
        }
        MvcTestResult exclusao = api.delete(usuario, "/api/transferencias/{id}", id);
        assertThat(exclusao).hasStatus(422).bodyJson().extractingPath("codigo").isEqualTo("TRANSFERENCIA_CONVERTIDA");
        assertThat(exclusao).bodyJson().extractingPath("detail")
                .isEqualTo("Uma transferência convertida não se exclui. Desfaça a transferência e exclua cada lançamento.");
        // Com uma conta arquivada, a convertida fica só leitura: o 422 manda reativar para editar ou desfazer, e o
        // DELETE continua recusado como convertida (a manual segue com "editar ou excluir", na TransferenciasIT).
        assertThat(api.acao(usuario, "/api/contas/{id}/arquivar", conjunta)).hasStatusOk();
        assertThat(api.put(usuario, "/api/transferencias/{id}", "{\"valor\":1000,\"data\":\"2026-11-02\",\"contaOrigemId\":" + horizonte
                + ",\"contaDestinoId\":" + conjunta + ",\"descricao\":\"Viagem\",\"observacao\":null}", id)).hasStatus(422).bodyJson()
                .extractingPath("detail").isEqualTo("Conta conjunta está arquivada. Reative a conta para editar ou desfazer esta transferência.");
        assertThat(api.delete(usuario, "/api/transferencias/{id}", id)).hasStatus(422).bodyJson().extractingPath("codigo")
                .isEqualTo("TRANSFERENCIA_CONVERTIDA");
        assertThat(lados(id)).hasSize(2);
    }

    @Test
    void desfazerSoltaOsDoisLadosSemApagarNenhum() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novaConta(usuario, "Banco Horizonte");
        long conjunta = novaConta(usuario, "Conta conjunta");
        long poupanca = novaConta(usuario, "Poupança");
        long ted = importado(usuario, horizonte, "2026-11-02", "-1000.00", "TED 341.0001DEV FICTICIO");
        long reserva = manual(usuario, conjunta, "RECEITA", "1000", "2026-11-03", "Reserva");
        long id = converterComPar(usuario, ted, reserva, "Viagem");
        long manualId = api.idCriado(api.post(usuario, "/api/transferencias", transferencia("5", "2026-11-04", horizonte, poupanca, null)));

        assertThat(api.acao(usuario, "/api/transferencias/{id}/desfazer", manualId)).hasStatus(422).bodyJson().extractingPath("codigo")
                .isEqualTo("TRANSFERENCIA_MANUAL");
        assertThat(api.acao(usuario, "/api/contas/{id}/arquivar", conjunta)).hasStatusOk();
        assertThat(api.acao(usuario, "/api/transferencias/{id}/desfazer", id)).hasStatus(422).bodyJson().extractingPath("detail")
                .isEqualTo("Conta conjunta está arquivada. Reative a conta para desfazer esta transferência.");
        assertThat(lados(id)).hasSize(2);
        assertThat(api.acao(usuario, "/api/contas/{id}/reativar", conjunta)).hasStatusOk();

        MvcTestResult desfeita = api.acao(usuario, "/api/transferencias/{id}/desfazer", id);

        assertThat(desfeita).hasStatusOk();
        assertThat(api.json(desfeita).valueStream().map(item -> item.get("tipo").asString() + " " + item.get("descricao").asString())
                .toList()).containsExactly("DESPESA TED 341.0001DEV FICTICIO", "RECEITA Viagem");
        assertThat(lancamentoNoBanco(ted)).isEqualTo("DESPESA -1000.00 2026-11-02 TED 341.0001DEV FICTICIO sem solto");
        assertThat(lancamentoNoBanco(reserva)).isEqualTo("RECEITA 1000.00 2026-11-03 Viagem sem solto");
        assertThat(jdbc.queryForObject("select count(*) from transferencias where id = ?", Integer.class, id)).isZero();
        assertThat(api.acao(usuario, "/api/transferencias/{id}/desfazer", id)).hasStatus(404).bodyJson().extractingPath("detail")
                .isEqualTo("Transferência não encontrada.");
        // Depois de desfeita, cada lado volta a ser um lançamento comum: o importado se exclui (fica ignorado).
        assertThat(api.delete(usuario, "/api/lancamentos/{id}", ted)).hasStatus(204);
        assertThat(api.get(usuario, "/api/lancamentos/{id}/pares", reserva)).hasStatus(422).bodyJson().extractingPath("codigo")
                .isEqualTo("SO_IMPORTADO");
    }

    @Test
    void oParQueNaoServeMaisEOParDeOutraTransferenciaSaoRecusados() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novaConta(usuario, "Banco Horizonte");
        long conjunta = novaConta(usuario, "Conta conjunta");
        long poupanca = novaConta(usuario, "Poupança");
        long ted = importado(usuario, horizonte, "2026-11-02", "-1000.00", "TED 1");
        long outroTed = importado(usuario, horizonte, "2026-11-02", "-1000.00", "TED 2");
        long recebida = importado(usuario, conjunta, "2026-11-03", "1000.00", "RECEBIDA");
        long longe = manual(usuario, poupanca, "RECEITA", "1000", "2026-11-08", "Longe");
        long reserva = manual(usuario, poupanca, "RECEITA", "1000", "2026-11-04", "Reserva");
        converterComPar(usuario, ted, recebida, null);

        assertThat(converter(usuario, outroTed, recebida, null, null)).hasStatus(422).bodyJson().extractingPath("detail")
                .isEqualTo("O lançamento escolhido já está numa transferência.");
        assertThat(converter(usuario, outroTed, longe, null, null)).hasStatus(422).bodyJson().extractingPath("detail")
                .isEqualTo(PAR_QUE_NAO_SERVE);
        assertThat(converter(usuario, outroTed, 999_999_999L, null, null)).hasStatus(422).bodyJson().extractingPath("codigo")
                .isEqualTo("PAR_INVALIDO");
        assertThat(api.acao(usuario, "/api/contas/{id}/arquivar", poupanca)).hasStatusOk();
        assertThat(converter(usuario, outroTed, reserva, null, null)).hasStatus(422).bodyJson().extractingPath("detail")
                .isEqualTo("Poupança está arquivada. Reative a conta para converter este lançamento.");
        assertThat(contar("transferencias", usuario)).isEqualTo(1);
        assertThat(lancamentoNoBanco(outroTed)).isEqualTo("DESPESA -1000.00 2026-11-02 TED 2 sem solto");
    }
}