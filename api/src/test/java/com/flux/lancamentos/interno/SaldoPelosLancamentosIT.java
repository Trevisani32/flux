package com.flux.lancamentos.interno;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import com.flux.suporte.IntegracaoBase;
import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static com.flux.suporte.ApiDeTeste.assertCampo;
import static com.flux.suporte.ApiDeTeste.conta;
import static com.flux.suporte.ApiDeTeste.lancamento;
import static com.flux.suporte.ApiDeTeste.texto;
import static com.flux.suporte.ApiDeTeste.transferencia;
import static org.assertj.core.api.Assertions.assertThat;

/** O saldo vem dos lançamentos (ADR 0003), com a data do saldo inicial e a data futura. */
class SaldoPelosLancamentosIT extends IntegracaoBase {

    private static final DateTimeFormatter DATA_CURTA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Test
    void saldoAtualSomaTodosOsTiposAteHojeENaoOsFuturos() {
        // "Hoje" 2 dias à frente do relógio do sistema, antes de criar o usuário (o token usa o
        // mesmo relógio): um LocalDate.now() sem o Clock daria outro dia em qualquer fuso.
        relogioDeTeste.avancar(Duration.ofDays(2));
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        LocalDate hoje = LocalDate.now(relogioDeTeste);
        long horizonte = api.idCriado(api.post(usuario, "/api/contas", conta("Banco Horizonte", "CORRENTE", "100", hoje.minusDays(10).toString(), "null")));
        long poupanca = api.idCriado(api.post(usuario, "/api/contas", conta("Poupança", "POUPANCA", "0", hoje.minusDays(10).toString(), "null")));
        api.idCriado(api.post(usuario, "/api/lancamentos", lancamento("RECEITA", "50", hoje.minusDays(5).toString(), "Freela", horizonte, null)));
        api.idCriado(api.post(usuario, "/api/lancamentos", lancamento("DESPESA", "30", hoje.toString(), "Hoje", horizonte, null)));
        MvcTestResult futuro = api.post(usuario, "/api/lancamentos", lancamento("DESPESA", "20", hoje.plusDays(1).toString(), "Amanhã", horizonte, null));
        api.idCriado(api.post(usuario, "/api/transferencias", transferencia("10", hoje.minusDays(1).toString(), horizonte, poupanca, null)));

        assertThat(texto(futuro)).contains("\"futuro\":true");
        assertThat(texto(api.get(usuario, "/api/contas"))).contains("\"saldoAtual\":110.00", "\"saldoAtual\":10.00",
                "\"totais\":{\"geral\":120.00,\"disponivel\":120.00,\"investimentos\":0.00}");
    }

    @Test
    void lancamentoNoDiaDoSaldoInicialEntraEOAnteriorERecusado() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = api.idCriado(api.post(usuario, "/api/contas", conta("Banco Horizonte", "CORRENTE", "100", "2026-10-01", "null")));

        assertThat(api.post(usuario, "/api/lancamentos", lancamento("DESPESA", "40", "2026-10-01", "No dia", horizonte, null))).hasStatus(201);
        assertCampo(api.post(usuario, "/api/lancamentos", lancamento("DESPESA", "40", "2026-09-30", "Antes", horizonte, null)),
                "data", "A data precisa ser 01/10/2026 ou depois, a data do saldo inicial de Banco Horizonte.");
        assertThat(texto(api.get(usuario, "/api/contas/{id}", horizonte))).contains("\"saldoAtual\":60.00");
    }

    @Test
    void dataFuturaVaiAteDozeMesesDepoisDeHoje() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        LocalDate hoje = LocalDate.now(relogioDeTeste);
        LocalDate limite = hoje.plusMonths(12);
        long horizonte = api.idCriado(api.post(usuario, "/api/contas", conta("Banco Horizonte", "CORRENTE", "0", hoje.toString(), "null")));

        assertThat(texto(api.post(usuario, "/api/lancamentos", lancamento("DESPESA", "1", limite.toString(), "No limite", horizonte, null))))
                .contains("\"futuro\":true");
        assertCampo(api.post(usuario, "/api/lancamentos", lancamento("DESPESA", "1", limite.plusDays(1).toString(), "Depois", horizonte, null)),
                "data", "A data pode ir até " + limite.format(DATA_CURTA) + ".");
        assertThat(texto(api.get(usuario, "/api/contas/{id}", horizonte))).contains("\"saldoAtual\":0.00");
    }

    /** Decisão 14 da 03a: a data do saldo inicial não passa do lançamento mais antigo. */
    @Test
    void dataDoSaldoInicialNaoPassaDoPrimeiroLancamentoNemDaTransferencia() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = api.idCriado(api.post(usuario, "/api/contas", conta("Banco Horizonte", "CORRENTE", "100", "2026-10-01", "null")));
        long poupanca = api.idCriado(api.post(usuario, "/api/contas", conta("Poupança", "POUPANCA", "0", "2026-10-01", "null")));
        api.idCriado(api.post(usuario, "/api/lancamentos", lancamento("DESPESA", "40", "2026-10-15", "Primeiro", horizonte, null)));

        assertCampo(api.put(usuario, "/api/contas/{id}", conta("Banco Horizonte", "CORRENTE", "100", "2026-10-16", "null"), horizonte),
                "dataSaldoInicial", "A conta tem lançamentos desde 15/10/2026. A data do saldo inicial precisa ser essa ou anterior.");
        assertThat(api.put(usuario, "/api/contas/{id}", conta("Banco Horizonte", "CORRENTE", "250", "2026-10-15", "null"), horizonte)).hasStatusOk();
        assertThat(api.put(usuario, "/api/contas/{id}", conta("Banco Horizonte", "CORRENTE", "250", "2026-10-01", "null"), horizonte)).hasStatusOk();

        // O lado de uma transferência também conta como lançamento da conta.
        api.idCriado(api.post(usuario, "/api/transferencias", transferencia("5", "2026-10-12", poupanca, horizonte, null)));
        assertCampo(api.put(usuario, "/api/contas/{id}", conta("Banco Horizonte", "CORRENTE", "250", "2026-10-13", "null"), horizonte),
                "dataSaldoInicial", "A conta tem lançamentos desde 12/10/2026. A data do saldo inicial precisa ser essa ou anterior.");
        assertThat(texto(api.get(usuario, "/api/contas/{id}", horizonte))).contains("\"saldoAtual\":215.00");
    }
}
