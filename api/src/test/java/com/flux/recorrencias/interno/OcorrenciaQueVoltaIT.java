package com.flux.recorrencias.interno;

import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

class OcorrenciaQueVoltaIT extends RecorrenciasBase {

    @Test
    void excluirOLancamentoDevolveAOcorrencia() {
        fixarHojeEm20261125();
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        Exemplo exemplo = montarOExemplo(usuario);
        long lancamento = json(confirmar(usuario, exemplo.luz(), "2026-11-01", "2026-11-30", "189.90")).get("lancamentoId").asLong();
        assertThat(api.delete(usuario, "/api/lancamentos/{id}", lancamento)).hasStatus(204);
        assertThat(descricoes(aConfirmar(usuario).get("ocorrencias"))).contains("Conta de luz Fictícia");
        assertThat(jdbc.queryForObject("select count(*) from ocorrencias_tratadas where recorrencia_id = ?", Integer.class,
                exemplo.luz())).isZero();
    }

    @Test
    void ignorarSoltaERestaurarReligaOImportado() {
        fixarHojeEm20261125();
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        Exemplo exemplo = montarOExemplo(usuario);
        long importado = importado(usuario, exemplo.bancoHorizonte(), "2026-11-24", "-201.37", "ENEL DEB AUT FICT");
        assertThat(ligar(usuario, exemplo.luz(), "2026-11-01", importado, null)).hasStatus(200);
        long linha = jdbc.queryForObject("select id from ocorrencias_tratadas where recorrencia_id = ?", Long.class, exemplo.luz());
        assertThat(api.delete(usuario, "/api/lancamentos/{id}", importado)).hasStatus(204);
        assertThat(jdbc.queryForObject("select id from ocorrencias_tratadas where recorrencia_id = ?", Long.class, exemplo.luz()))
                .isEqualTo(linha);
        assertThat(jdbc.queryForObject("select ignorado_em is not null from lancamentos where id = ?", Boolean.class, importado)).isTrue();
        assertThat(descricoes(aConfirmar(usuario).get("ocorrencias"))).contains("Conta de luz Fictícia");
        assertThat(historico(usuario, exemplo.luz()).get("itens").valueStream().map(item -> item.get("situacao").asString()).toList())
                .doesNotContain("EFETIVADA");
        assertThat(api.post(usuario, "/api/lancamentos/{id}/restaurar", "{}", importado)).hasStatus(200);
        assertThat(descricoes(aConfirmar(usuario).get("ocorrencias"))).doesNotContain("Conta de luz Fictícia");
        assertThat(api.delete(usuario, "/api/lancamentos/{id}", importado)).hasStatus(204);
        long novo = json(confirmar(usuario, exemplo.luz(), "2026-11-01", "2026-11-30", "189.90")).get("lancamentoId").asLong();
        assertThat(jdbc.queryForObject("select id from ocorrencias_tratadas where recorrencia_id = ?", Long.class, exemplo.luz()))
                .isEqualTo(linha);
        assertThat(jdbc.queryForObject("select lancamento_id from ocorrencias_tratadas where id = ?", Long.class, linha)).isEqualTo(novo);
        assertThat(api.post(usuario, "/api/lancamentos/{id}/restaurar", "{}", importado)).hasStatus(200);
        assertThat(jdbc.queryForObject("select situacao || ' ' || lancamento_id from ocorrencias_tratadas where id = ?", String.class,
                linha)).isEqualTo("EFETIVADA " + novo);
        assertThat(ligar(usuario, exemplo.agua(), "2026-11-01", importado, null)).hasStatus(200);
        assertThat(api.delete(usuario, "/api/lancamentos/{id}", importado)).hasStatus(204);
        assertThat(api.acao(usuario, "/api/recorrencias/{id}/ocorrencias/{periodo}/voltar-a-prever", exemplo.agua(), "2026-11-01"))
                .hasStatus(204);
        assertThat(jdbc.queryForObject("select count(*) from ocorrencias_tratadas where recorrencia_id = ?", Integer.class, exemplo.agua()))
                .isEqualTo(1);
        assertThat(api.post(usuario, "/api/lancamentos/{id}/restaurar", "{}", importado)).hasStatus(200);
        assertThat(descricoes(aConfirmar(usuario).get("ocorrencias"))).doesNotContain("Conta de água Fictícia");
    }

    @Test
    void desfazerAImportacaoDevolveAOcorrencia() {
        fixarHojeEm20261125();
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        Exemplo exemplo = montarOExemplo(usuario);
        long importado = importado(usuario, exemplo.bancoHorizonte(), "2026-11-24", "-201.37", "ENEL DEB AUT FICT");
        long importacao = jdbc.queryForObject("select importacao_id from lancamentos where id = ?", Long.class, importado);
        assertThat(ligar(usuario, exemplo.luz(), "2026-11-01", importado, null)).hasStatus(200);
        assertThat(api.acao(usuario, "/api/importacoes/{id}/desfazer", importacao)).hasStatus(200);
        assertThat(jdbc.queryForObject("select count(*) from ocorrencias_tratadas where recorrencia_id = ?", Integer.class,
                exemplo.luz())).isZero();
        assertThat(descricoes(aConfirmar(usuario).get("ocorrencias"))).contains("Conta de luz Fictícia");
    }

    @Test
    void editarACompraMantemALigacaoEExcluirSolta() {
        fixarHojeEm20261125();
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        Exemplo exemplo = montarOExemplo(usuario);
        long compra = json(confirmar(usuario, exemplo.netflix(), "2026-11-01", "2026-11-28", "55.90")).get("compraId").asLong();
        long parcelaAntiga = jdbc.queryForObject("select id from lancamentos where compra_id = ?", Long.class, compra);
        assertThat(api.put(usuario, "/api/compras/{id}", compra(exemplo.cartaoLua(), "2026-11-29", "Netflix Fictícia", "59.90", 1, 1,
                idDaCategoria(usuario, "Assinaturas")), compra)).hasStatus(200);
        assertThat(jdbc.queryForObject("select compra_id from ocorrencias_tratadas where recorrencia_id = ?", Long.class,
                exemplo.netflix()))
                .isEqualTo(compra);
        assertThat(parcelas(compra)).containsExactly("1 2027-01-05 -59.90 2027-01");
        assertThat(jdbc.queryForObject("select id from lancamentos where compra_id = ?", Long.class, compra)).isNotEqualTo(parcelaAntiga);
        assertThat(api.delete(usuario, "/api/compras/{id}", compra)).hasStatus(204);
        assertThat(descricoes(aConfirmar(usuario).get("ocorrencias"))).contains("Netflix Fictícia");
    }

    private static java.util.List<String> descricoes(JsonNode itens) {
        return itens.valueStream().map(item -> item.get("descricao").asString()).toList();
    }
}
