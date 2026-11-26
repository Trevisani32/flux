package com.flux.recorrencias.interno;

import java.util.List;

import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

class ListaComPrevistosIT extends RecorrenciasBase {

    @Test
    void novembroTemOsPrevistosForaDosTotais() {
        fixarHojeEm20261125();
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        Exemplo exemplo = montarOExemplo(usuario);
        JsonNode novembro = lista(usuario, "2026-11", "");
        assertResumoPrevistos(novembro, "0.00", "-686.10", 5, 1);
        assertThat(previstosPorDia(novembro)).containsExactly(
                new DiaPrevisto("2026-11-30", List.of("Conta de luz Fictícia")),
                new DiaPrevisto("2026-11-28", List.of("Academia Fictícia")),
                new DiaPrevisto("2026-11-27", List.of("Diarista Fictícia")), new DiaPrevisto("2026-11-26", List.of("Internet Fictícia")),
                new DiaPrevisto("2026-11-20", List.of("Conta de água Fictícia")));
        assertThat(previsto(novembro, "Conta de água Fictícia").get("atrasada").asBoolean()).isTrue();
        assertTotais(novembro, "0.00", 0, "-350.00", 1, "-350.00");
        assertThat(novembro.get("quantidadeLancamentos").asInt()).isEqualTo(1);
        assertThat(item(novembro, "Curso de inglês Fictício").get("recorrenciaId").asLong()).isEqualTo(exemplo.curso());
        JsonNode daConta = lista(usuario, "2026-11", "&conta=" + exemplo.bancoHorizonte());
        assertThat(dia(daConta, "2026-11-30").get("saldoNoFimDoDia").decimalValue()).isEqualByComparingTo("890.35");
    }

    @Test
    void dezembroTemOCartaoNoVencimento() {
        fixarHojeEm20261125();
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        Exemplo exemplo = montarOExemplo(usuario);
        JsonNode dezembro = lista(usuario, "2026-12", "");
        assertResumoPrevistos(dezembro, "8200.00", "-4508.00", 14, 0);
        assertThat(previstosDoDia(dezembro, "2026-12-10")).containsExactly("Plano de saúde Fictício", "Spotify Fictício");
        assertThat(previsto(dezembro, "Spotify Fictício").get("data").asString()).isEqualTo("2026-12-02");
        assertThat(previsto(dezembro, "Spotify Fictício").get("dataNaLista").asString()).isEqualTo("2026-12-10");
        assertThat(descricoesPrevistas(dezembro)).doesNotContain("Netflix Fictícia", "Mensalidade do clube Fictícia");
        JsonNode janeiroLua = lista(usuario, "2027-01", "&cartao=" + exemplo.cartaoLua());
        assertThat(previstosDoDia(janeiroLua, "2027-01-05")).containsExactly("Netflix Fictícia");
        assertThat(previsto(janeiroLua, "Netflix Fictícia").get("data").asString()).isEqualTo("2026-11-28");
        assertThat(janeiroLua.get("fatura").get("previsto").decimalValue()).isEqualByComparingTo("55.90");
    }

    @Test
    void osFiltrosValemNosPrevistos() {
        fixarHojeEm20261125();
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        Exemplo exemplo = montarOExemplo(usuario);
        api.idCriado(api.post(usuario, "/api/recorrencias", recorrencia("DESPESA", "Feira Fictícia", "50.00", false, null,
                exemplo.bancoAurora(), null, "SEMANAL", null, "2026-12-07", null)));
        JsonNode aurora = lista(usuario, "2026-12", "&conta=" + exemplo.bancoAurora());
        assertResumoPrevistos(aurora, "0.00", "-840.00", 5, 0);
        JsonNode horizonte = lista(usuario, "2026-12", "&cartao=" + exemplo.cartaoHorizonte());
        assertResumoPrevistos(horizonte, "0.00", "-21.90", 1, 0);
        assertThat(horizonte.get("fatura").get("previsto").decimalValue()).isEqualByComparingTo("21.90");
        JsonNode moradia = lista(usuario, "2026-12", "&categoria=" + idDaCategoria(usuario, "Moradia"));
        assertResumoPrevistos(moradia, "0.00", "-3396.20", 9, 0);
        JsonNode semCategoria = lista(usuario, "2026-12", "&categoria=sem");
        assertResumoPrevistos(semCategoria, "0.00", "-200.00", 4, 0);
        JsonNode receita = lista(usuario, "2026-12", "&tipo=receita");
        assertResumoPrevistos(receita, "8200.00", "0.00", 1, 0);
        for (String tipo : List.of("transferencia", "pagamento")) {
            JsonNode semPrevistos = lista(usuario, "2026-12", "&tipo=" + tipo);
            assertResumoPrevistos(semPrevistos, "0.00", "0.00", 0, 0);
            assertThat(previstosPorDia(semPrevistos)).isEmpty();
        }
        JsonNode busca = lista(usuario, "2026-12", "&busca=conta");
        assertResumoPrevistos(busca, "0.00", "-286.30", 2, 0);
        JsonNode todos = lista(usuario, "2026-12", "");
        JsonNode sem = lista(usuario, "2026-12", "&previstos=false");
        assertThat(sem.get("previstos").isNull()).isTrue();
        assertThat(previstosPorDia(sem)).isEmpty();
        assertThat(sem.get("totais")).isEqualTo(todos.get("totais"));
    }

    @Test
    void pausadaSomeEReativarTrazAsAtrasadas() {
        fixarHojeEm20261125();
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        Exemplo exemplo = montarOExemplo(usuario);
        jdbc.update("update recorrencias set comeca_em = '2026-11-01', ocorrencias_desde = '2026-11-01' where id = ?", exemplo.clube());
        assertThat(descricoes(aConfirmar(usuario).get("ocorrencias"))).doesNotContain("Mensalidade do clube Fictícia");
        assertThat(descricoesPrevistas(lista(usuario, "2026-12", "&conta=" + exemplo.bancoAntigo())))
                .doesNotContain("Mensalidade do clube Fictícia");
        assertThat(api.acao(usuario, "/api/contas/{id}/reativar", exemplo.bancoAntigo())).hasStatus(200);
        JsonNode confirmacao = aConfirmar(usuario);
        JsonNode atrasada = ocorrencia(confirmacao.get("ocorrencias"), "Mensalidade do clube Fictícia");
        assertThat(atrasada.get("periodo").asString()).isEqualTo("2026-11-01");
        assertThat(atrasada.get("data").asString()).isEqualTo("2026-11-10");
        assertThat(atrasada.get("atrasada").asBoolean()).isTrue();
        assertThat(atrasada.get("visivelAte").asString()).isEqualTo("2027-01-09");
        JsonNode dezembro = lista(usuario, "2026-12", "&conta=" + exemplo.bancoAntigo());
        assertThat(previstosDoDia(dezembro, "2026-12-10")).containsExactly("Mensalidade do clube Fictícia");
        assertThat(previsto(dezembro, "Mensalidade do clube Fictícia").get("valor").decimalValue()).isEqualByComparingTo("-150.00");
        JsonNode ativa = ocorrencia(json(api.get(usuario, "/api/recorrencias")).get("ativas"), "Mensalidade do clube Fictícia");
        assertThat(ativa.get("atrasadas").asInt()).isEqualTo(1);
        assertThat(ativa.get("proxima").asString()).isEqualTo("2026-12-10");
    }

    private static void assertResumoPrevistos(JsonNode resposta, String entradas, String saidas, int quantidade, int atrasadas) {
        assertThat(resposta.get("previstos").get("entradas").decimalValue()).isEqualByComparingTo(entradas);
        assertThat(resposta.get("previstos").get("saidas").decimalValue()).isEqualByComparingTo(saidas);
        assertThat(resposta.get("previstos").get("quantidade").asInt()).isEqualTo(quantidade);
        assertThat(resposta.get("previstos").get("atrasadas").asInt()).isEqualTo(atrasadas);
    }

    private static void assertTotais(JsonNode resposta, String entradas, int quantidadeEntradas, String saidas, int quantidadeSaidas,
                                     String resultado) {
        JsonNode totais = resposta.get("totais");
        assertThat(totais.get("entradas").decimalValue()).isEqualByComparingTo(entradas);
        assertThat(totais.get("quantidadeEntradas").asInt()).isEqualTo(quantidadeEntradas);
        assertThat(totais.get("saidas").decimalValue()).isEqualByComparingTo(saidas);
        assertThat(totais.get("quantidadeSaidas").asInt()).isEqualTo(quantidadeSaidas);
        assertThat(totais.get("resultado").decimalValue()).isEqualByComparingTo(resultado);
    }

    private static List<DiaPrevisto> previstosPorDia(JsonNode resposta) {
        return resposta.get("dias").valueStream().filter(dia -> !dia.get("previstos").isEmpty()).map(dia -> new DiaPrevisto(
                dia.get("data").asString(), descricoes(dia.get("previstos")))).toList();
    }

    private static List<String> previstosDoDia(JsonNode resposta, String data) {
        return descricoes(dia(resposta, data).get("previstos"));
    }

    private static List<String> descricoesPrevistas(JsonNode resposta) {
        return resposta.get("dias").valueStream().flatMap(dia -> dia.get("previstos").valueStream())
                .map(item -> item.get("descricao").asString())
                .toList();
    }

    private static List<String> descricoes(JsonNode itens) {
        return itens.valueStream().map(item -> item.get("descricao").asString()).toList();
    }

    private static JsonNode previsto(JsonNode resposta, String descricao) {
        return resposta.get("dias").valueStream().flatMap(dia -> dia.get("previstos").valueStream())
                .filter(item -> descricao.equals(item.get("descricao").asString())).findFirst()
                .orElseThrow(() -> new AssertionError("Previsto não encontrado: " + descricao));
    }

    private static JsonNode item(JsonNode resposta, String descricao) {
        return resposta.get("dias").valueStream().flatMap(dia -> dia.get("itens").valueStream())
                .filter(item -> descricao.equals(item.get("descricao").asString())).findFirst()
                .orElseThrow(() -> new AssertionError("Item não encontrado: " + descricao));
    }

    private static JsonNode dia(JsonNode resposta, String data) {
        return resposta.get("dias").valueStream().filter(dia -> data.equals(dia.get("data").asString())).findFirst()
                .orElseThrow(() -> new AssertionError("Dia não encontrado: " + data));
    }

    private static JsonNode ocorrencia(JsonNode itens, String descricao) {
        return itens.valueStream().filter(item -> descricao.equals(item.get("descricao").asString())).findFirst()
                .orElseThrow(() -> new AssertionError("Ocorrência não encontrada: " + descricao));
    }

    private record DiaPrevisto(String data, List<String> descricoes) {
    }
}
