package com.flux.contas.interno;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.flux.suporte.IntegracaoBase;
import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;

import static com.flux.suporte.ApiDeTeste.assertCampo;
import static com.flux.suporte.ApiDeTeste.conta;
import static com.flux.suporte.ApiDeTeste.texto;
import static org.assertj.core.api.Assertions.assertThat;

class ContasIT extends IntegracaoBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void criaContaEDevolveOSaldoAtualIgualAoInicial() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();

        MvcTestResult resposta = api.post(usuario, "/api/contas", conta("  Banco   Horizonte ", "CORRENTE", "4218.4", "2026-10-01", "\"ROXO\""));

        assertThat(resposta).hasStatus(201);
        JsonNode conta = api.json(resposta);
        assertThat(resposta.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo("/api/contas/" + conta.get("id").asLong());
        assertThat(conta.get("nome").asString()).isEqualTo("Banco Horizonte");
        assertThat(conta.get("tipo").asString()).isEqualTo("CORRENTE");
        assertThat(conta.get("dataSaldoInicial").asString()).isEqualTo("2026-10-01");
        assertThat(conta.get("cor").asString()).isEqualTo("ROXO");
        assertThat(conta.get("arquivadaEm").isNull()).isTrue();
        assertThat(texto(resposta)).contains("\"saldoInicial\":4218.40", "\"saldoAtual\":4218.40");
        assertThat(jdbc.queryForObject("select nome_chave from contas where id = ?", String.class, conta.get("id").asLong()))
                .isEqualTo("banco horizonte");
        assertThat(api.get(usuario, "/api/contas/{id}", conta.get("id").asLong())).hasStatusOk()
                .bodyJson().extractingPath("nome").isEqualTo("Banco Horizonte");
    }

    @Test
    void validaOsCamposComAsMensagensDaTela() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();

        assertCampo(api.post(usuario, "/api/contas", conta("   ", "CORRENTE", "0", "2026-10-01", "null")), "nome", "Informe o nome.");
        assertCampo(api.post(usuario, "/api/contas", conta("a".repeat(61), "CORRENTE", "0", "2026-10-01", "null")),
                "nome", "Use no máximo 60 caracteres.");
        assertCampo(api.post(usuario, "/api/contas", "{\"nome\":\"Sem tipo\",\"saldoInicial\":0,\"dataSaldoInicial\":\"2026-10-01\"}"),
                "tipo", "Escolha o tipo.");
        assertCampo(api.post(usuario, "/api/contas", "{\"nome\":\"Sem saldo\",\"tipo\":\"CORRENTE\",\"dataSaldoInicial\":\"2026-10-01\"}"),
                "saldoInicial", "Informe o saldo inicial.");
        assertCampo(api.post(usuario, "/api/contas", conta("Três casas", "CORRENTE", "10.005", "2026-10-01", "null")),
                "saldoInicial", "Use no máximo 2 casas decimais.");
        assertCampo(api.post(usuario, "/api/contas", conta("Grande", "CORRENTE", "99999999999999", "2026-10-01", "null")),
                "saldoInicial", "Use no máximo 13 dígitos antes da vírgula.");
        assertCampo(api.post(usuario, "/api/contas", conta("Pequeno", "CORRENTE", "-99999999999999", "2026-10-01", "null")),
                "saldoInicial", "Use no máximo 13 dígitos antes da vírgula.");
        assertCampo(api.post(usuario, "/api/contas", "{\"nome\":\"Sem data\",\"tipo\":\"CORRENTE\",\"saldoInicial\":0}"),
                "dataSaldoInicial", "Informe a data do saldo inicial.");
        assertThat(api.post(usuario, "/api/contas", conta("Tipo errado", "XPTO", "0", "2026-10-01", "null"))).hasStatus(400)
                .bodyJson().extractingPath("title").isEqualTo("Requisição inválida");
        assertThat(api.post(usuario, "/api/contas", conta("Cor errada", "CORRENTE", "0", "2026-10-01", "\"VERDE\""))).hasStatus(400)
                .bodyJson().extractingPath("title").isEqualTo("Requisição inválida");
        assertThat(api.post(usuario, "/api/contas", conta("No limite", "INVESTIMENTO", "9999999999999.99", "2026-10-01", "null")))
                .hasStatus(201);
        assertThat(jdbc.queryForObject("select count(*) from contas where usuario_id = ?", Integer.class, usuario.id())).isEqualTo(1);
    }

    @Test
    void dataDoSaldoInicialAteHojeNoFusoDoNegocio() {
        // O "hoje" da aplicação fica 2 dias à frente do relógio do sistema: um LocalDate.now()
        // sem o Clock recusaria "hoje". Avança antes de criar o usuário, porque o token de
        // acesso é conferido pelo mesmo relógio.
        relogioDeTeste.avancar(Duration.ofDays(2));
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        LocalDate hoje = LocalDate.now(relogioDeTeste);

        assertCampo(api.post(usuario, "/api/contas", conta("Amanhã", "CORRENTE", "0", hoje.plusDays(1).toString(), "null")),
                "dataSaldoInicial", "A data não pode ser depois de hoje.");
        assertThat(api.post(usuario, "/api/contas", conta("Hoje", "CORRENTE", "0", hoje.toString(), "null"))).hasStatus(201);
    }

    @Test
    void nomeUnicoEntreAsAtivasSemDiferenciarMaiusculasAcentosEEspacos() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long poupanca = api.idCriado(api.post(usuario, "/api/contas", conta("Poupança", "POUPANCA", "10", "2026-10-01", "null")));

        assertThat(api.post(usuario, "/api/contas", conta("  POUPANCA ", "CORRENTE", "0", "2026-10-01", "null"))).hasStatus(422)
                .bodyJson().extractingPath("codigo").isEqualTo("NOME_EM_USO");
        assertThat(api.post(usuario, "/api/contas", conta("poupança", "CORRENTE", "0", "2026-10-01", "null")))
                .bodyJson().extractingPath("detail").isEqualTo("Já existe uma conta ativa com este nome.");
        assertThat(api.acao(usuario, "/api/contas/{id}/arquivar", poupanca)).hasStatusOk();
        assertThat(api.post(usuario, "/api/contas", conta("poupança", "CORRENTE", "0", "2026-10-01", "null"))).hasStatus(201);
        assertThat(api.post(usuario, "/api/contas", conta("Poupança 2", "CORRENTE", "0", "2026-10-01", "null"))).hasStatus(201);
    }

    @Test
    void editarTrocaTodosOsCamposEConfereONome() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        api.idCriado(api.post(usuario, "/api/contas", conta("Carteira", "CARTEIRA", "180", "2026-11-01", "null")));
        long conjunta = api.idCriado(api.post(usuario, "/api/contas", conta("Conta conjunta", "CORRENTE", "0", "2026-09-01", "\"ROSA\"")));

        assertThat(api.put(usuario, "/api/contas/{id}", conta("carteira", "CORRENTE", "0", "2026-09-01", "null"), conjunta))
                .hasStatus(422).bodyJson().extractingPath("codigo").isEqualTo("NOME_EM_USO");
        MvcTestResult editada = api.put(usuario, "/api/contas/{id}", conta("CONTA CONJUNTA", "POUPANCA", "-120.5", "2026-08-15", "null"), conjunta);
        assertThat(editada).hasStatusOk();
        JsonNode conta = api.json(editada);
        assertThat(conta.get("nome").asString()).isEqualTo("CONTA CONJUNTA");
        assertThat(conta.get("tipo").asString()).isEqualTo("POUPANCA");
        assertThat(conta.get("dataSaldoInicial").asString()).isEqualTo("2026-08-15");
        assertThat(conta.get("cor").isNull()).isTrue();
        assertThat(texto(editada)).contains("\"saldoInicial\":-120.50", "\"saldoAtual\":-120.50");

        assertThat(api.acao(usuario, "/api/contas/{id}/arquivar", conjunta)).hasStatusOk();
        MvcTestResult arquivada = api.put(usuario, "/api/contas/{id}", conta("Outra", "CORRENTE", "0", "2026-09-01", "null"), conjunta);
        assertThat(arquivada).hasStatus(422).bodyJson().extractingPath("codigo").isEqualTo("CONTA_ARQUIVADA");
        assertThat(arquivada).bodyJson().extractingPath("detail").isEqualTo("Reative a conta antes de editar.");
        assertThat(api.put(usuario, "/api/contas/{id}", conta("Outra", "CORRENTE", "0", "2026-09-01", "null"), Long.MAX_VALUE))
                .hasStatus(404).bodyJson().extractingPath("detail").isEqualTo("Conta não encontrada.");
    }

    @Test
    void arquivarEReativarSaoIdempotentesEConferemONome() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long reserva = api.idCriado(api.post(usuario, "/api/contas", conta("Reserva", "INVESTIMENTO", "100", "2026-10-01", "null")));

        MvcTestResult arquivada = api.acao(usuario, "/api/contas/{id}/arquivar", reserva);
        assertThat(arquivada).hasStatusOk();
        String arquivadaEm = api.json(arquivada).get("arquivadaEm").asString();
        assertThat(arquivadaEm).isNotBlank();
        assertThat(api.json(api.acao(usuario, "/api/contas/{id}/arquivar", reserva)).get("arquivadaEm").asString()).isEqualTo(arquivadaEm);
        JsonNode lista = api.json(api.get(usuario, "/api/contas"));
        assertThat(lista.get("ativas").size()).isZero();
        assertThat(lista.get("arquivadas").get(0).get("id").asLong()).isEqualTo(reserva);

        long outra = api.idCriado(api.post(usuario, "/api/contas", conta("reserva", "CORRENTE", "0", "2026-10-01", "null")));
        MvcTestResult conflito = api.acao(usuario, "/api/contas/{id}/reativar", reserva);
        assertThat(conflito).hasStatus(422).bodyJson().extractingPath("codigo").isEqualTo("NOME_EM_USO");
        assertThat(conflito).bodyJson().extractingPath("detail")
                .isEqualTo("Já existe uma conta ativa com este nome. Renomeie a conta ativa e tente de novo.");
        assertThat(jdbc.queryForObject("select arquivada_em is not null from contas where id = ?", Boolean.class, reserva)).isTrue();

        assertThat(api.put(usuario, "/api/contas/{id}", conta("Corrente", "CORRENTE", "0", "2026-10-01", "null"), outra)).hasStatusOk();
        MvcTestResult reativada = api.acao(usuario, "/api/contas/{id}/reativar", reserva);
        assertThat(reativada).hasStatusOk();
        assertThat(api.json(reativada).get("arquivadaEm").isNull()).isTrue();
        assertThat(api.acao(usuario, "/api/contas/{id}/reativar", reserva)).hasStatusOk();
        assertThat(api.json(api.get(usuario, "/api/contas")).get("ativas").size()).isEqualTo(2);
    }

    @Test
    void excluiAtivaEArquivada() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long ativa = api.idCriado(api.post(usuario, "/api/contas", conta("Ativa", "CORRENTE", "50", "2026-10-01", "null")));
        long arquivada = api.idCriado(api.post(usuario, "/api/contas", conta("Arquivada", "CORRENTE", "0", "2026-10-01", "null")));
        api.acao(usuario, "/api/contas/{id}/arquivar", arquivada);

        assertThat(api.delete(usuario, "/api/contas/{id}", ativa)).hasStatus(204);
        assertThat(api.delete(usuario, "/api/contas/{id}", arquivada)).hasStatus(204);
        assertThat(api.get(usuario, "/api/contas/{id}", ativa)).hasStatus(404)
                .bodyJson().extractingPath("detail").isEqualTo("Conta não encontrada.");
        assertThat(api.delete(usuario, "/api/contas/{id}", ativa)).hasStatus(404);
        assertThat(jdbc.queryForObject("select count(*) from contas where usuario_id = ?", Integer.class, usuario.id())).isZero();
    }

    @Test
    void listaEmOrdemAlfabeticaComOsTotaisDasAtivas() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        api.idCriado(api.post(usuario, "/api/contas", conta("Reserva de emergência", "INVESTIMENTO", "15000", "2026-08-15", "\"AZUL\"")));
        api.idCriado(api.post(usuario, "/api/contas", conta("Banco Horizonte", "CORRENTE", "4218.40", "2026-10-01", "\"ROXO\"")));
        api.idCriado(api.post(usuario, "/api/contas", conta("álamo", "CARTEIRA", "180", "2026-11-01", "null")));
        api.idCriado(api.post(usuario, "/api/contas", conta("Conta conjunta", "CORRENTE", "-120", "2026-09-01", "\"ROSA\"")));
        api.idCriado(api.post(usuario, "/api/contas", conta("Poupança", "POUPANCA", "3902.05", "2026-10-01", "\"CIANO\"")));
        long antiga = api.idCriado(api.post(usuario, "/api/contas", conta("Antiga", "CORRENTE", "999.99", "2026-01-01", "null")));
        api.acao(usuario, "/api/contas/{id}/arquivar", antiga);

        MvcTestResult resposta = api.get(usuario, "/api/contas");

        assertThat(resposta).hasStatusOk();
        JsonNode lista = api.json(resposta);
        assertThat(nomes(lista.get("ativas"))).containsExactly("álamo", "Banco Horizonte", "Conta conjunta", "Poupança",
                "Reserva de emergência");
        assertThat(nomes(lista.get("arquivadas"))).containsExactly("Antiga");
        assertThat(texto(resposta)).contains("\"totais\":{\"geral\":23180.45,\"disponivel\":8180.45,\"investimentos\":15000.00}");
    }

    @Test
    void semContaOsTotaisVemZerados() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();

        MvcTestResult resposta = api.get(usuario, "/api/contas");

        assertThat(resposta).hasStatusOk();
        assertThat(texto(resposta)).isEqualTo(
                "{\"ativas\":[],\"arquivadas\":[],\"totais\":{\"geral\":0.00,\"disponivel\":0.00,\"investimentos\":0.00}}");
    }

    @Test
    void limiteDe50ContasContaAsArquivadas() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        for (int i = 1; i <= 49; i++) {
            jdbc.update("insert into contas (usuario_id, nome, nome_chave, tipo, saldo_inicial, data_saldo_inicial, arquivada_em) "
                    + "values (?, ?, ?, 'CORRENTE', 0, date '2026-01-01', case when ? % 2 = 0 then now() end)",
                    usuario.id(), "Conta " + i, "conta " + i, i);
        }

        assertThat(api.post(usuario, "/api/contas", conta("Quinquagésima", "CORRENTE", "0", "2026-10-01", "null"))).hasStatus(201);
        MvcTestResult excedeu = api.post(usuario, "/api/contas", conta("Mais uma", "CORRENTE", "0", "2026-10-01", "null"));
        assertThat(excedeu).hasStatus(422).bodyJson().extractingPath("codigo").isEqualTo("LIMITE_DE_CONTAS");
        assertThat(excedeu).bodyJson().extractingPath("detail").isEqualTo("Você chegou ao limite de 50 contas.");
    }

    @Test
    void doisPedidosSimultaneosComOMesmoNomeCriamUmaContaSo() throws Exception {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> pedidos = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                pedidos.add(executor.submit(() -> {
                    largada.await();
                    return api.post(usuario, "/api/contas", conta("Simultânea", "CORRENTE", "0", "2026-10-01", "null"))
                            .getResponse().getStatus();
                }));
            }
            largada.countDown();
            List<Integer> status = new ArrayList<>();
            for (Future<Integer> pedido : pedidos) {
                status.add(pedido.get());
            }
            Collections.sort(status);
            assertThat(status).containsExactly(201, 422);
        } finally {
            executor.shutdownNow();
        }
        assertThat(jdbc.queryForObject("select count(*) from contas where usuario_id = ?", Integer.class, usuario.id())).isEqualTo(1);
    }

    @Test
    void semTokenDa401EIdInvalidoDa400() {
        assertThat(mvc.get().uri("/api/contas")).hasStatus(401);
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        assertThat(api.get(usuario, "/api/contas/abc")).hasStatus(400);
    }

    private static List<String> nomes(JsonNode contas) {
        List<String> nomes = new ArrayList<>();
        contas.forEach(conta -> nomes.add(conta.get("nome").asString()));
        return nomes;
    }
}
