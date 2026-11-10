package com.flux.lancamentos.interno;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.flux.suporte.IntegracaoBase;
import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;

import static com.flux.suporte.ApiDeTeste.assertCampo;
import static com.flux.suporte.ApiDeTeste.conta;
import static com.flux.suporte.ApiDeTeste.lancamento;
import static com.flux.suporte.ApiDeTeste.texto;
import static com.flux.suporte.ApiDeTeste.transferencia;
import static org.assertj.core.api.Assertions.assertThat;

class ListaDoMesIT extends IntegracaoBase {

    @Autowired
    private JdbcTemplate jdbc;

    private record Cenario(UsuarioDeTeste usuario, long horizonte, long carteira) {
    }

    /**
     * Banco Horizonte: R$ 1.000,00 em 01/09/2026 e uma despesa de R$ 100,00 em setembro.
     * Carteira: R$ 100,00 em 01/10/2026. Outubro: 7 lançamentos e 1 transferência; novembro: 1.
     */
    private Cenario cenario() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = api.idCriado(api.post(usuario, "/api/contas", conta("Banco Horizonte", "CORRENTE", "1000", "2026-09-01", "null")));
        long carteira = api.idCriado(api.post(usuario, "/api/contas", conta("Carteira", "CARTEIRA", "100", "2026-10-01", "null")));
        criar(usuario, lancamento("DESPESA", "100", "2026-09-20", "Setembro", horizonte, null));
        criar(usuario, lancamento("DESPESA", "184.37", "2026-10-03", "Conta de luz", horizonte, categoria(usuario, "Contas da casa")));
        criar(usuario, lancamento("RECEITA", "1250", "2026-10-04", "Freelance — site", horizonte, categoria(usuario, "Freelance")));
        criar(usuario, lancamento("RECEITA", "7200", "2026-10-05", "Salário", horizonte, categoria(usuario, "Salário")));
        criar(usuario, lancamento("DESPESA", "2100", "2026-10-05", "Aluguel", horizonte, categoria(usuario, "Aluguel")));
        api.idCriado(api.post(usuario, "/api/transferencias", transferencia("1000", "2026-10-05", horizonte, carteira, "Reserva")));
        criar(usuario, lancamento("DESPESA", "287.43", "2026-10-06", "Mercado Bom Preço", horizonte, categoria(usuario, "Mercado")));
        criar(usuario, lancamento("DESPESA", "42", "2026-10-07", "Almoço", carteira, categoria(usuario, "Alimentação")));
        criar(usuario, lancamento("DESPESA", "18.50", "2026-10-09", "Padaria Central", carteira, null));
        criar(usuario, lancamento("DESPESA", "50", "2026-11-02", "Novembro", horizonte, null));
        return new Cenario(usuario, horizonte, carteira);
    }

    @Test
    void semFiltrosTrazOMesInteiroNaOrdemComOsTotaisSemTransferencias() {
        Cenario cenario = cenario();
        MvcTestResult resposta = api.get(cenario.usuario(), "/api/lancamentos?mes=2026-10");

        assertThat(resposta).hasStatusOk();
        assertThat(texto(resposta)).contains("\"mes\":\"2026-10\"",
                "\"totais\":{\"entradas\":8450.00,\"quantidadeEntradas\":2,\"saidas\":-2632.30,\"quantidadeSaidas\":5,\"resultado\":5817.70}",
                "\"quantidadeLancamentos\":7,\"quantidadeTransferencias\":1");
        JsonNode json = api.json(resposta);
        assertThat(campos(json, "descricao")).containsExactly("Padaria Central", "Almoço", "Mercado Bom Preço", "Reserva", "Aluguel",
                "Salário", "Freelance — site", "Conta de luz");
        assertThat(datasDosDias(json)).containsExactly("2026-10-09", "2026-10-07", "2026-10-06", "2026-10-05", "2026-10-04", "2026-10-03");
        assertThat(saldosDosDias(resposta)).containsOnly("null").hasSize(6);
        assertThat(texto(resposta)).contains("\"tipo\":\"TRANSFERENCIA\",\"id\":", "\"descricao\":\"Reserva\",\"valor\":1000.00",
                "\"contaNome\":\"Banco Horizonte\"", "\"contaDestinoNome\":\"Carteira\",\"direcao\":null");
    }

    @Test
    void comAContaOSaldoNoFimDoDiaContaTudoDelaEOsTotaisSoDela() {
        Cenario cenario = cenario();
        MvcTestResult horizonte = api.get(cenario.usuario(), "/api/lancamentos?mes=2026-10&conta={id}", cenario.horizonte());

        assertThat(texto(horizonte)).contains(
                "\"totais\":{\"entradas\":8450.00,\"quantidadeEntradas\":2,\"saidas\":-2571.80,\"quantidadeSaidas\":3,\"resultado\":5878.20}",
                "\"quantidadeLancamentos\":5,\"quantidadeTransferencias\":1", "\"direcao\":\"SAIDA\"");
        JsonNode json = api.json(horizonte);
        assertThat(datasDosDias(json)).containsExactly("2026-10-06", "2026-10-05", "2026-10-04", "2026-10-03");
        // 1.000,00 de saldo inicial − 100,00 de setembro = 900,00 no começo de outubro.
        assertThat(saldosDosDias(horizonte)).containsExactly("5778.20", "6065.63", "1965.63", "715.63");

        MvcTestResult respostaDaCarteira = api.get(cenario.usuario(), "/api/lancamentos?mes=2026-10&conta={id}", cenario.carteira());
        JsonNode carteira = api.json(respostaDaCarteira);
        assertThat(campos(carteira, "descricao")).containsExactly("Padaria Central", "Almoço", "Reserva");
        assertThat(saldosDosDias(respostaDaCarteira)).containsExactly("1039.50", "1058.00", "1100.00");
        assertThat(campos(carteira, "direcao")).containsExactly("null", "null", "ENTRADA");

        // Os outros filtros não mudam o saldo do dia: ele é da conta inteira.
        MvcTestResult buscaNaConta = api.get(cenario.usuario(), "/api/lancamentos?mes=2026-10&conta={id}&busca=mercado", cenario.horizonte());
        assertThat(campos(api.json(buscaNaConta), "descricao")).containsExactly("Mercado Bom Preço");
        assertThat(saldosDosDias(buscaNaConta)).containsExactly("5778.20");
    }

    @Test
    void filtrosDeCategoriaTipoEBusca() {
        Cenario cenario = cenario();
        UsuarioDeTeste usuario = cenario.usuario();

        assertThat(descricoes(usuario, "&categoria=" + categoria(usuario, "Alimentação"))).containsExactly("Almoço", "Mercado Bom Preço");
        assertThat(descricoes(usuario, "&categoria=" + categoria(usuario, "Mercado"))).containsExactly("Mercado Bom Preço");
        assertThat(descricoes(usuario, "&categoria=sem")).containsExactly("Padaria Central");
        assertThat(descricoes(usuario, "&tipo=receita")).containsExactly("Salário", "Freelance — site");
        assertThat(descricoes(usuario, "&tipo=despesa")).containsExactly("Padaria Central", "Almoço", "Mercado Bom Preço", "Aluguel", "Conta de luz");
        assertThat(descricoes(usuario, "&busca=MERCADO  bom")).containsExactly("Mercado Bom Preço");
        assertThat(descricoes(usuario, "&busca=salario")).containsExactly("Salário");
        assertThat(descricoes(usuario, "&busca=reserva")).containsExactly("Reserva");
        assertThat(descricoes(usuario, "&tipo=despesa&categoria=sem&conta=" + cenario.carteira())).containsExactly("Padaria Central");

        MvcTestResult transferencias = api.get(usuario, "/api/lancamentos?mes=2026-10&tipo=transferencia");
        assertThat(texto(transferencias)).contains(
                "\"totais\":{\"entradas\":0.00,\"quantidadeEntradas\":0,\"saidas\":0.00,\"quantidadeSaidas\":0,\"resultado\":0.00}",
                "\"quantidadeLancamentos\":0,\"quantidadeTransferencias\":1");
        assertThat(campos(api.json(transferencias), "descricao")).containsExactly("Reserva");
    }

    @Test
    void mesVazioMesPadraoEParametrosInvalidos() {
        Cenario cenario = cenario();
        UsuarioDeTeste usuario = cenario.usuario();

        assertThat(texto(api.get(usuario, "/api/lancamentos?mes=2026-08"))).contains("\"mes\":\"2026-08\"",
                "\"totais\":{\"entradas\":0.00,\"quantidadeEntradas\":0,\"saidas\":0.00,\"quantidadeSaidas\":0,\"resultado\":0.00}",
                "\"dias\":[]");
        LocalDate hoje = LocalDate.now(relogioDeTeste);
        assertThat(texto(api.get(usuario, "/api/lancamentos"))).contains("\"mes\":\"" + YearMonth.from(hoje) + "\"",
                "\"hoje\":\"" + hoje + "\"");
        assertThat(api.get(usuario, "/api/lancamentos?mes=2026-13")).hasStatus(400);
        assertThat(api.get(usuario, "/api/lancamentos?conta=abc")).hasStatus(400);
        assertCampo(api.get(usuario, "/api/lancamentos?tipo=outro"), "tipo", "Filtro de tipo inválido.");
        assertCampo(api.get(usuario, "/api/lancamentos?categoria=abc"), "categoria", "Filtro de categoria inválido.");
        assertCampo(api.get(usuario, "/api/lancamentos?busca=" + "x".repeat(101)), "busca", "Use no máximo 100 caracteres.");
    }

    @Test
    void ultimosSaoOsCincoMaisRecentesAteHojeComATransferenciaNumItem() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        LocalDate hoje = LocalDate.now(relogioDeTeste);
        long horizonte = api.idCriado(api.post(usuario, "/api/contas", conta("Banco Horizonte", "CORRENTE", "0", hoje.minusDays(30).toString(), "null")));
        long poupanca = api.idCriado(api.post(usuario, "/api/contas", conta("Poupança", "POUPANCA", "0", hoje.minusDays(30).toString(), "null")));
        criar(usuario, lancamento("DESPESA", "1", hoje.minusDays(10).toString(), "A", horizonte, null));
        criar(usuario, lancamento("DESPESA", "1", hoje.minusDays(5).toString(), "B", horizonte, null));
        api.idCriado(api.post(usuario, "/api/transferencias", transferencia("1", hoje.minusDays(3).toString(), horizonte, poupanca, "T")));
        criar(usuario, lancamento("DESPESA", "1", hoje.minusDays(2).toString(), "C", horizonte, null));
        criar(usuario, lancamento("DESPESA", "1", hoje.minusDays(1).toString(), "D", horizonte, null));
        criar(usuario, lancamento("DESPESA", "1", hoje.toString(), "E", horizonte, null));
        criar(usuario, lancamento("DESPESA", "1", hoje.plusDays(1).toString(), "F", horizonte, null));

        JsonNode ultimos = api.json(api.get(usuario, "/api/lancamentos/ultimos"));
        List<String> descricoes = new ArrayList<>();
        ultimos.forEach(item -> descricoes.add(item.get("descricao").asString()));
        assertThat(descricoes).containsExactly("E", "D", "C", "T", "B");
        assertThat(texto(api.get(usuario, "/api/lancamentos?mes={mes}", YearMonth.from(hoje.plusDays(1)))))
                .contains("\"descricao\":\"F\",\"valor\":-1.00");
    }

    private void criar(UsuarioDeTeste usuario, String corpo) {
        api.idCriado(api.post(usuario, "/api/lancamentos", corpo));
    }

    private long categoria(UsuarioDeTeste usuario, String nome) {
        return jdbc.queryForObject("select id from categorias where usuario_id = ? and nome = ?", Long.class, usuario.id(), nome);
    }

    private List<String> descricoes(UsuarioDeTeste usuario, String filtros) {
        return campos(api.json(api.get(usuario, "/api/lancamentos?mes=2026-10" + filtros)), "descricao");
    }

    /** O campo de cada item, na ordem da lista ("null" quando nulo). */
    private static List<String> campos(JsonNode lista, String campo) {
        List<String> valores = new ArrayList<>();
        lista.get("dias").forEach(dia -> dia.get("itens").forEach(item -> valores.add(item.get(campo).isNull() ? "null" : item.get(campo).asString())));
        return valores;
    }

    private static List<String> datasDosDias(JsonNode lista) {
        List<String> datas = new ArrayList<>();
        lista.get("dias").forEach(dia -> datas.add(dia.get("data").asString()));
        return datas;
    }

    /** O saldo de cada dia como veio no JSON, com a escala da API ("5778.20"). */
    private static List<String> saldosDosDias(MvcTestResult resposta) {
        Matcher saldo = Pattern.compile("\"saldoNoFimDoDia\":(-?[0-9.]+|null)").matcher(texto(resposta));
        List<String> saldos = new ArrayList<>();
        while (saldo.find()) {
            saldos.add(saldo.group(1));
        }
        return saldos;
    }
}
