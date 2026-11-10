package com.flux;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import com.flux.categorias.UsoDaCategoria;
import com.flux.contas.MovimentoDasContas;
import com.flux.contas.UsoDaConta;
import com.flux.suporte.InfraestruturaDeTeste;
import com.flux.suporte.IntegracaoBase;
import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static com.flux.suporte.ApiDeTeste.assertCampo;
import static com.flux.suporte.ApiDeTeste.conta;
import static com.flux.suporte.ApiDeTeste.texto;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * As interfaces que a Fatia 03b implementa em "lancamentos", com implementações falsas
 * (esta classe sobe um contexto próprio). Prova que contas e categorias já consultam o
 * saldo, a data do primeiro lançamento e o uso, sem depender do módulo de lançamentos.
 */
@Import({InfraestruturaDeTeste.class, InterfacesDaFatia03bIT.Falsas.class})
class InterfacesDaFatia03bIT extends IntegracaoBase {

    static final Map<Long, BigDecimal> SOMAS = new ConcurrentHashMap<>();
    static final Map<Long, LocalDate> PRIMEIROS = new ConcurrentHashMap<>();
    static final Map<Long, String> LANCAMENTOS_DA_CONTA = new ConcurrentHashMap<>();
    static final Map<Long, String> CARTOES_DA_CONTA = new ConcurrentHashMap<>();
    static final Map<Long, String> LANCAMENTOS_DA_CATEGORIA = new ConcurrentHashMap<>();
    static final AtomicReference<LocalDate> DATA_PEDIDA = new AtomicReference<>();

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void limpar() {
        SOMAS.clear();
        PRIMEIROS.clear();
        LANCAMENTOS_DA_CONTA.clear();
        CARTOES_DA_CONTA.clear();
        LANCAMENTOS_DA_CATEGORIA.clear();
        DATA_PEDIDA.set(null);
    }

    @Test
    void saldoAtualSomaOsLancamentosAteHoje() {
        // "Hoje" 2 dias à frente do relógio do sistema: prova que a data pedida vem do Clock.
        relogioDeTeste.avancar(Duration.ofDays(2));
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long corrente = api.idCriado(api.post(usuario, "/api/contas", conta("Corrente", "CORRENTE", "100", "2026-10-01", "null")));
        long reserva = api.idCriado(api.post(usuario, "/api/contas", conta("Reserva", "INVESTIMENTO", "1000", "2026-10-01", "null")));
        SOMAS.put(corrente, new BigDecimal("-250.75"));
        SOMAS.put(reserva, new BigDecimal("10.50"));

        MvcTestResult lista = api.get(usuario, "/api/contas");

        assertThat(DATA_PEDIDA.get()).isEqualTo(LocalDate.now(relogioDeTeste));
        assertThat(texto(lista)).contains("\"saldoInicial\":100.00", "\"saldoAtual\":-150.75", "\"saldoAtual\":1010.50",
                "\"totais\":{\"geral\":859.75,\"disponivel\":-150.75,\"investimentos\":1010.50}");
        assertThat(texto(api.get(usuario, "/api/contas/{id}", corrente))).contains("\"saldoAtual\":-150.75");
    }

    @Test
    void dataDoSaldoInicialNaoPassaDoPrimeiroLancamento() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long corrente = api.idCriado(api.post(usuario, "/api/contas", conta("Corrente", "CORRENTE", "100", "2026-10-01", "null")));
        PRIMEIROS.put(corrente, LocalDate.of(2026, 10, 15));

        assertCampo(api.put(usuario, "/api/contas/{id}", conta("Corrente", "CORRENTE", "100", "2026-10-16", "null"), corrente),
                "dataSaldoInicial", "A conta tem lançamentos desde 15/10/2026. A data do saldo inicial precisa ser essa ou anterior.");
        assertThat(api.put(usuario, "/api/contas/{id}", conta("Corrente", "CORRENTE", "100", "2026-10-15", "null"), corrente))
                .hasStatusOk();
        assertThat(api.put(usuario, "/api/contas/{id}", conta("Corrente", "CORRENTE", "250", "2026-09-01", "null"), corrente))
                .hasStatusOk();
    }

    @Test
    void contaUsadaNaoPodeSerExcluida() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long usada = api.idCriado(api.post(usuario, "/api/contas", conta("Usada", "CORRENTE", "0", "2026-10-01", "null")));
        long muitoUsada = api.idCriado(api.post(usuario, "/api/contas", conta("Muito usada", "CORRENTE", "0", "2026-10-01", "null")));
        long livre = api.idCriado(api.post(usuario, "/api/contas", conta("Livre", "CORRENTE", "0", "2026-10-01", "null")));
        LANCAMENTOS_DA_CONTA.put(usada, "128 lançamentos");
        LANCAMENTOS_DA_CONTA.put(muitoUsada, "3 lançamentos");
        CARTOES_DA_CONTA.put(muitoUsada, "2 cartões");

        MvcTestResult recusada = api.delete(usuario, "/api/contas/{id}", usada);
        assertThat(recusada).hasStatus(422).bodyJson().extractingPath("codigo").isEqualTo("CONTA_EM_USO");
        assertThat(recusada).bodyJson().extractingPath("detail").isEqualTo(
                "Esta conta não pode ser excluída porque tem 128 lançamentos. Arquive a conta para tirá-la do saldo total e dos seletores.");
        assertThat(api.delete(usuario, "/api/contas/{id}", muitoUsada)).bodyJson().extractingPath("detail").isEqualTo(
                "Esta conta não pode ser excluída porque tem 3 lançamentos e 2 cartões. Arquive a conta para tirá-la do saldo total e dos seletores.");
        assertThat(api.delete(usuario, "/api/contas/{id}", livre)).hasStatus(204);
        assertThat(jdbc.queryForObject("select count(*) from contas where usuario_id = ?", Integer.class, usuario.id())).isEqualTo(2);
    }

    @Test
    void categoriaUsadaNaoPodeSerExcluida() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long alimentacao = id(usuario, "Alimentação");
        long saude = id(usuario, "Saúde");
        LANCAMENTOS_DA_CATEGORIA.put(id(usuario, "Mercado"), "42 lançamentos");
        LANCAMENTOS_DA_CATEGORIA.put(saude, "3 lançamentos");

        MvcTestResult principal = api.delete(usuario, "/api/categorias/{id}", alimentacao);
        assertThat(principal).hasStatus(422).bodyJson().extractingPath("codigo").isEqualTo("CATEGORIA_EM_USO");
        assertThat(principal).bodyJson().extractingPath("detail").isEqualTo(
                "Esta categoria não pode ser excluída porque ela e as subcategorias têm 42 lançamentos. Arquive a categoria para tirá-la dos seletores.");
        assertThat(api.delete(usuario, "/api/categorias/{id}", saude)).bodyJson().extractingPath("detail").isEqualTo(
                "Esta categoria não pode ser excluída porque tem 3 lançamentos. Arquive a categoria para tirá-la dos seletores.");
        assertThat(api.delete(usuario, "/api/categorias/{id}", id(usuario, "Lazer"))).hasStatus(204);
        assertThat(jdbc.queryForObject("select count(*) from categorias where usuario_id = ?", Integer.class, usuario.id())).isEqualTo(23);
    }

    private long id(UsuarioDeTeste usuario, String nome) {
        return jdbc.queryForObject("select id from categorias where usuario_id = ? and nome = ?", Long.class, usuario.id(), nome);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Falsas {

        @Bean
        MovimentoDasContas movimentoFalso() {
            return new MovimentoDasContas() {
                @Override
                public Map<Long, BigDecimal> somaPorContaAte(Long usuarioId, LocalDate data) {
                    DATA_PEDIDA.set(data);
                    return Map.copyOf(SOMAS);
                }

                @Override
                public Optional<LocalDate> dataDoPrimeiroLancamento(Long usuarioId, Long contaId) {
                    return Optional.ofNullable(PRIMEIROS.get(contaId));
                }
            };
        }

        @Bean
        UsoDaConta lancamentosDaConta() {
            return new UsoOrdenado(1, LANCAMENTOS_DA_CONTA);
        }

        @Bean
        UsoDaConta cartoesDaConta() {
            return new UsoOrdenado(2, CARTOES_DA_CONTA);
        }

        @Bean
        UsoDaCategoria lancamentosDaCategoria() {
            return (Long usuarioId, Collection<Long> ids) -> ids.stream()
                    .map(LANCAMENTOS_DA_CATEGORIA::get).filter(uso -> uso != null).findFirst();
        }
    }

    record UsoOrdenado(int ordem, Map<Long, String> usos) implements UsoDaConta, Ordered {

        @Override
        public Optional<String> uso(Long usuarioId, Long contaId) {
            return Optional.ofNullable(usos.get(contaId));
        }

        @Override
        public int getOrder() {
            return ordem;
        }
    }
}
