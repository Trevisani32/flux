package com.flux.recorrencias.interno;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;

import static com.flux.suporte.ApiDeTeste.assertCampo;
import static com.flux.suporte.ApiDeTeste.lancamento;
import static com.flux.suporte.ApiDeTeste.transferencia;
import static org.assertj.core.api.Assertions.assertThat;

class OcorrenciasIT extends RecorrenciasBase {

    @Test
    void aConfirmarTrazAAtrasadaEOsProximos7Dias() {
        fixarHojeEm20261125();
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        montarOExemplo(usuario);
        JsonNode resposta = aConfirmar(usuario);

        assertThat(resposta.get("ate").asString()).isEqualTo("2026-12-02");
        assertThat(resposta.get("recorrencias").asLong()).isEqualTo(15);
        assertThat(ocorrencias(resposta.get("ocorrencias"))).containsExactly(
                new Ocorrencia("Conta de água Fictícia", "2026-11-01", "2026-11-20", true, "2027-01-19", true, null, null),
                new Ocorrencia("Internet Fictícia", "2026-11-01", "2026-11-26", false, null, false, null, null),
                new Ocorrencia("Diarista Fictícia", "2026-11-23", "2026-11-27", false, null, false, null, null),
                new Ocorrencia("Academia Fictícia", "2026-11-01", "2026-11-28", false, null, false, null, null),
                new Ocorrencia("Netflix Fictícia", "2026-11-01", "2026-11-28", false, null, false, "Cartão Lua", "ROXO"),
                new Ocorrencia("Conta de luz Fictícia", "2026-11-01", "2026-11-30", false, null, true, null, null),
                new Ocorrencia("Aluguel Fictício", "2026-12-01", "2026-12-01", false, null, false, null, null),
                new Ocorrencia("Condomínio Fictício", "2026-12-01", "2026-12-01", false, null, false, null, null),
                new Ocorrencia("Spotify Fictício", "2026-12-01", "2026-12-02", false, null, false, "Cartão Horizonte", "OURO"));
        assertValores(resposta.get("ocorrencias"), "-96.40", "-119.90", "-180.00", "-99.90", "-55.90", "-189.90", "-1850.00", "-420.00",
                "-21.90");
        assertThat(resposta.get("ocorrencias").get(4).get("contaId").isNull()).isTrue();
        assertThat(resposta.get("ocorrencias").valueStream().map(item -> item.get("dataNaLista").isNull()).toList()).containsOnly(true);
    }

    @Test
    void confirmarNaContaCriaOLancamento() {
        fixarHojeEm20261125();
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        Exemplo exemplo = montarOExemplo(usuario);
        JsonNode resposta = json(confirmar(usuario, exemplo.luz(), "2026-11-01", "2026-11-30", "201.37", "Leitura de novembro"));
        long lancamentoId = resposta.get("lancamentoId").asLong();
        assertThat(resposta.get("recorrenciaId").asLong()).isEqualTo(exemplo.luz());
        assertThat(resposta.get("periodo").asString()).isEqualTo("2026-11-01");
        assertThat(resposta.get("data").asString()).isEqualTo("2026-11-30");
        assertThat(resposta.get("compraId").isNull()).isTrue();
        JsonNode lancamento = json(api.get(usuario, "/api/lancamentos/{id}", lancamentoId));
        assertThat(lancamento.get("valor").decimalValue()).isEqualByComparingTo("-201.37");
        assertThat(lancamento.get("tipo").asString()).isEqualTo("DESPESA");
        assertThat(lancamento.get("descricao").asString()).isEqualTo("Conta de luz Fictícia");
        assertThat(lancamento.get("categoriaNome").asString()).isEqualTo("Contas da casa");
        assertThat(lancamento.get("contaNome").asString()).isEqualTo("Banco Horizonte");
        assertThat(lancamento.get("origem").asString()).isEqualTo("MANUAL");
        assertThat(lancamento.get("observacao").asString()).isEqualTo("Leitura de novembro");
        assertThat(jdbc.queryForList("select situacao || ' ' || data_prevista from ocorrencias_tratadas where recorrencia_id = ?",
                String.class,
                exemplo.luz())).containsExactly("EFETIVADA 2026-11-30");
        JsonNode agua = json(confirmar(usuario, exemplo.agua(), "2026-11-01", "2026-11-23", "102.15"));
        assertThat(agua.get("lancamentoId").isNull()).isFalse();
        assertThat(agua.get("data").asString()).isEqualTo("2026-11-23");
        assertThat(ocorrencias(aConfirmar(usuario).get("ocorrencias"))).hasSize(7);
        assertThat(descricoes(aConfirmar(usuario).get("ocorrencias"))).doesNotContain("Conta de luz Fictícia", "Conta de água Fictícia");
        assertThat(json(api.get(usuario, "/api/lancamentos/{id}", lancamentoId)).get("futuro").asBoolean()).isTrue();
    }

    @Test
    void confirmarNoCartaoCriaACompraAVista() {
        fixarHojeEm20261125();
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        Exemplo exemplo = montarOExemplo(usuario);
        JsonNode resposta = json(confirmar(usuario, exemplo.netflix(), "2026-11-01", "2026-11-28", "55.90"));
        long compraId = resposta.get("compraId").asLong();
        assertThat(resposta.get("recorrenciaId").asLong()).isEqualTo(exemplo.netflix());
        assertThat(resposta.get("periodo").asString()).isEqualTo("2026-11-01");
        assertThat(resposta.get("lancamentoId").isNull()).isTrue();
        assertThat(resposta.get("faturaMes").asString()).isEqualTo("2027-01");
        assertThat(resposta.get("cartaoNome").asString()).isEqualTo("Cartão Lua");
        assertThat(resposta.get("data").asString()).isEqualTo("2026-11-28");
        assertThat(jdbc.queryForObject("select parcelas from compras where id = ?", Integer.class, compraId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select valor_total from compras where id = ?", BigDecimal.class, compraId))
                .isEqualByComparingTo("55.90");
        assertThat(jdbc.queryForObject("select data_compra from compras where id = ?", String.class, compraId))
                .isEqualTo("2026-11-28");
        assertThat(parcelas(compraId)).containsExactly("1 2027-01-05 -55.90 2027-01");
        assertThat(jdbc.queryForList("select descricao || '|' || categoria_id from lancamentos where compra_id = ?", String.class,
                compraId))
                .containsExactly("Netflix Fictícia|" + idDaCategoria(usuario, "Assinaturas"));
        assertThat(jdbc.queryForObject("select compra_id from ocorrencias_tratadas where recorrencia_id = ?", Long.class,
                exemplo.netflix()))
                .isEqualTo(compraId);
    }

    @Test
    void confirmarRecusaOQueNaoServe() {
        fixarHojeEm20261125();
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        Exemplo exemplo = montarOExemplo(usuario);
        assertThat(confirmar(usuario, exemplo.luz(), "2026-11-01", "2026-11-30", "189.90")).hasStatus(200);
        assertThat(pular(usuario, exemplo.aluguel(), "2026-12-01")).hasStatus(200);
        int lancamentosAntes = contar("lancamentos", usuario);
        int tratadasAntes = contar("ocorrencias_tratadas", usuario);
        assertRegra(confirmar(usuario, exemplo.luz(), "2026-11-01", "2026-11-30", "189.90"), "Esta ocorrência já foi confirmada.");
        assertRegra(confirmar(usuario, exemplo.aluguel(), "2026-12-01", "2026-12-01", "1850.00"),
                "Esta ocorrência foi pulada. Desfaça antes de confirmar.");
        assertRegra(confirmar(usuario, exemplo.clube(), "2026-12-01", "2026-12-10", "150.00"),
                "A recorrência está pausada: Banco Antigo está arquivada.");
        assertNaoEncontrada(confirmar(usuario, exemplo.internet(), "2026-11-15", "2026-11-26", "119.90"));
        assertNaoEncontrada(confirmar(usuario, exemplo.internet(), "2026-10-01", "2026-11-26", "119.90"));
        assertNaoEncontrada(confirmar(usuario, exemplo.internet(), "2027-12-01", "2026-12-26", "119.90"));
        assertCampo(confirmar(usuario, exemplo.internet(), "2026-13-01", "2026-11-26", "119.90"), "periodo", "Período inválido.");
        assertCampo(confirmar(usuario, exemplo.internet(), "novembro", "2026-11-26", "119.90"), "periodo", "Período inválido.");
        assertCampo(confirmar(usuario, exemplo.condominio(), "2026-12-01", "2027-11-26", "420.00"), "data",
                "A data pode ir até 25/11/2027.");
        assertCampo(confirmar(usuario, exemplo.condominio(), "2026-12-01", "2026-09-30", "420.00"), "data",
                "A data precisa ser 01/10/2026 ou depois, a data do saldo inicial de Banco Horizonte.");
        jdbc.update("insert into lancamentos (usuario_id, conta_id, data, descricao, descricao_chave, valor, tipo, origem) select ?, ?, "
                + "'2026-12-01', 'Limite ' || generate_series, 'limite ' || generate_series, -1, 'DESPESA', 'MANUAL' "
                + "from generate_series(1, 1000)", usuario.id(), exemplo.bancoHorizonte());
        assertRegra(confirmar(usuario, exemplo.condominio(), "2026-12-01", "2026-12-01", "420.00"),
                "Este lançamento passaria do limite de 1.000 lançamentos em dezembro de 2026.");
        assertCampo(api.post(usuario, "/api/recorrencias/{id}/ocorrencias/{periodo}/confirmar", confirmarCorpo("2026-12-01", "null", null),
                exemplo.condominio(), "2026-12-01"), "valor", "Informe o valor.");
        assertCampo(confirmar(usuario, exemplo.condominio(), "2026-12-01", "2026-12-01", "0"), "valor",
                "O valor precisa ser maior que zero.");
        assertCampo(confirmar(usuario, exemplo.condominio(), "2026-12-01", "2026-12-01", "1.234"), "valor",
                "Use até 2 casas decimais e até 13 dígitos antes da vírgula.");
        assertCampo(api.post(usuario, "/api/recorrencias/{id}/ocorrencias/{periodo}/confirmar", confirmarCorpo(null, "420.00", null),
                exemplo.condominio(), "2026-12-01"), "data", "Informe a data.");
        assertCampo(confirmar(usuario, exemplo.condominio(), "2026-12-01", "2026-12-01", "420.00", "X".repeat(1001)), "observacao",
                "Use no máximo 1.000 caracteres.");
        assertThat(contar("lancamentos", usuario)).isEqualTo(lancamentosAntes + 1000);
        assertThat(contar("ocorrencias_tratadas", usuario)).isEqualTo(tratadasAntes);
    }

    @Test
    void pularEVoltarAPrever() {
        fixarHojeEm20261125();
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        Exemplo exemplo = montarOExemplo(usuario);
        JsonNode pulada = json(pular(usuario, exemplo.diarista(), "2026-11-23"));
        assertThat(pulada.get("descricao").asString()).isEqualTo("Diarista Fictícia");
        assertThat(pulada.get("periodo").asString()).isEqualTo("2026-11-23");
        assertThat(pulada.get("data").asString()).isEqualTo("2026-11-27");
        assertThat(descricoes(aConfirmar(usuario).get("ocorrencias"))).doesNotContain("Diarista Fictícia");
        assertThat(pular(usuario, exemplo.diarista(), "2026-11-23")).hasStatus(200);
        assertThat(jdbc.queryForObject("select count(*) from ocorrencias_tratadas where recorrencia_id = ? and periodo = '2026-11-23'",
                Integer.class, exemplo.diarista())).isEqualTo(1);
        assertThat(api.acao(usuario, "/api/recorrencias/{id}/ocorrencias/{periodo}/voltar-a-prever", exemplo.diarista(), "2026-11-23"))
                .hasStatus(204);
        assertThat(descricoes(aConfirmar(usuario).get("ocorrencias"))).contains("Diarista Fictícia");
        assertThat(api.acao(usuario, "/api/recorrencias/{id}/ocorrencias/{periodo}/voltar-a-prever", exemplo.diarista(), "2026-11-23"))
                .hasStatus(204);
        assertRegra(api.acao(usuario, "/api/recorrencias/{id}/ocorrencias/{periodo}/voltar-a-prever", exemplo.curso(), "2026-11-01"),
                "Esta ocorrência já foi confirmada.");
        assertNaoEncontrada(api.acao(usuario, "/api/recorrencias/{id}/ocorrencias/{periodo}/voltar-a-prever", exemplo.diarista(),
                "2026-11-15"));
    }

    @Test
    void jaLanceiSugereELista() {
        fixarHojeEm20261125();
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        Exemplo exemplo = montarOExemplo(usuario);
        CandidatosDoExemplo candidatos = criarCandidatos(usuario, exemplo);
        JsonNode doAluguel = candidatos(usuario, exemplo.aluguel(), "2026-12-01");
        assertThat(doAluguel.get("de").asString()).isEqualTo("2026-11-21");
        assertThat(doAluguel.get("ate").asString()).isEqualTo("2026-12-11");
        assertThat(candidato(doAluguel.get("sugerido"))).isEqualTo(new Candidato(candidatos.aluguel(), "LANCAMENTO", "2026-11-30",
                "Aluguel apartamento", "-1850", "MANUAL", "Aluguel", "Moradia"));
        JsonNode daLuz = candidatos(usuario, exemplo.luz(), "2026-11-01");
        assertThat(daLuz.get("de").asString()).isEqualTo("2026-11-20");
        assertThat(daLuz.get("ate").asString()).isEqualTo("2026-12-10");
        assertThat(daLuz.get("sugerido").isNull()).isTrue();
        assertThat(candidatos(daLuz.get("candidatos"))).containsExactly(
                new Candidato(candidatos.aluguel(), "LANCAMENTO", "2026-11-30", "Aluguel apartamento", "-1850", "MANUAL", "Aluguel",
                        "Moradia"),
                new Candidato(candidatos.enel(), "LANCAMENTO", "2026-11-24", "ENEL DEB AUT FICT", "-201.37", "IMPORTADO",
                        "Contas da casa", "Moradia"),
                new Candidato(candidatos.mercado(), "LANCAMENTO", "2026-11-24", "Mercado Fictício", "-212.4", "MANUAL", "Mercado",
                        "Alimentação"));

        long sugeridaAgua = api.idCriado(api.post(usuario, "/api/lancamentos", lancamento("DESPESA", "140.00", "2026-11-21",
                "Água sugerida",
                exemplo.bancoHorizonte(), null)));
        assertThat(candidato(candidatos(usuario, exemplo.agua(), "2026-11-01").get("sugerido")).id()).isEqualTo(sugeridaAgua);
        assertThat(api.delete(usuario, "/api/lancamentos/{id}", sugeridaAgua)).hasStatus(204);
        api.idCriado(api.post(usuario, "/api/lancamentos", lancamento("DESPESA", "150.00", "2026-11-21", "Água não sugerida",
                exemplo.bancoHorizonte(), null)));
        assertThat(candidatos(usuario, exemplo.agua(), "2026-11-01").get("sugerido").isNull()).isTrue();
        long sugeridoCondominio = api.idCriado(api.post(usuario, "/api/lancamentos", lancamento("DESPESA", "462.00", "2026-12-02",
                "Condomínio sugerido", exemplo.bancoHorizonte(), null)));
        assertThat(candidato(candidatos(usuario, exemplo.condominio(), "2026-12-01").get("sugerido")).id()).isEqualTo(sugeridoCondominio);
        assertThat(api.delete(usuario, "/api/lancamentos/{id}", sugeridoCondominio)).hasStatus(204);
        api.idCriado(api.post(usuario, "/api/lancamentos", lancamento("DESPESA", "462.01", "2026-12-02", "Condomínio não sugerido",
                exemplo.bancoHorizonte(), null)));
        assertThat(candidatos(usuario, exemplo.condominio(), "2026-12-01").get("sugerido").isNull()).isTrue();

        JsonNode daNetflix = candidatos(usuario, exemplo.netflix(), "2026-11-01");
        assertThat(candidato(daNetflix.get("sugerido"))).isEqualTo(new Candidato(candidatos.compraLua(), "COMPRA", "2026-11-27",
                "NETFLIX.COM", "-55.9", "MANUAL", null, null));
        assertThat(candidatos(daNetflix.get("candidatos"))).containsExactly(candidato(daNetflix.get("sugerido")));
    }

    @Test
    void ligarLigaERecusaOQueNaoServe() {
        fixarHojeEm20261125();
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        Exemplo exemplo = montarOExemplo(usuario);
        CandidatosDoExemplo dados = criarCandidatos(usuario, exemplo);
        JsonNode ligada = json(ligar(usuario, exemplo.luz(), "2026-11-01", dados.enel(), null));
        assertThat(ligada.get("lancamentoId").asLong()).isEqualTo(dados.enel());
        assertThat(ligada.get("data").asString()).isEqualTo("2026-11-24");
        assertThat(ligada.get("compraId").isNull()).isTrue();
        assertThat(jdbc.queryForList("select valor || '|' || descricao || '|' || categoria_id from lancamentos where id = ?", String.class,
                dados.enel())).containsExactly("-201.37|ENEL DEB AUT FICT|" + idDaCategoria(usuario, "Contas da casa"));
        assertRegra(ligar(usuario, exemplo.agua(), "2026-11-01", dados.enel(), null),
                "Este lançamento já está ligado a Conta de luz Fictícia de novembro.");
        assertRegra(ligar(usuario, exemplo.luz(), "2026-11-01", dados.enel(), null), "Esta ocorrência já foi confirmada.");
        assertRegra(ligar(usuario, exemplo.aluguel(), "2026-12-01", dados.receita(), null),
                "Escolha um lançamento desta conta e do mesmo tipo.");
        assertRegra(ligar(usuario, exemplo.aluguel(), "2026-12-01", dados.aurora(), null),
                "Escolha um lançamento desta conta e do mesmo tipo.");
        assertRegra(ligar(usuario, exemplo.aluguel(), "2026-12-01", dados.transferencia(), null),
                "Escolha um lançamento desta conta e do mesmo tipo.");
        assertRegra(ligar(usuario, exemplo.aluguel(), "2026-12-01", dados.ignorado(), null),
                "Escolha um lançamento desta conta e do mesmo tipo.");
        assertRegra(ligar(usuario, exemplo.aluguel(), "2026-12-01", null, dados.compraLua()),
                "Escolha um lançamento desta conta e do mesmo tipo.");
        assertCampo(ligar(usuario, exemplo.aluguel(), "2026-12-01", dados.aluguel(), dados.compraLua()), "lancamentoId",
                "Escolha um lançamento ou uma compra.");
        UsuarioDeTeste outro = usuariosDeTeste.criar();
        long contaDoOutro = novaConta(outro, "Conta do outro");
        long lancamentoDoOutro = api.idCriado(api.post(outro, "/api/lancamentos", lancamento("DESPESA", "1", "2026-11-30", "Outro",
                contaDoOutro, null)));
        assertRegra(ligar(usuario, exemplo.aluguel(), "2026-12-01", lancamentoDoOutro, null), "Lançamento não encontrado.");
        assertThat(ligar(usuario, exemplo.netflix(), "2026-11-01", null, dados.compraLua())).hasStatus(200);
        assertRegra(ligar(usuario, exemplo.netflix(), "2026-12-01", dados.aluguel(), null), "Escolha uma compra deste cartão.");
        assertRegra(ligar(usuario, exemplo.netflix(), "2026-12-01", null, dados.compraHorizonte()), "Escolha uma compra deste cartão.");
        assertRegra(ligar(usuario, exemplo.netflix(), "2026-12-01", null, dados.estornoLua()), "Escolha uma compra deste cartão.");
        long revista = api.idCriado(api.post(usuario, "/api/recorrencias", recorrencia("DESPESA", "Revista Fictícia", "55.90", false,
                idDaCategoria(usuario, "Assinaturas"), null, exemplo.cartaoLua(), "MENSAL", 28, "2026-11-25", null)));
        assertRegra(ligar(usuario, revista, "2026-11-01", null, dados.compraLua()),
                "Esta compra já está ligada a Netflix Fictícia de novembro.");
        long cartaoOutro = novoCartao(outro, "Cartão do outro", 3, 10, contaDoOutro);
        long compraDoOutro = novaCompra(outro, cartaoOutro, "2026-11-27", "Compra do outro", "55.90", 1);
        assertRegra(ligar(usuario, revista, "2026-11-01", null, compraDoOutro), "Compra não encontrada.");
    }

    @Test
    void aAtrasadaSomeDepoisDe60Dias() {
        fixarHojeEm20261125();
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        Exemplo exemplo = montarOExemplo(usuario);
        fixarHojeEm(LocalDate.of(2027, 1, 19), 12, 0);
        UsuarioDeTeste tokenNovo = renovarToken(usuario);
        JsonNode noLimite = aConfirmar(tokenNovo);
        assertThat(ocorrencias(noLimite.get("ocorrencias")).stream().filter(item -> item.descricao().equals("Conta de água Fictícia")
                && item.periodo().equals("2026-11-01")).toList())
                .containsExactly(new Ocorrencia("Conta de água Fictícia", "2026-11-01", "2026-11-20", true, "2027-01-19", true,
                        null, null));
        fixarHojeEm(LocalDate.of(2027, 1, 20), 12, 0);
        tokenNovo = renovarToken(tokenNovo);
        assertThat(ocorrencias(aConfirmar(tokenNovo).get("ocorrencias")).stream()
                .filter(item -> item.descricao().equals("Conta de água Fictícia"))
                .map(Ocorrencia::periodo).toList()).doesNotContain("2026-11-01");
        int tratadasAntes = contar("ocorrencias_tratadas", usuario);
        assertNaoEncontrada(confirmar(tokenNovo, exemplo.agua(), "2026-11-01", "2027-01-20", "96.40"));
        assertThat(contar("ocorrencias_tratadas", usuario)).isEqualTo(tratadasAntes);
    }

    @Test
    void oHistoricoMostraPrevistaConfirmadaEPulada() {
        fixarHojeEm20261125();
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        Exemplo exemplo = montarOExemplo(usuario);
        JsonNode antes = historico(usuario, exemplo.academia());
        assertThat(antes.get("desde").asString()).isEqualTo("2026-10-01");
        assertThat(historicoCompleto(antes)).containsExactly(
                new Historico("2026-11-01", "2026-11-28", "PREVISTA", "-99.9", null, null, null, null, exemplo.bancoHorizonte(), null),
                new Historico("2026-10-01", "2026-10-28", "PULADA", null, null, null, null, null, exemplo.bancoHorizonte(), null));
        long lancamento = json(confirmar(usuario, exemplo.academia(), "2026-11-01", "2026-11-28", "99.90")).get("lancamentoId").asLong();
        JsonNode depois = historico(usuario, exemplo.academia());
        assertThat(historicoCompleto(depois)).containsExactly(
                new Historico("2026-12-01", "2026-12-28", "PREVISTA", "-99.9", null, null, null, null, exemplo.bancoHorizonte(), null),
                new Historico("2026-11-01", "2026-11-28", "EFETIVADA", "-99.9", "2026-11-28", lancamento, null, "2026-11",
                        exemplo.bancoHorizonte(), null),
                new Historico("2026-10-01", "2026-10-28", "PULADA", null, null, null, null, null, exemplo.bancoHorizonte(), null));
        JsonNode agua = historico(usuario, exemplo.agua());
        assertThat(historicoCompleto(agua)).containsExactly(
                new Historico("2026-12-01", "2026-12-20", "PREVISTA", "-96.4", null, null, null, null, exemplo.bancoHorizonte(), null),
                new Historico("2026-11-01", "2026-11-20", "ATRASADA", "-96.4", null, null, null, null, exemplo.bancoHorizonte(), null));
        long compra = json(confirmar(usuario, exemplo.netflix(), "2026-11-01", "2026-11-28", "55.90")).get("compraId").asLong();
        assertThat(historicoPorPeriodo(historico(usuario, exemplo.netflix()), "2026-11-01")).containsExactly(
                new Historico("2026-11-01", "2026-11-28", "EFETIVADA", "-55.9", "2026-11-28", null, compra, "2027-01", null,
                        exemplo.cartaoLua()));
    }

    private CandidatosDoExemplo criarCandidatos(UsuarioDeTeste usuario, Exemplo exemplo) {
        long aluguel = api.idCriado(api.post(usuario, "/api/lancamentos", lancamento("DESPESA", "1850.00", "2026-11-30",
                "Aluguel apartamento",
                exemplo.bancoHorizonte(), idDaCategoria(usuario, "Aluguel"))));
        long mercado = api.idCriado(api.post(usuario, "/api/lancamentos", lancamento("DESPESA", "212.40", "2026-11-24", "Mercado Fictício",
                exemplo.bancoHorizonte(), idDaCategoria(usuario, "Mercado"))));
        long receita = api.idCriado(api.post(usuario, "/api/lancamentos", lancamento("RECEITA", "100.00", "2026-11-25", "Receita Fictícia",
                exemplo.bancoHorizonte(), null)));
        long enel = importado(usuario, exemplo.bancoHorizonte(), "2026-11-24", "-201.37", "ENEL DEB AUT FICT");
        jdbc.update("update lancamentos set categoria_id = ? where id = ?", idDaCategoria(usuario, "Contas da casa"), enel);
        long aurora = api.idCriado(api.post(usuario, "/api/lancamentos", lancamento("DESPESA", "189.90", "2026-11-29", "Despesa Aurora",
                exemplo.bancoAurora(), null)));
        long transferenciaCriada = api.idCriado(api.post(usuario, "/api/transferencias", transferencia("189.90", "2026-11-29",
                exemplo.bancoHorizonte(), exemplo.bancoAurora(), "Transferência Fictícia")));
        long transferencia = jdbc.queryForObject("select id from lancamentos where transferencia_id = ? and conta_id = ?", Long.class,
                transferenciaCriada, exemplo.bancoHorizonte());
        long ignorado = importado(usuario, exemplo.bancoHorizonte(), "2026-11-28", "-189.90", "Importado ignorado");
        assertThat(api.delete(usuario, "/api/lancamentos/{id}", ignorado)).hasStatus(204);
        assertThat(confirmar(usuario, exemplo.internet(), "2026-11-01", "2026-11-26", "119.90")).hasStatus(200);
        long compraLua = novaCompra(usuario, exemplo.cartaoLua(), "2026-11-27", "NETFLIX.COM", "55.90", 1);
        long estornoLua = api.idCriado(api.post(usuario, "/api/estornos",
                estorno(exemplo.cartaoLua(), "2026-11-27", "Estorno Netflix", "55.90", null)));
        long compraHorizonte = novaCompra(usuario, exemplo.cartaoHorizonte(), "2026-11-27", "NETFLIX.COM Horizonte", "55.90", 1);
        return new CandidatosDoExemplo(aluguel, mercado, receita, enel, aurora, transferencia, ignorado, compraLua, estornoLua,
                compraHorizonte);
    }

    private static void assertRegra(MvcTestResult resposta, String detalhe) {
        assertThat(resposta).hasStatus(422).bodyJson().extractingPath("detail").isEqualTo(detalhe);
    }

    private static void assertNaoEncontrada(MvcTestResult resposta) {
        assertThat(resposta).hasStatus(404).bodyJson().extractingPath("detail").isEqualTo("Ocorrência não encontrada.");
    }

    private static List<Ocorrencia> ocorrencias(JsonNode itens) {
        return itens.valueStream().map(item -> new Ocorrencia(item.get("descricao").asString(), item.get("periodo").asString(),
                item.get("data").asString(), item.get("atrasada").asBoolean(), texto(item, "visivelAte"),
                item.get("valorVaria").asBoolean(),
                texto(item, "cartaoNome"), texto(item, "cartaoCor"))).toList();
    }

    private static void assertValores(JsonNode itens, String... esperados) {
        assertThat(itens.size()).isEqualTo(esperados.length);
        for (int indice = 0; indice < esperados.length; indice++) {
            assertThat(itens.get(indice).get("valor").decimalValue()).isEqualByComparingTo(esperados[indice]);
        }
    }

    private static List<String> descricoes(JsonNode itens) {
        return itens.valueStream().map(item -> item.get("descricao").asString()).toList();
    }

    private static List<Candidato> candidatos(JsonNode itens) {
        return itens.valueStream().map(OcorrenciasIT::candidato).toList();
    }

    private static Candidato candidato(JsonNode item) {
        return new Candidato(item.get("id").asLong(), item.get("alvo").asString(), item.get("data").asString(),
                item.get("descricao").asString(),
                item.get("valor").decimalValue().stripTrailingZeros().toPlainString(), item.get("origem").asString(),
                texto(item, "categoriaNome"),
                texto(item, "categoriaMaeNome"));
    }

    private static List<Historico> historicoPorPeriodo(JsonNode resposta, String periodo) {
        return historicoCompleto(resposta).stream().filter(item -> periodo.equals(item.periodo())).toList();
    }

    private static List<Historico> historicoCompleto(JsonNode resposta) {
        return resposta.get("itens").valueStream().map(item -> new Historico(
                item.get("periodo").asString(), item.get("dataPrevista").asString(), item.get("situacao").asString(), texto(item, "valor"),
                texto(item, "data"), numero(item, "lancamentoId"), numero(item, "compraId"), texto(item, "mesNaLista"),
                numero(item, "contaId"),
                numero(item, "cartaoId"))).toList();
    }

    private static String texto(JsonNode item, String campo) {
        return item.get(campo).isNull() ? null : item.get(campo).asString();
    }

    private static Long numero(JsonNode item, String campo) {
        return item.get(campo).isNull() ? null : item.get(campo).asLong();
    }

    private record Ocorrencia(String descricao, String periodo, String data, boolean atrasada, String visivelAte, boolean valorVaria,
                              String cartaoNome, String cartaoCor) {
    }

    private record Candidato(long id, String alvo, String data, String descricao, String valor, String origem, String categoriaNome,
                             String categoriaMaeNome) {
    }

    private record Historico(String periodo, String dataPrevista, String situacao, String valor, String data, Long lancamentoId,
                             Long compraId, String mesNaLista, Long contaId, Long cartaoId) {
    }

    private record CandidatosDoExemplo(long aluguel, long mercado, long receita, long enel, long aurora, long transferencia,
                                       long ignorado, long compraLua, long estornoLua, long compraHorizonte) {
    }
}
