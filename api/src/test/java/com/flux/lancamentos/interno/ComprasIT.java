package com.flux.lancamentos.interno;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.flux.suporte.CartoesBase;
import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static com.flux.suporte.ApiDeTeste.assertCampo;
import static com.flux.suporte.ApiDeTeste.texto;
import static org.assertj.core.api.Assertions.assertThat;

/** Compras e estornos no cartão (decisões 14 a 21 da 05a). O Cartão Horizonte fecha dia 3 e vence dia 10. */
class ComprasIT extends CartoesBase {

    @Test
    void aCompraParceladaCaiNasFaturasCertasComORestoNaPrimeira() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novoCartao(usuario, "Cartão Horizonte", 3, 10, novaConta(usuario, "Banco Horizonte"));
        long compras = idDaCategoria(usuario, "Compras");
        MvcTestResult criada = api.post(usuario, "/api/compras", compra(horizonte, "2025-11-09", " Loja   Fictícia ", "100", 3, 1, compras));
        long id = api.idCriado(criada);
        assertThat(criada).bodyJson().extractingPath("tipo").isEqualTo("COMPRA");
        assertThat(criada).bodyJson().extractingPath("descricao").isEqualTo("Loja Fictícia");
        assertThat(criada).bodyJson().extractingPath("categoriaNome").isEqualTo("Compras");
        assertThat(criada).bodyJson().extractingPath("parcelasNoFlux[*].mes").asArray().containsExactly("2025-12", "2026-01", "2026-02");
        assertThat(texto(criada)).contains("\"valorTotal\":100.00").contains("\"valor\":33.34").contains("\"valor\":33.33");
        assertThat(parcelas(id)).containsExactly("1 2025-12-10 -33.34 2025-12", "2 2026-01-10 -33.33 2026-01", "3 2026-02-10 -33.33 2026-02");
        assertThat(jdbc.queryForList("select distinct tipo || ' ' || coalesce(conta_id::text, 'sem conta') || ' ' || categoria_id "
                + "from lancamentos where compra_id = ?", String.class, id)).containsExactly("DESPESA sem conta " + compras);
        assertThat(faturas(horizonte)).containsExactly("2025-12 2025-12-03 2025-12-10 false", "2026-01 2026-01-03 2026-01-10 false",
                "2026-02 2026-02-03 2026-02-10 false");

        // O dia do fechamento ainda é da fatura; o seguinte, da próxima (decisão 8).
        long noFechamento = novaCompra(usuario, horizonte, "2025-12-03", "No fechamento", "10", 1);
        long depois = novaCompra(usuario, horizonte, "2025-12-04", "Depois do fechamento", "10", 1);
        assertThat(parcelas(noFechamento)).containsExactly("1 2025-12-10 -10.00 2025-12");
        assertThat(parcelas(depois)).containsExactly("1 2026-01-10 -10.00 2026-01");
        assertThat(api.get(usuario, "/api/compras/{id}", id)).hasStatus(200).bodyJson().extractingPath("parcelas").isEqualTo(3);
    }

    /** Decisão 17: a compra em andamento grava só as parcelas do Flux, nas faturas pela data da compra. */
    @Test
    void aCompraEmAndamentoGravaSoAsParcelasDoFlux() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novoCartao(usuario, "Cartão Horizonte", 3, 10, novaConta(usuario, "Banco Horizonte"));
        long id = api.idCriado(api.post(usuario, "/api/compras", compra(horizonte, "2025-07-15", "Geladeira Fria Mais", "2490", 10, 5, null)));
        List<String> gravadas = parcelas(id);
        assertThat(gravadas).hasSize(6);
        assertThat(gravadas.getFirst()).isEqualTo("5 2025-12-10 -249.00 2025-12");
        assertThat(gravadas.getLast()).isEqualTo("10 2026-05-10 -249.00 2026-05");
        assertThat(api.get(usuario, "/api/compras/{id}", id)).bodyJson().extractingPath("parcelaInicial").isEqualTo(5);
    }

    /** Decisão 18: a prévia divide e diz as faturas, sem gravar nada; a 1ª parcela fora do Flux também aparece. */
    @Test
    void aPreviaMostraAsParcelasSemGravar() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novoCartao(usuario, "Cartão Horizonte", 3, 10, novaConta(usuario, "Banco Horizonte"));
        MvcTestResult previa = api.post(usuario, "/api/compras/previa", compra(horizonte, "2025-06-20", "x", "2490", 10, 6, null));
        assertThat(previa).hasStatus(200).bodyJson().extractingPath("parcelas[*].mes").asArray().hasSize(10);
        assertThat(previa).bodyJson().extractingPath("parcelas[0].mes").isEqualTo("2025-07");
        assertThat(previa).bodyJson().extractingPath("parcelas[0].noFlux").isEqualTo(false);
        assertThat(previa).bodyJson().extractingPath("parcelas[5].mes").isEqualTo("2025-12");
        assertThat(previa).bodyJson().extractingPath("parcelas[5].noFlux").isEqualTo(true);
        assertThat(previa).bodyJson().extractingPath("primeiraFatura.mes").isEqualTo("2025-07");
        assertThat(previa).bodyJson().extractingPath("primeiraFatura.fechamento").isEqualTo("2025-07-03");
        assertThat(previa).bodyJson().extractingPath("primeiraFatura.situacao").isEqualTo("FECHADA");
        assertThat(texto(previa)).contains("\"valor\":249.00");
        assertThat(contar("faturas", usuario)).isZero();
        assertCampo(api.post(usuario, "/api/compras/previa", compra(horizonte, "2025-06-20", "x", "0.47", 48, 1, null)), "valorTotal",
                "Em 48x, o valor precisa ser de pelo menos R$ 0,48.");
    }

    @Test
    void validaACompraComAsMensagensDaTela() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long conta = novaConta(usuario, "Banco Horizonte");
        long horizonte = novoCartao(usuario, "Cartão Horizonte", 3, 10, conta);
        long lua = novoCartao(usuario, "Cartão Lua", 25, 5, conta);
        String limite = LocalDate.now(relogioDeTeste).plusMonths(12).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String depoisDoLimite = LocalDate.now(relogioDeTeste).plusMonths(12).plusDays(1).toString();
        assertCampo(api.post(usuario, "/api/compras", compra(horizonte, "2025-11-09", " ", "100", 3, 1, null)), "descricao", "Escreva a descrição.");
        assertCampo(api.post(usuario, "/api/compras", compra(horizonte, "2025-11-09", "Loja", "100", 49, 1, null)), "parcelas",
                "Use de 1 a 48 parcelas.");
        assertCampo(api.post(usuario, "/api/compras", compra(horizonte, "2025-11-09", "Loja", "100", 10, 11, null)), "parcelaInicial",
                "A primeira parcela no Flux vai da 1ª à 10ª.");
        assertCampo(api.post(usuario, "/api/compras", compra(horizonte, "2025-11-09", "Loja", "0.47", 48, 1, null)), "valorTotal",
                "Em 48x, o valor precisa ser de pelo menos R$ 0,48.");
        assertCampo(api.post(usuario, "/api/compras", compra(horizonte, depoisDoLimite, "Loja", "100", 1, 1, null)), "data",
                "A data pode ir até " + limite + ".");
        // Decisão 16: a data vai até 10 anos para trás; a véspera disso é recusada na compra, na prévia e no estorno.
        LocalDate hoje = LocalDate.now(relogioDeTeste);
        String minima = hoje.minusYears(10).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String antesDaMinima = hoje.minusYears(10).minusDays(1).toString();
        assertCampo(api.post(usuario, "/api/compras", compra(horizonte, antesDaMinima, "Loja", "100", 1, 1, null)), "data",
                "A data precisa ser " + minima + " ou depois.");
        assertCampo(api.post(usuario, "/api/compras/previa", compra(horizonte, antesDaMinima, "Loja", "100", 1, 1, null)), "data",
                "A data precisa ser " + minima + " ou depois.");
        assertCampo(api.post(usuario, "/api/estornos", estorno(horizonte, antesDaMinima, "Estorno", "10", null)), "data",
                "A data precisa ser " + minima + " ou depois.");
        assertThat(api.post(usuario, "/api/compras", compra(999_999L, "2025-11-09", "Loja", "100", 1, 1, null))).hasStatus(422).bodyJson()
                .extractingPath("detail").isEqualTo("Cartão não encontrado.");
        assertThat(api.post(usuario, "/api/compras", compra(horizonte, "2025-11-09", "Loja", "100", 1, 1, idDaCategoria(usuario, "Salário"))))
                .hasStatus(422).bodyJson().extractingPath("detail").isEqualTo("Escolha uma categoria de despesa.");
        assertThat(api.acao(usuario, "/api/cartoes/{id}/arquivar", lua)).hasStatus(200);
        MvcTestResult arquivado = api.post(usuario, "/api/compras", compra(lua, "2025-11-09", "Loja", "100", 1, 1, null));
        assertThat(arquivado).hasStatus(422).bodyJson().extractingPath("detail").isEqualTo("Cartão Lua está arquivado. Reative o cartão para lançar nele.");
        assertThat(arquivado).bodyJson().extractingPath("codigo").isEqualTo("CARTAO_ARQUIVADO");
        assertThat(api.post(usuario, "/api/compras", compra(horizonte, "2025-11-09", "Loja", "0.48", 48, 1, null))).hasStatus(201);
        // As duas pontas valem: 10 anos para trás, exatos, e 12 meses para a frente, exatos.
        assertThat(api.post(usuario, "/api/compras", compra(horizonte, hoje.minusYears(10).toString(), "Na data mínima", "100", 1, 1, null)))
                .hasStatus(201);
        assertThat(api.post(usuario, "/api/estornos", estorno(horizonte, hoje.plusMonths(12).toString(), "Na data máxima", "10", null)))
                .hasStatus(201);
        assertThat(contar("lancamentos", usuario)).isEqualTo(50);
    }

    /** Decisão 19: os textos mudam nas parcelas que existem; cartão, data, valor ou parcelas regravam todas. */
    @Test
    void editarRegravaAsParcelasOuSoTrocaOsTextos() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long conta = novaConta(usuario, "Banco Horizonte");
        long horizonte = novoCartao(usuario, "Cartão Horizonte", 3, 10, conta);
        long lua = novoCartao(usuario, "Cartão Lua", 25, 5, conta);
        long id = novaCompra(usuario, horizonte, "2025-11-09", "Sapataria", "450", 3);
        List<Long> antes = jdbc.queryForList("select id from lancamentos where compra_id = ? order by id", Long.class, id);
        long compras = idDaCategoria(usuario, "Compras");

        assertThat(api.put(usuario, "/api/compras/{id}", compra(horizonte, "2025-11-09", "Sapatos de corrida", "450", 3, 1, compras), id))
                .hasStatus(200).bodyJson().extractingPath("descricao").isEqualTo("Sapatos de corrida");
        assertThat(jdbc.queryForList("select id from lancamentos where compra_id = ? order by id", Long.class, id)).isEqualTo(antes);
        assertThat(jdbc.queryForList("select distinct descricao || ' ' || categoria_id from lancamentos where compra_id = ?", String.class, id))
                .containsExactly("Sapatos de corrida " + compras);

        assertThat(api.put(usuario, "/api/compras/{id}", compra(horizonte, "2025-11-09", "Sapatos de corrida", "450", 2, 1, compras), id))
                .hasStatus(200);
        assertThat(parcelas(id)).containsExactly("1 2025-12-10 -225.00 2025-12", "2 2026-01-10 -225.00 2026-01");
        assertThat(jdbc.queryForList("select id from lancamentos where compra_id = ? order by id", Long.class, id)).doesNotContainAnyElementsOf(antes);

        assertThat(api.put(usuario, "/api/compras/{id}", compra(lua, "2025-11-20", "Sapatos de corrida", "450", 2, 1, compras), id))
                .hasStatus(200).bodyJson().extractingPath("cartaoNome").isEqualTo("Cartão Lua");
        assertThat(parcelas(id)).containsExactly("1 2025-12-05 -225.00 2025-12", "2 2026-01-05 -225.00 2026-01");
        assertThat(jdbc.queryForObject("select count(distinct cartao_id) from lancamentos where compra_id = ?", Integer.class, id)).isEqualTo(1);
        assertThat(api.acao(usuario, "/api/cartoes/{id}/arquivar", lua)).hasStatus(200);
        assertThat(api.put(usuario, "/api/compras/{id}", compra(lua, "2025-11-20", "Outra", "450", 2, 1, null), id)).hasStatus(422).bodyJson()
                .extractingPath("detail").isEqualTo("Cartão Lua está arquivado. Reative o cartão para editar ou excluir esta compra.");
        assertThat(api.delete(usuario, "/api/compras/{id}", id)).hasStatus(422);
    }

    /**
     * Decisão 16: a faixa vale para a data que se lança. A compra e o estorno com mais de 10 anos ainda se editam com a
     * data que têm, e só a data nova passa pela faixa. Com a data igual, regravar mantém as parcelas nos meses delas
     * (decisão 7), mesmo sem os dias de antes gravados.
     */
    @Test
    void aDataQueJaEstaNaCompraFicaNaEdicaoESoADataNovaPassaPelaFaixa() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long conta = novaConta(usuario, "Banco Horizonte");
        long horizonte = novoCartao(usuario, "Cartão Horizonte", 3, 10, conta);
        LocalDate minima = LocalDate.now(relogioDeTeste).minusYears(10);
        long id = api.idCriado(api.post(usuario, "/api/compras", compra(horizonte, minima.toString(), "Geladeira", "300", 3, 2, null)));
        long devolucao = api.idCriado(api.post(usuario, "/api/estornos", estorno(horizonte, minima.toString(), "Estorno", "10", null)));
        List<String> mesesAntes = parcelas(id).stream().map(linha -> linha.substring(linha.lastIndexOf(' ') + 1)).toList();
        // Dois dias depois, as duas datas passam de 10 anos; e os dias do cartão mudam sem que os de antes fiquem gravados.
        jdbc.update("update compras set data_compra = data_compra - 2 where usuario_id = ?", usuario.id());
        jdbc.update("update cartoes set dia_fechamento = 20, dia_vencimento = 27 where id = ?", horizonte);
        String antiga = minima.minusDays(2).toString();

        assertThat(api.put(usuario, "/api/compras/{id}", compra(horizonte, antiga, "Geladeira Fria", "300", 3, 2, null), id)).hasStatus(200)
                .bodyJson().extractingPath("descricao").isEqualTo("Geladeira Fria");
        assertThat(api.put(usuario, "/api/estornos/{id}", estorno(horizonte, antiga, "Estorno da geladeira", "10", null), devolucao))
                .hasStatus(200).bodyJson().extractingPath("descricao").isEqualTo("Estorno da geladeira");
        assertThat(api.put(usuario, "/api/compras/{id}", compra(horizonte, antiga, "Geladeira Fria", "330", 3, 2, null), id)).hasStatus(200);
        assertThat(parcelas(id)).extracting(linha -> linha.substring(linha.lastIndexOf(' ') + 1)).containsExactlyElementsOf(mesesAntes);
        assertThat(parcelas(id)).allMatch(linha -> linha.contains(" -110.00 "));

        String foraDaFaixa = minima.minusDays(3).toString();
        String mensagem = "A data precisa ser " + minima.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + " ou depois.";
        assertCampo(api.put(usuario, "/api/compras/{id}", compra(horizonte, foraDaFaixa, "Geladeira Fria", "330", 3, 2, null), id), "data",
                mensagem);
        assertCampo(api.put(usuario, "/api/estornos/{id}", estorno(horizonte, foraDaFaixa, "Estorno da geladeira", "10", null), devolucao),
                "data", mensagem);
        assertCampo(api.post(usuario, "/api/compras/previa", compra(horizonte, antiga, "x", "330", 3, 2, null)), "data", mensagem);
    }

    /** Decisão 19 e 9: excluir tira as parcelas, e as faturas ficam. O estorno não se alcança como compra, nem o contrário. */
    @Test
    void excluirTiraAsParcelasEDeixaAsFaturas() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novoCartao(usuario, "Cartão Horizonte", 3, 10, novaConta(usuario, "Banco Horizonte"));
        long id = novaCompra(usuario, horizonte, "2025-11-09", "Loja Eletro", "1990", 10);
        long devolucao = api.idCriado(api.post(usuario, "/api/estornos", estorno(horizonte, "2025-11-11", "Estorno", "50", null)));
        assertThat(api.get(usuario, "/api/estornos/{id}", id)).hasStatus(404).bodyJson().extractingPath("detail").isEqualTo("Estorno não encontrado.");
        assertThat(api.get(usuario, "/api/compras/{id}", devolucao)).hasStatus(404).bodyJson().extractingPath("detail").isEqualTo("Compra não encontrada.");
        assertThat(api.delete(usuario, "/api/compras/{id}", id)).hasStatus(204);
        assertThat(api.get(usuario, "/api/compras/{id}", id)).hasStatus(404);
        assertThat(jdbc.queryForObject("select count(*) from lancamentos where compra_id = ?", Integer.class, id)).isZero();
        assertThat(contar("faturas", usuario)).isEqualTo(10);
        assertThat(api.delete(usuario, "/api/estornos/{id}", devolucao)).hasStatus(204);
        assertThat(contar("compras", usuario)).isZero();
    }

    /** Decisão 20: o estorno é receita positiva na fatura da data dele, com categoria de receita opcional. */
    @Test
    void oEstornoEReceitaNaFaturaDaData() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long horizonte = novoCartao(usuario, "Cartão Horizonte", 3, 10, novaConta(usuario, "Banco Horizonte"));
        long reembolsos = idDaCategoria(usuario, "Reembolsos");
        MvcTestResult criado = api.post(usuario, "/api/estornos", estorno(horizonte, "2025-11-11", "Estorno Sapataria", "50", reembolsos));
        long id = api.idCriado(criado);
        assertThat(criado).bodyJson().extractingPath("tipo").isEqualTo("ESTORNO");
        assertThat(parcelas(id)).containsExactly("1 2025-12-10 50.00 2025-12");
        assertThat(jdbc.queryForObject("select tipo from lancamentos where compra_id = ?", String.class, id)).isEqualTo("RECEITA");
        assertThat(api.post(usuario, "/api/estornos", estorno(horizonte, "2025-11-11", "Estorno", "50", idDaCategoria(usuario, "Compras"))))
                .hasStatus(422).bodyJson().extractingPath("detail").isEqualTo("Escolha uma categoria de receita.");
        assertThat(api.put(usuario, "/api/estornos/{id}", estorno(horizonte, "2025-12-05", "Estorno Sapataria", "60", reembolsos), id))
                .hasStatus(200);
        assertThat(parcelas(id)).containsExactly("1 2026-01-10 60.00 2026-01");
        assertCampo(api.post(usuario, "/api/estornos", estorno(horizonte, "2025-11-11", "Estorno", "0", null)), "valor",
                "O valor precisa ser maior que zero.");
    }

    /** Decisão 21: cada parcela conta 1 no mês do vencimento dela; a compra que passaria do limite não grava nada. */
    @Test
    void cadaParcelaContaNoLimiteDoMes() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long conta = novaConta(usuario, "Banco Horizonte");
        long horizonte = novoCartao(usuario, "Cartão Horizonte", 3, 10, conta);
        jdbc.update("insert into lancamentos (usuario_id, conta_id, data, descricao, descricao_chave, valor, tipo, origem) "
                + "select ?, ?, date '2026-01-01' + (n % 28), 'Lote', 'lote', -1, 'DESPESA', 'MANUAL' from generate_series(1, 1000) n",
                usuario.id(), conta);
        MvcTestResult recusada = api.post(usuario, "/api/compras", compra(horizonte, "2025-11-09", "Loja", "300", 3, 1, null));
        assertThat(recusada).hasStatus(422).bodyJson().extractingPath("detail")
                .isEqualTo("Esta compra passaria do limite de 1.000 lançamentos em janeiro de 2026.");
        assertThat(contar("compras", usuario)).isZero();
        assertThat(contar("faturas", usuario)).isZero();
        assertThat(api.post(usuario, "/api/compras", compra(horizonte, "2025-11-09", "Loja", "300", 1, 1, null))).hasStatus(201);
    }
}
