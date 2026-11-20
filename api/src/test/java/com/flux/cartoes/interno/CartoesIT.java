package com.flux.cartoes.interno;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

import com.flux.cartoes.CalendarioDoCartao;
import com.flux.suporte.CartoesBase;
import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static com.flux.suporte.ApiDeTeste.assertCampo;
import static com.flux.suporte.ApiDeTeste.lancamento;
import static com.flux.suporte.ApiDeTeste.texto;
import static org.assertj.core.api.Assertions.assertThat;

/** O cadastro de cartões (decisões 1 a 6 da 05a). */
class CartoesIT extends CartoesBase {

    @Test
    void criaListaEmOrdemAlfabeticaComAFaturaAbertaPelosDias() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novaConta(usuario, "Banco Horizonte");
        MvcTestResult criado = api.post(usuario, "/api/cartoes", cartao("  Cartão   Lua ", "2000", 25, 5, horizonte, "\"ROXO\""));
        long lua = api.idCriado(criado);
        assertThat(criado).bodyJson().extractingPath("nome").isEqualTo("Cartão Lua");
        assertThat(texto(criado)).contains("\"limite\":2000.00");
        long estrela = novoCartao(usuario, "Cartão Estrela", 3, 10, horizonte);

        LocalDate hoje = LocalDate.now(relogioDeTeste);
        CalendarioDoCartao calendario = new CalendarioDoCartao(25, 5);
        YearMonth aberta = calendario.faturaDe(hoje, Map.of());
        MvcTestResult lista = api.get(usuario, "/api/cartoes");
        assertThat(lista).hasStatus(200).bodyJson().extractingPath("ativos[*].nome").asArray().containsExactly("Cartão Estrela", "Cartão Lua");
        assertThat(lista).bodyJson().extractingPath("arquivados").asArray().isEmpty();
        MvcTestResult buscado = api.get(usuario, "/api/cartoes/{id}", lua);
        assertThat(buscado).bodyJson().extractingPath("contaPagamentoNome").isEqualTo("Banco Horizonte");
        assertThat(buscado).bodyJson().extractingPath("contaPagamentoArquivada").isEqualTo(false);
        assertThat(buscado).bodyJson().extractingPath("cor").isEqualTo("ROXO");
        assertThat(buscado).bodyJson().extractingPath("faturaAberta.mes").isEqualTo(aberta.toString());
        assertThat(buscado).bodyJson().extractingPath("faturaAberta.fechamento").isEqualTo(calendario.padrao(aberta).fechamento().toString());
        assertThat(buscado).bodyJson().extractingPath("faturaAberta.vencimento").isEqualTo(calendario.padrao(aberta).vencimento().toString());
        assertThat(buscado).bodyJson().extractingPath("faturaAberta.lancamentos").isEqualTo(0);
        assertThat(buscado).bodyJson().extractingPath("proximaFatura.mes").isEqualTo(aberta.plusMonths(1).toString());
        assertThat(texto(buscado)).contains("\"total\":0.00").contains("\"emAberto\":0.00");
        assertThat(api.get(usuario, "/api/cartoes/{id}", estrela)).hasStatus(200);
    }

    @Test
    void validaOsCamposComAsMensagensDaTela() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novaConta(usuario, "Banco Horizonte");
        assertCampo(api.post(usuario, "/api/cartoes", cartao(" ", "1", 3, 10, horizonte, "null")), "nome", "Escreva o nome do cartão.");
        assertCampo(api.post(usuario, "/api/cartoes", cartao("x".repeat(61), "1", 3, 10, horizonte, "null")), "nome",
                "O nome pode ter até 60 caracteres.");
        assertCampo(api.post(usuario, "/api/cartoes", cartao("Cartão", "0", 3, 10, horizonte, "null")), "limite",
                "O limite precisa ser maior que zero.");
        assertCampo(api.post(usuario, "/api/cartoes", cartao("Cartão", "1.234", 3, 10, horizonte, "null")), "limite",
                "Use até 2 casas decimais e até 13 dígitos antes da vírgula.");
        assertCampo(api.post(usuario, "/api/cartoes", cartao("Cartão", "1", 0, 10, horizonte, "null")), "diaFechamento",
                "Escolha um dia de 1 a 31.");
        assertCampo(api.post(usuario, "/api/cartoes", cartao("Cartão", "1", 3, 32, horizonte, "null")), "diaVencimento",
                "Escolha um dia de 1 a 31.");
        assertCampo(api.post(usuario, "/api/cartoes", cartao("Cartão", "1", 28, 30, horizonte, "null")), "diaVencimento",
                "Com o fechamento no dia 28 ou depois, o vencimento precisa ser num dia antes dele, no mês seguinte.");
        assertThat(api.post(usuario, "/api/cartoes", cartao("Cartão", "1", 27, 28, horizonte, "null"))).hasStatus(201);
        assertThat(api.post(usuario, "/api/cartoes", cartao("Cartão 31", "1", 31, 31, horizonte, "null"))).hasStatus(201);
    }

    /** Decisão 4: a conta de pagamento é do usuário, ativa e não é investimento; na edição, a que o cartão já tem fica. */
    @Test
    void aContaDePagamentoPrecisaPagarFatura() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        UsuarioDeTeste outro = usuariosDeTeste.criar();
        long horizonte = novaConta(usuario, "Banco Horizonte");
        long antiga = novaConta(usuario, "Poupança antiga", "POUPANCA");
        long reserva = novaConta(usuario, "Reserva", "INVESTIMENTO");
        long doOutro = novaConta(outro, "Do outro");
        long cartao = novoCartao(usuario, "Cartão Lua", 25, 5, antiga);
        assertThat(api.acao(usuario, "/api/contas/{id}/arquivar", antiga)).hasStatus(200);

        assertThat(api.post(usuario, "/api/cartoes", cartao("Cartão", "1", 3, 10, doOutro, "null"))).hasStatus(422).bodyJson()
                .extractingPath("detail").isEqualTo("Conta não encontrada.");
        assertThat(api.post(usuario, "/api/cartoes", cartao("Cartão", "1", 3, 10, antiga, "null"))).hasStatus(422).bodyJson()
                .extractingPath("detail").isEqualTo("Poupança antiga está arquivada. Escolha uma conta ativa.");
        MvcTestResult investimento = api.post(usuario, "/api/cartoes", cartao("Cartão", "1", 3, 10, reserva, "null"));
        assertThat(investimento).hasStatus(422).bodyJson().extractingPath("detail").isEqualTo("Escolha uma conta corrente, poupança ou carteira.");
        assertThat(investimento).bodyJson().extractingPath("codigo").isEqualTo("CONTA_INVALIDA");

        MvcTestResult lista = api.get(usuario, "/api/cartoes");
        assertThat(lista).bodyJson().extractingPath("ativos[0].contaPagamentoArquivada").isEqualTo(true);
        assertThat(api.put(usuario, "/api/cartoes/{id}", cartao("Cartão Lua", "2500", 25, 5, antiga, "null"), cartao)).hasStatus(200);
        assertThat(api.put(usuario, "/api/cartoes/{id}", cartao("Cartão Lua", "2500", 25, 5, horizonte, "null"), cartao)).hasStatus(200);
        assertThat(api.put(usuario, "/api/cartoes/{id}", cartao("Cartão Lua", "2500", 25, 5, antiga, "null"), cartao)).hasStatus(422);
    }

    /** Decisões 5 e 6: o nome único entre os ativos, o limite de 20 com os arquivados, arquivar, reativar e excluir. */
    @Test
    void nomeUnicoLimiteArquivarReativarEExcluir() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novaConta(usuario, "Banco Horizonte");
        long lua = novoCartao(usuario, "Cartão Lua", 25, 5, horizonte);
        assertThat(api.post(usuario, "/api/cartoes", cartao(" cartao LUA ", "1", 3, 10, horizonte, "null"))).hasStatus(422).bodyJson()
                .extractingPath("detail").isEqualTo("Já existe um cartão ativo com este nome.");
        assertThat(api.acao(usuario, "/api/cartoes/{id}/arquivar", lua)).hasStatus(200).bodyJson().extractingPath("arquivadoEm").isNotNull();
        assertThat(api.acao(usuario, "/api/cartoes/{id}/arquivar", lua)).hasStatus(200);
        assertThat(api.put(usuario, "/api/cartoes/{id}", cartao("Cartão Lua", "1", 25, 5, horizonte, "null"), lua)).hasStatus(422)
                .bodyJson().extractingPath("detail").isEqualTo("Reative o cartão antes de editar.");
        long novaLua = novoCartao(usuario, "Cartão Lua", 3, 10, horizonte);
        assertThat(api.acao(usuario, "/api/cartoes/{id}/reativar", lua)).hasStatus(422).bodyJson().extractingPath("detail")
                .isEqualTo("Já existe um cartão ativo chamado Cartão Lua. Renomeie um deles para reativar.");
        assertThat(api.get(usuario, "/api/cartoes")).bodyJson().extractingPath("arquivados[0].id").isEqualTo((int) lua);

        for (int numero = 3; numero <= 20; numero++) {
            novoCartao(usuario, "Cartão " + numero, 3, 10, horizonte);
        }
        assertThat(api.post(usuario, "/api/cartoes", cartao("Cartão 21", "1", 3, 10, horizonte, "null"))).hasStatus(422).bodyJson()
                .extractingPath("detail").isEqualTo("Você chegou ao limite de 20 cartões.");
        assertThat(api.delete(usuario, "/api/cartoes/{id}", novaLua)).hasStatus(204);
        assertThat(api.acao(usuario, "/api/cartoes/{id}/reativar", lua)).hasStatus(200).bodyJson().extractingPath("arquivadoEm").isNull();
        assertThat(api.delete(usuario, "/api/cartoes/{id}", lua)).hasStatus(204);
        assertThat(api.get(usuario, "/api/cartoes/{id}", lua)).hasStatus(404).bodyJson().extractingPath("detail").isEqualTo("Cartão não encontrado.");
    }

    /** Decisão 4: a conta de pagamento não se exclui; a frase diz o papel dela, com e sem lançamentos. */
    @Test
    void aContaDePagamentoNaoSeExclui() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novaConta(usuario, "Banco Horizonte");
        novoCartao(usuario, "Cartão Lua", 25, 5, horizonte);
        MvcTestResult soOCartao = api.delete(usuario, "/api/contas/{id}", horizonte);
        assertThat(soOCartao).hasStatus(422).bodyJson().extractingPath("detail").isEqualTo("Esta conta não pode ser excluída porque é a "
                + "conta de pagamento de 1 cartão. Arquive a conta para tirá-la do saldo total e dos seletores.");
        assertThat(soOCartao).bodyJson().extractingPath("motivo").isEqualTo("é a conta de pagamento de 1 cartão");
        assertThat(soOCartao).bodyJson().extractingPath("codigo").isEqualTo("CONTA_EM_USO");
        assertThat(texto(soOCartao)).doesNotContain("\"uso\"");

        novoCartao(usuario, "Cartão Sol", 3, 10, horizonte);
        api.idCriado(api.post(usuario, "/api/lancamentos", lancamento("DESPESA", "10", "2025-03-10", "Mercado", horizonte, null)));
        MvcTestResult comLancamento = api.delete(usuario, "/api/contas/{id}", horizonte);
        assertThat(comLancamento).hasStatus(422).bodyJson().extractingPath("detail").isEqualTo("Esta conta não pode ser excluída porque "
                + "tem 1 lançamento e é a conta de pagamento de 2 cartões. Arquive a conta para tirá-la do saldo total e dos seletores.");
        assertThat(comLancamento).bodyJson().extractingPath("uso").isEqualTo("1 lançamento");
        assertThat(comLancamento).bodyJson().extractingPath("motivo").isEqualTo("tem 1 lançamento e é a conta de pagamento de 2 cartões");
    }
}
