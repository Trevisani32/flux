package com.flux.recorrencias.interno;

import java.util.List;

import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;

import static com.flux.suporte.ApiDeTeste.assertCampo;
import static com.flux.suporte.ApiDeTeste.categoria;
import static com.flux.suporte.ApiDeTeste.conta;
import static org.assertj.core.api.Assertions.assertThat;

class RecorrenciasIT extends RecorrenciasBase {

    @Test
    void criaComAsMensagensDeValidacao() {
        fixarHojeEm20261125();
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long conta = api.idCriado(api.post(usuario, "/api/contas", conta("Banco de validação", "CORRENTE", "0", "2026-10-01", "null")));

        MvcTestResult criada = api.post(usuario, "/api/recorrencias", recorrencia("DESPESA", "Conta de luz Fictícia", "189.90", true,
                null, conta, null, "MENSAL", 30, "2026-11-25", null));
        JsonNode corpo = json(criada);
        assertThat(criada).hasStatus(201).hasHeader("Location", "/api/recorrencias/" + corpo.get("id").asLong());
        assertThat(corpo.get("descricao").asString()).isEqualTo("Conta de luz Fictícia");
        assertThat(corpo.get("valor").decimalValue()).isEqualByComparingTo("189.90");
        assertThat(corpo.get("valorComSinal").decimalValue()).isEqualByComparingTo("-189.90");
        assertThat(corpo.get("valorVaria").asBoolean()).isTrue();
        assertThat(corpo.get("contaId").asLong()).isEqualTo(conta);
        assertThat(corpo.get("frequencia").asString()).isEqualTo("MENSAL");
        assertThat(corpo.get("diaDoMes").asInt()).isEqualTo(30);
        assertThat(corpo.get("comecaEm").asString()).isEqualTo("2026-11-25");
        assertThat(corpo.get("proxima").asString()).isEqualTo("2026-11-30");
        assertThat(corpo.get("ocorrenciasDesde").asString()).isEqualTo("2026-11-25");
        assertThat(corpo.get("situacao").asString()).isEqualTo("ATIVA");

        int antesDasRecusas = contar("recorrencias", usuario);
        assertCampo(criar(usuario, corpo("\"DESPESA\"", "\"\"", "1", "false", "null", conta, "null", "\"MENSAL\"", "1",
                "\"2026-11-25\"", "null")), "descricao", "Escreva a descrição.");
        assertCampo(criar(usuario, corpo("\"DESPESA\"", "\"" + "X".repeat(256) + "\"", "1", "false", "null", conta, "null",
                "\"MENSAL\"", "1", "\"2026-11-25\"", "null")), "descricao", "A descrição pode ter até 255 caracteres.");
        assertCampo(criar(usuario, corpo("\"DESPESA\"", "\"Valor nulo\"", "null", "false", "null", conta, "null", "\"MENSAL\"",
                "1", "\"2026-11-25\"", "null")), "valor", "Informe o valor.");
        assertCampo(criar(usuario, corpo("\"DESPESA\"", "\"Valor zero\"", "0", "false", "null", conta, "null", "\"MENSAL\"",
                "1", "\"2026-11-25\"", "null")), "valor", "O valor precisa ser maior que zero.");
        assertCampo(criar(usuario, corpo("\"DESPESA\"", "\"Três casas\"", "12.345", "false", "null", conta, "null", "\"MENSAL\"",
                "1", "\"2026-11-25\"", "null")), "valor", "Use até 2 casas decimais e até 13 dígitos antes da vírgula.");
        assertCampo(criar(usuario, corpo("\"DESPESA\"", "\"Valor longo\"", "10000000000000.00", "false", "null", conta, "null",
                "\"MENSAL\"", "1", "\"2026-11-25\"", "null")), "valor", "Use até 2 casas decimais e até 13 dígitos antes da vírgula.");
        assertCampo(criar(usuario, corpo("null", "\"Sem tipo\"", "1", "false", "null", conta, "null", "\"MENSAL\"", "1",
                "\"2026-11-25\"", "null")), "tipo", "Escolha o tipo.");
        assertCampo(criar(usuario, corpo("\"DESPESA\"", "\"Sem frequência\"", "1", "false", "null", conta, "null", "null", "1",
                "\"2026-11-25\"", "null")), "frequencia", "Escolha a frequência.");
        assertCampo(criar(usuario, corpo("\"DESPESA\"", "\"Sem início\"", "1", "false", "null", conta, "null", "\"MENSAL\"", "1",
                "null", "null")), "comecaEm", "Informe o início.");
        assertCampo(criar(usuario, corpo("\"DESPESA\"", "\"Sem dia\"", "1", "false", "null", conta, "null", "\"MENSAL\"", "null",
                "\"2026-11-25\"", "null")), "diaDoMes", "Escolha um dia de 1 a 31.");
        assertCampo(criar(usuario, corpo("\"DESPESA\"", "\"Dia zero\"", "1", "false", "null", conta, "null", "\"MENSAL\"", "0",
                "\"2026-11-25\"", "null")), "diaDoMes", "Escolha um dia de 1 a 31.");
        assertCampo(criar(usuario, corpo("\"DESPESA\"", "\"Dia 32\"", "1", "false", "null", conta, "null", "\"MENSAL\"", "32",
                "\"2026-11-25\"", "null")), "diaDoMes", "Escolha um dia de 1 a 31.");
        assertCampo(criar(usuario, corpo("\"DESPESA\"", "\"Início distante\"", "1", "false", "null", conta, "null", "\"MENSAL\"", "1",
                "\"2027-11-26\"", "null")), "comecaEm", "O início pode ir até 25/11/2027.");
        assertCampo(criar(usuario, corpo("\"DESPESA\"", "\"Fim anterior\"", "1", "false", "null", conta, "null", "\"MENSAL\"", "1",
                "\"2026-11-25\"", "\"2026-11-24\"")), "terminaEm", "O fim precisa ser no dia do início ou depois (25/11/2026).");
        assertCampo(criar(usuario, corpo("\"DESPESA\"", "\"Sem alvo\"", "1", "false", "null", "null", "null", "\"MENSAL\"", "1",
                "\"2026-11-25\"", "null")), "contaId", "Escolha a conta ou o cartão.");
        long cartao = novoCartao(usuario, "Cartão de validação", 3, 10, conta);
        assertCampo(criar(usuario, corpo("\"DESPESA\"", "\"Dois alvos\"", "1", "false", "null", conta, Long.toString(cartao),
                "\"MENSAL\"", "1",
                "\"2026-11-25\"", "null")), "contaId", "Escolha a conta ou o cartão, não os dois.");
        MvcTestResult frequenciaDesconhecida = criar(usuario, corpo("\"DESPESA\"", "\"Frequência desconhecida\"", "1", "false",
                "null", conta, "null", "\"DIARIA\"", "1", "\"2026-11-25\"", "null"));
        assertThat(frequenciaDesconhecida).hasStatus(400).bodyJson().extractingPath("title").isEqualTo("Requisição inválida");
        MvcTestResult tipoDesconhecido = criar(usuario, corpo("\"TRANSFERENCIA\"", "\"Tipo desconhecido\"", "1", "false", "null",
                conta, "null", "\"MENSAL\"", "1", "\"2026-11-25\"", "null"));
        assertThat(tipoDesconhecido).hasStatus(400).bodyJson().extractingPath("title").isEqualTo("Requisição inválida");
        assertThat(contar("recorrencias", usuario)).isEqualTo(antesDasRecusas);

        JsonNode semanal = json(criar(usuario, corpo("\"DESPESA\"", "\"Semanal sem dia\"", "1", "false", "null", conta,
                "null", "\"SEMANAL\"", "5", "\"2026-11-25\"", "null")));
        assertThat(semanal.get("diaDoMes").isNull()).isTrue();
    }

    @Test
    void contaCartaoECategoriaQueNaoServemDao422() {
        fixarHojeEm20261125();
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        Exemplo exemplo = montarOExemplo(usuario);
        long viagens = api.idCriado(api.post(usuario, "/api/categorias", categoria("Viagens", "DESPESA", null)));
        assertThat(api.acao(usuario, "/api/categorias/{id}/arquivar", viagens)).hasStatus(200);
        int antes = contar("recorrencias", usuario);

        assertRegra(criar(usuario, recorrencia("RECEITA", "Receita no cartão", "1", false, null, null, exemplo.cartaoLua(), "MENSAL", 1,
                "2026-11-25", null)), "O cartão só recebe despesa: escolha uma conta para a receita.");
        assertRegra(criar(usuario, recorrencia("DESPESA", "Conta ausente", "1", false, null, 999999L, null, "MENSAL", 1,
                "2026-11-25", null)), "Conta não encontrada.");
        assertRegra(criar(usuario, recorrencia("DESPESA", "Conta arquivada", "1", false, null, exemplo.bancoAntigo(), null, "MENSAL", 1,
                "2026-11-25", null)), "Banco Antigo está arquivada. Escolha uma conta ativa.");
        assertRegra(criar(usuario, recorrencia("DESPESA", "Cartão ausente", "1", false, null, null, 999999L, "MENSAL", 1,
                "2026-11-25", null)), "Cartão não encontrado.");
        assertThat(api.acao(usuario, "/api/cartoes/{id}/arquivar", exemplo.cartaoLua())).hasStatus(200);
        assertRegra(criar(usuario, recorrencia("DESPESA", "Cartão arquivado", "1", false, null, null, exemplo.cartaoLua(), "MENSAL", 1,
                "2026-11-25", null)), "Cartão Lua está arquivado. Escolha um cartão ativo.");
        assertThat(api.acao(usuario, "/api/cartoes/{id}/reativar", exemplo.cartaoLua())).hasStatus(200);
        assertRegra(criar(usuario, recorrencia("DESPESA", "Categoria ausente", "1", false, 999999L, exemplo.bancoHorizonte(), null,
                "MENSAL", 1, "2026-11-25", null)), "Categoria não encontrada.");
        assertRegra(criar(usuario, recorrencia("DESPESA", "Categoria de receita", "1", false, idDaCategoria(usuario, "Salário"),
                exemplo.bancoHorizonte(), null, "MENSAL", 1, "2026-11-25", null)), "Escolha uma categoria de despesa.");
        assertRegra(criar(usuario, recorrencia("DESPESA", "Categoria arquivada", "1", false, viagens, exemplo.bancoHorizonte(), null,
                "MENSAL", 1, "2026-11-25", null)), "Escolha uma categoria ativa.");
        assertThat(contar("recorrencias", usuario)).isEqualTo(antes);
    }

    @Test
    void aPreviaTrazAsProximasEAFaturaNoCartao() {
        fixarHojeEm20261125();
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        Exemplo exemplo = montarOExemplo(usuario);
        int antes = contar("recorrencias", usuario);

        JsonNode mensal = json(api.post(usuario, "/api/recorrencias/previa", previa("MENSAL", 30, "2026-11-25", null, null, null)));
        assertThat(mensal.get("desde").asString()).isEqualTo("2026-11-25");
        assertThat(mensal.get("comecaAntes").asBoolean()).isFalse();
        assertThat(previaItens(mensal)).containsExactly(
                new Previa("2026-11-30", false, null, null), new Previa("2026-12-30", false, null, null),
                new Previa("2027-01-30", false, null, null), new Previa("2027-02-28", true, null, null),
                new Previa("2027-03-30", false, null, null));
        JsonNode semanal = json(api.post(usuario, "/api/recorrencias/previa", previa("SEMANAL", null, "2026-11-27", null, null, null)));
        assertThat(previaItens(semanal)).containsExactly(
                new Previa("2026-11-27", false, null, null), new Previa("2026-12-04", false, null, null),
                new Previa("2026-12-11", false, null, null), new Previa("2026-12-18", false, null, null),
                new Previa("2026-12-25", false, null, null));
        JsonNode anual = json(api.post(usuario, "/api/recorrencias/previa", previa("ANUAL", null, "2024-02-29", null, null, null)));
        assertThat(anual.get("comecaAntes").asBoolean()).isTrue();
        assertThat(previaItens(anual)).containsExactly(
                new Previa("2027-02-28", true, null, null), new Previa("2028-02-29", false, null, null),
                new Previa("2029-02-28", true, null, null));
        JsonNode cartao = json(api.post(usuario, "/api/recorrencias/previa", previa("MENSAL", 28, "2026-11-25", null,
                exemplo.cartaoLua(), null)));
        assertThat(previaItens(cartao)).containsExactly(
                new Previa("2026-11-28", false, "2027-01", "2027-01-05"), new Previa("2026-12-28", false, "2027-02", "2027-02-05"),
                new Previa("2027-01-28", false, "2027-03", "2027-03-05"), new Previa("2027-02-28", false, "2027-04", "2027-04-05"),
                new Previa("2027-03-28", false, "2027-05", "2027-05-05"));
        JsonNode daAcademia = json(api.post(usuario, "/api/recorrencias/previa", previa("MENSAL", 28, "2026-10-01", null, null,
                exemplo.academia())));
        assertThat(daAcademia.get("desde").asString()).isEqualTo("2026-10-01");
        assertThat(contar("recorrencias", usuario)).isEqualTo(antes);
    }

    @Test
    void editarMudaOQueVemENaoTrazDeVoltaOTratado() {
        fixarHojeEm20261125();
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        Exemplo exemplo = montarOExemplo(usuario);
        long categoriaCasa = idDaCategoria(usuario, "Contas da casa");
        long categoriaMoradia = idDaCategoria(usuario, "Moradia");
        long confirmado = json(confirmar(usuario, exemplo.internet(), "2026-11-01", "2026-11-26", "119.90")).get("lancamentoId").asLong();
        assertThat(api.put(usuario, "/api/recorrencias/{id}", recorrencia("DESPESA", "Internet Fictícia", "119.90", false,
                categoriaCasa, exemplo.bancoHorizonte(), null, "MENSAL", 27, "2026-11-25", null), exemplo.internet())).hasStatus(200);
        assertThat(descricoes(aConfirmar(usuario).get("ocorrencias"))).doesNotContain("Internet Fictícia");
        assertThat(previstosDoDia(lista(usuario, "2026-12", ""), "2026-12-27")).containsExactly("Internet Fictícia");

        assertThat(pular(usuario, exemplo.diarista(), "2026-11-23")).hasStatus(200);
        assertThat(api.put(usuario, "/api/recorrencias/{id}", recorrencia("DESPESA", "Diarista Fictícia", "180.00", false,
                categoriaMoradia, exemplo.bancoHorizonte(), null, "SEMANAL", null, "2026-11-25", null), exemplo.diarista())).hasStatus(200);
        assertThat(datasDasOcorrencias(aConfirmar(usuario).get("ocorrencias"), "Diarista Fictícia")).containsExactly("2026-12-02");

        assertThat(api.put(usuario, "/api/recorrencias/{id}", recorrencia("DESPESA", "Internet Fictícia", "119.90", false,
                categoriaCasa, exemplo.bancoAurora(), null, "MENSAL", 27, "2026-11-25", null), exemplo.internet())).hasStatus(200);
        assertThat(json(api.get(usuario, "/api/lancamentos/{id}", confirmado)).get("contaId").asLong()).isEqualTo(exemplo.bancoHorizonte());
        assertThat(historicoItens(historico(usuario, exemplo.internet())).stream()
                .filter(item -> item.periodo().equals("2026-12-01") || item.periodo().equals("2026-11-01")).toList()).containsExactly(
                new Historico("2026-12-01", "2026-12-27", "PREVISTA", "-119.9", null, null, null, null, exemplo.bancoAurora(), null),
                new Historico("2026-11-01", "2026-11-26", "EFETIVADA", "-119.9", "2026-11-26", confirmado, null, "2026-11",
                        exemplo.bancoHorizonte(), null));
        assertCampo(api.put(usuario, "/api/recorrencias/{id}", recorrencia("DESPESA", "Internet Fictícia", "119.90", false,
                categoriaCasa, exemplo.bancoAurora(), null, "SEMANAL", null, "2026-11-25", null), exemplo.internet()), "frequencia",
                "A frequência não muda. Encerre esta recorrência e crie outra.");

        JsonNode pausada = json(api.put(usuario, "/api/recorrencias/{id}", recorrencia("DESPESA", "Clube renomeado", "150.00", false,
                idDaCategoria(usuario, "Lazer"), exemplo.bancoAntigo(), null, "MENSAL", 10, "2026-11-25", null), exemplo.clube()));
        assertThat(pausada.get("descricao").asString()).isEqualTo("Clube renomeado");
        assertThat(pausada.get("situacao").asString()).isEqualTo("PAUSADA");
        assertRegra(api.post(usuario, "/api/recorrencias", recorrencia("DESPESA", "Outra antiga", "1", false, null,
                exemplo.bancoAntigo(), null, "MENSAL", 1, "2026-11-25", null)), "Banco Antigo está arquivada. Escolha uma conta ativa.");
        assertRegra(api.put(usuario, "/api/recorrencias/{id}", recorrencia("DESPESA", "Streaming antigo Fictício", "29.90", false,
                idDaCategoria(usuario, "Assinaturas"), null, exemplo.cartaoLua(), "MENSAL", 31, "2026-03-01", "2026-08-31"),
                exemplo.streaming()), "Retome a recorrência antes de editar.");
    }

    @Test
    void encerrarRetomarEExcluir() {
        fixarHojeEm20261125();
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        Exemplo exemplo = montarOExemplo(usuario);
        long lancamentoAcademia = json(confirmar(usuario, exemplo.academia(), "2026-11-01", "2026-11-28", "99.90"))
                .get("lancamentoId").asLong();
        MvcTestResult primeira = api.acao(usuario, "/api/recorrencias/{id}/encerrar", exemplo.academia());
        JsonNode encerrada = json(primeira);
        assertThat(primeira).hasStatus(200);
        assertThat(encerrada.get("terminaEm").asString()).isEqualTo("2026-11-25");
        assertThat(encerrada.get("situacao").asString()).isEqualTo("ENCERRADA");
        assertThat(encerrada.get("confirmadas").asInt()).isEqualTo(1);
        assertThat(descricoes(aConfirmar(usuario).get("ocorrencias"))).doesNotContain("Academia Fictícia");
        assertThat(previstosDoMes(lista(usuario, "2026-12", ""))).doesNotContain("Academia Fictícia");
        MvcTestResult segunda = api.acao(usuario, "/api/recorrencias/{id}/encerrar", exemplo.academia());
        assertThat(segunda).hasStatus(200);
        assertThat(json(segunda)).isEqualTo(encerrada);
        JsonNode retomada = json(api.acao(usuario, "/api/recorrencias/{id}/retomar", exemplo.academia()));
        assertThat(retomada.get("terminaEm").isNull()).isTrue();
        assertThat(retomada.get("ocorrenciasDesde").asString()).isEqualTo("2026-11-25");
        assertThat(retomada.get("situacao").asString()).isEqualTo("ATIVA");
        assertThat(retomada.get("proxima").asString()).isEqualTo("2026-12-28");
        assertThat(api.acao(usuario, "/api/recorrencias/{id}/encerrar", exemplo.agua())).hasStatus(200);
        assertThat(api.acao(usuario, "/api/recorrencias/{id}/retomar", exemplo.agua())).hasStatus(200);
        assertThat(datasDasOcorrencias(aConfirmar(usuario).get("ocorrencias"), "Conta de água Fictícia")).isEmpty();
        assertThat(api.delete(usuario, "/api/recorrencias/{id}", exemplo.academia())).hasStatus(204);
        assertThat(api.get(usuario, "/api/recorrencias/{id}", exemplo.academia())).hasStatus(404).bodyJson().extractingPath("detail")
                .isEqualTo("Recorrência não encontrada.");
        assertThat(json(api.get(usuario, "/api/lancamentos/{id}", lancamentoAcademia)).get("id").asLong()).isEqualTo(lancamentoAcademia);
        assertThat(jdbc.queryForObject("select count(*) from ocorrencias_tratadas where recorrencia_id = ?", Integer.class,
                exemplo.academia())).isZero();
    }

    @Test
    void oLimiteDe200ContaAsEncerradas() {
        fixarHojeEm20261125();
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long conta = novaConta(usuario, "Conta do limite");
        jdbc.update("insert into recorrencias (usuario_id, descricao, valor, tipo, conta_id, frequencia, dia_do_mes, comeca_em, "
                + "ocorrencias_desde, criada_em, atualizada_em, termina_em) select ?, 'Limite ' || generate_series, 1, 'DESPESA', ?, "
                + "'MENSAL', 1, '2026-11-25', '2026-11-25', now(), now(), case when generate_series = 1 then '2026-11-25'::date end "
                + "from generate_series(1, 199)", usuario.id(), conta);
        assertThat(contar("recorrencias", usuario)).isEqualTo(199);
        assertThat(jdbc.queryForObject("select count(*) from recorrencias where usuario_id = ? and termina_em is not null", Integer.class,
                usuario.id())).isEqualTo(1);
        assertThat(api.post(usuario, "/api/recorrencias", recorrencia("DESPESA", "Número 200", "1", false, null, conta, null,
                "MENSAL", 1, "2026-11-25", null))).hasStatus(201);
        assertRegra(api.post(usuario, "/api/recorrencias", recorrencia("DESPESA", "Número 201", "1", false, null, conta, null,
                "MENSAL", 1, "2026-11-25", null)), "Você chegou ao limite de 200 recorrências.");
        assertThat(contar("recorrencias", usuario)).isEqualTo(200);
    }

    @Test
    void aListaSeparaAtivasPausadasEEncerradas() {
        fixarHojeEm20261125();
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        montarOExemplo(usuario);
        JsonNode resposta = json(api.get(usuario, "/api/recorrencias"));
        assertThat(resumoDasRecorrencias(resposta.get("ativas"))).containsExactly(
                new Resumo("Internet Fictícia", "2026-11-26", 0, 0, null), new Resumo("Diarista Fictícia", "2026-11-27", 0, 0, null),
                new Resumo("Academia Fictícia", "2026-11-28", 0, 0, null), new Resumo("Netflix Fictícia", "2026-11-28", 0, 0, null),
                new Resumo("Conta de luz Fictícia", "2026-11-30", 0, 0, null), new Resumo("Aluguel Fictício", "2026-12-01", 0, 0, null),
                new Resumo("Condomínio Fictício", "2026-12-01", 0, 0, null), new Resumo("Spotify Fictício", "2026-12-02", 0, 0, null),
                new Resumo("Salário Fictício", "2026-12-05", 0, 0, null),
                new Resumo("Plano de saúde Fictício", "2026-12-10", 0, 0, null),
                new Resumo("Curso de inglês Fictício", "2026-12-15", 0, 1, null),
                new Resumo("Conta de água Fictícia", "2026-12-20", 1, 0, null),
                new Resumo("IPVA Fictício", "2027-01-15", 0, 0, null));
        assertThat(resumoDasRecorrencias(resposta.get("pausadas"))).containsExactly(
                new Resumo("Mensalidade do clube Fictícia", null, 0, 0, "Banco Antigo está arquivada."));
        assertThat(encerradas(resposta.get("encerradas"))).containsExactly(new Encerrada("Streaming antigo Fictício", "2026-08-31"));
        assertThat(resposta.get("aConfirmar")).isEqualTo(aConfirmar(usuario));
    }

    private MvcTestResult criar(UsuarioDeTeste usuario, String corpo) {
        return api.post(usuario, "/api/recorrencias", corpo);
    }

    private static String corpo(String tipo, String descricao, String valor, String valorVaria, Long categoriaId, long contaId,
                                String cartaoId,
                                String frequencia, String diaDoMes, String comecaEm, String terminaEm) {
        return corpo(tipo, descricao, valor, valorVaria, categoriaId == null ? "null" : categoriaId.toString(), Long.toString(contaId),
                cartaoId,
                frequencia, diaDoMes, comecaEm, terminaEm);
    }

    private static String corpo(String tipo, String descricao, String valor, String valorVaria, String categoriaId, long contaId,
                                String cartaoId, String frequencia, String diaDoMes, String comecaEm, String terminaEm) {
        return corpo(tipo, descricao, valor, valorVaria, categoriaId, Long.toString(contaId), cartaoId, frequencia, diaDoMes, comecaEm,
                terminaEm);
    }

    private static String corpo(String tipo, String descricao, String valor, String valorVaria, String categoriaId, String contaId,
                                String cartaoId, String frequencia, String diaDoMes, String comecaEm, String terminaEm) {
        return "{\"tipo\":" + tipo + ",\"descricao\":" + descricao + ",\"valor\":" + valor + ",\"valorVaria\":" + valorVaria
                + ",\"categoriaId\":" + categoriaId + ",\"contaId\":" + contaId + ",\"cartaoId\":" + cartaoId + ",\"frequencia\":"
                + frequencia + ",\"diaDoMes\":" + diaDoMes + ",\"comecaEm\":" + comecaEm + ",\"terminaEm\":" + terminaEm + "}";
    }

    private static void assertRegra(MvcTestResult resposta, String esperada) {
        assertThat(resposta).hasStatus(422).bodyJson().extractingPath("detail").isEqualTo(esperada);
    }

    private static List<Previa> previaItens(JsonNode resposta) {
        return resposta.get("ocorrencias").valueStream()
                .map(item -> new Previa(item.get("data").asString(), item.get("ajustada").asBoolean(),
                texto(item, "faturaMes"), texto(item, "faturaVencimento"))).toList();
    }

    private static List<String> descricoes(JsonNode ocorrencias) {
        return ocorrencias.valueStream().map(item -> item.get("descricao").asString()).toList();
    }

    private static List<String> datasDasOcorrencias(JsonNode ocorrencias, String descricao) {
        return ocorrencias.valueStream().filter(item -> descricao.equals(item.get("descricao").asString()))
                .map(item -> item.get("data").asString())
                .toList();
    }

    private static List<String> previstosDoDia(JsonNode resposta, String data) {
        return resposta.get("dias").valueStream().filter(dia -> data.equals(dia.get("data").asString()))
                .flatMap(dia -> dia.get("previstos").valueStream())
                .map(item -> item.get("descricao").asString()).toList();
    }

    private static List<String> previstosDoMes(JsonNode resposta) {
        return resposta.get("dias").valueStream().flatMap(dia -> dia.get("previstos").valueStream())
                .map(item -> item.get("descricao").asString())
                .toList();
    }

    private static List<Historico> historicoItens(JsonNode resposta) {
        return resposta.get("itens").valueStream()
                .map(item -> new Historico(item.get("periodo").asString(), item.get("dataPrevista").asString(),
                item.get("situacao").asString(), texto(item, "valor"), texto(item, "data"), numero(item, "lancamentoId"),
                numero(item, "compraId"), texto(item, "mesNaLista"), numero(item, "contaId"), numero(item, "cartaoId"))).toList();
    }

    private static List<Resumo> resumoDasRecorrencias(JsonNode resposta) {
        return resposta.valueStream().map(item -> new Resumo(item.get("descricao").asString(), texto(item, "proxima"),
                item.get("atrasadas").asInt(),
                item.get("confirmadas").asInt(), texto(item, "motivoDaPausa"))).toList();
    }

    private static List<Encerrada> encerradas(JsonNode resposta) {
        return resposta.valueStream().map(item -> new Encerrada(item.get("descricao").asString(), texto(item, "terminaEm"))).toList();
    }

    private static String texto(JsonNode item, String campo) {
        return item.get(campo).isNull() ? null : item.get(campo).asString();
    }

    private static Long numero(JsonNode item, String campo) {
        return item.get(campo).isNull() ? null : item.get(campo).asLong();
    }

    private record Previa(String data, boolean ajustada, String faturaMes, String faturaVencimento) {
    }

    private record Historico(String periodo, String dataPrevista, String situacao, String valor, String data, Long lancamentoId,
                             Long compraId, String mesNaLista, Long contaId, Long cartaoId) {
    }

    private record Resumo(String descricao, String proxima, int atrasadas, int confirmadas, String motivoDaPausa) {
    }

    private record Encerrada(String descricao, String terminaEm) {
    }
}
